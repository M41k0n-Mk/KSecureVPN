package tunneling.vpn

import auth.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logging.LogLevel
import logging.SecureLogger
import session.SessionTracker
import tunneling.readFully
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey

/**
 * Experimental VPN server using the existing AES channel but carrying IP packets.
 * - Clients authenticate with the same AUTH flow as TunnelServer (reused in higher level).
 * - After auth, client sends CONTROL/IP_REQUEST. Server assigns an IP and returns CONTROL/IP_ASSIGN.
 * - Subsequent frames of type PACKET are forwarded based on destination IP to the corresponding client session.
 */
class VpnServer(
    private val port: Int = 9001,
    private val key: SecretKey,
    private val authService: AuthService = AuthService(),
) {
    private val logger = SecureLogger.getInstance()
    private val ipPool = IpPool("10.8.0.0", 24, reserveGateway = true)
    private val routes = RoutingTable()

    fun start() = runBlocking {
        val server = ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
        println("[VPN-SERVER] Listening on $port (virtual network 10.8.0.0/24)")
        while (true) {
            val client = server.accept()
            launch(Dispatchers.IO) {
                handleClient(client)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val sessionId = SessionTracker.createSession(socket.inetAddress.toString())
        logger.logSessionEvent(sessionId, LogLevel.INFO, "VPN client connected")
        val input = socket.getInputStream()
        val out = socket.getOutputStream()

        // Reuse authentication from existing protocol: read IV+LEN+CIPHERTEXT and expect plain string starting with AUTH\n
        val iv = ByteArray(16)
        val ivRead = readFully(input, iv)
        if (ivRead < iv.size) return closeSession(sessionId, socket)
        val lenBytes = ByteArray(4)
        val lr = readFully(input, lenBytes)
        if (lr < 4) return closeSession(sessionId, socket)
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or ((lenBytes[1].toInt() and 0xFF) shl 16) or ((lenBytes[2].toInt() and 0xFF) shl 8) or (lenBytes[3].toInt() and 0xFF)
        val cipher = ByteArray(len)
        val cr = readFully(input, cipher)
        if (cr < len) return closeSession(sessionId, socket)

        // Decrypt using AESCipher from existing stack
        val plain = try {
            crypt.AESCipher.decrypt(cipher, key, iv)
        } catch (e: Exception) {
            logger.logSessionEvent(sessionId, LogLevel.WARN, "Auth decrypt failed: ${e.message}")
            return closeSession(sessionId, socket)
        }
        val s = plain.decodeToString()
        val parts = s.split('\n')
        if (parts.size < 3 || parts[0] != "AUTH") {
            // Malformed
            out.write(byteArrayOf(tunneling.ResponseCode.AUTH_MALFORMED))
            out.flush()
            return closeSession(sessionId, socket)
        }
        val username = parts[1]
        val password = parts[2].toCharArray()
        val ok = authService.authenticate(username, password)
        if (!ok) {
            out.write(byteArrayOf(tunneling.ResponseCode.AUTH_FAILED))
            out.flush()
            return closeSession(sessionId, socket)
        }
        out.write(byteArrayOf(tunneling.ResponseCode.AUTH_SUCCESS))
        out.flush()
        logger.logSessionEvent(sessionId, LogLevel.INFO, "VPN auth OK", mapOf("user" to username))

        // Expect CONTROL/IP_REQUEST
        val first = PacketFramer.readFrame(input, key) ?: return closeSession(sessionId, socket)
        if (first.first != FrameType.CONTROL || first.second.isEmpty() || first.second[0] != ControlKind.IP_REQUEST) {
            return closeSession(sessionId, socket)
        }

        val ip = ipPool.allocate() ?: run {
            logger.logSessionEvent(sessionId, LogLevel.WARN, "IP pool exhausted")
            return closeSession(sessionId, socket)
        }
        val ipInt = IPv4.inet4ToInt(ip)

        // Register route: sender function uses PacketFramer with PACKET frames
        routes.add(ipInt) { packet, length ->
            val payload = ByteArray(length)
            System.arraycopy(packet, 0, payload, 0, length)
            runBlocking { PacketFramer.sendFrame(out, key, FrameType.PACKET, payload) }
        }

        // Send IP_ASSIGN (payload: 4 bytes IPv4)
        val payload = byteArrayOf(
            ((ipInt ushr 24) and 0xFF).toByte(),
            ((ipInt ushr 16) and 0xFF).toByte(),
            ((ipInt ushr 8) and 0xFF).toByte(),
            (ipInt and 0xFF).toByte(),
        )
        PacketFramer.sendFrame(out, key, FrameType.CONTROL, byteArrayOf(ControlKind.IP_ASSIGN) + payload)

        // Main loop: read frames from this client
        while (true) {
            val frame = PacketFramer.readFrame(input, key) ?: break
            when (frame.first) {
                FrameType.PACKET -> {
                    val p = frame.second
                    val dst = IPv4.dstAsInt(p, p.size) ?: continue
                    val entry = routes.lookup(dst)
                    if (entry != null) {
                        entry.sendPacket(p, p.size)
                    } else {
                        // Unknown route: drop for now.
                    }
                }
                FrameType.CONTROL -> {
                    // Ignore keepalives for now
                }
                else -> {}
            }
        }

        // Cleanup
        routes.remove(ipInt)
        ipPool.release(ip)
        closeSession(sessionId, socket)
    }

    private fun closeSession(sessionId: String, socket: Socket) {
        logger.logSessionEvent(sessionId, LogLevel.INFO, "VPN session ended")
        SessionTracker.endSession(sessionId)
        try { socket.close() } catch (_: Exception) {}
    }
}
