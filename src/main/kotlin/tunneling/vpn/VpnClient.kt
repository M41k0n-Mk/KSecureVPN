package tunneling.vpn

import crypt.AESCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logging.LogLevel
import logging.SecureLogger
import tunneling.readFully
import java.net.Socket
import javax.crypto.SecretKey

/**
 * Experimental VPN client that exchanges raw IP packets with the server using PacketFramer.
 * This class expects a [VirtualInterface] implementation (e.g., system TUN) to be provided.
 */
class VpnClient(
    private val serverHost: String,
    private val serverPort: Int,
    private val key: SecretKey,
    private val username: String,
    private val password: CharArray,
    private val vInterface: VirtualInterface,
) {
    private val logger = SecureLogger.getInstance()

    fun start() = runBlocking {
        val socket = Socket(serverHost, serverPort)
        val input = socket.getInputStream()
        val out = socket.getOutputStream()

        // Send AUTH reusing existing scheme (IV + LEN + CIPHERTEXT). Plaintext starts with "AUTH\n".
        val authPayload = buildString {
            append("AUTH\n")
            append(username)
            append('\n')
            append(password.concatToString())
        }.toByteArray()
        val (authCipher, authIv) = AESCipher.encrypt(authPayload, key)
        out.write(authIv)
        out.write(authCipher.size.toBytes())
        out.write(authCipher)
        out.flush()

        // Read single-byte AUTH response
        val authResp = ByteArray(1)
        val r = readFully(input, authResp)
        if (r < 1 || authResp[0] != tunneling.ResponseCode.AUTH_SUCCESS) {
            socket.close()
            throw IllegalStateException("Authentication failed or malformed.")
        }

        // Request an IP
        PacketFramer.sendFrame(out, key, FrameType.CONTROL, byteArrayOf(ControlKind.IP_REQUEST))
        val first = PacketFramer.readFrame(input, key) ?: error("No IP assignment received")
        require(first.first == FrameType.CONTROL && first.second.isNotEmpty() && first.second[0] == ControlKind.IP_ASSIGN) {
            "Unexpected first frame from server"
        }
        require(first.second.size >= 5) { "IP_ASSIGN payload too small" }
        val ipInt = ((first.second[1].toInt() and 0xFF) shl 24) or ((first.second[2].toInt() and 0xFF) shl 16) or ((first.second[3].toInt() and 0xFF) shl 8) or (first.second[4].toInt() and 0xFF)
        val assigned = IPv4.intToInet4(ipInt)
        logger.logSessionEvent("vpn-client", LogLevel.INFO, "Assigned virtual IP ${assigned.hostAddress}")

        // Start two loops: TUN->server and server->TUN
        val scope = CoroutineScope(Dispatchers.IO)
        val toServer: Job = scope.launch {
            val buf = ByteArray(vInterface.mtu + 64)
            while (isActive) {
                val n = vInterface.readPacket(buf)
                if (n <= 0) continue
                if (n > 1500) continue // MTU/fragmentation: drop oversize for now
                val payload = buf.copyOfRange(0, n)
                PacketFramer.sendFrame(out, key, FrameType.PACKET, payload)
            }
        }
        val toTun: Job = scope.launch {
            while (isActive) {
                val frame = PacketFramer.readFrame(input, key) ?: break
                if (frame.first == FrameType.PACKET) {
                    val p = frame.second
                    if (p.size <= vInterface.mtu + 64) {
                        vInterface.writePacket(p, p.size)
                    }
                }
            }
        }

        // On coroutine end
        toServer.join()
        toTun.cancelAndJoin()
        socket.close()
    }
}

private fun Int.toBytes(): ByteArray =
    byteArrayOf(
        ((this ushr 24) and 0xFF).toByte(),
        ((this ushr 16) and 0xFF).toByte(),
        ((this ushr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )
