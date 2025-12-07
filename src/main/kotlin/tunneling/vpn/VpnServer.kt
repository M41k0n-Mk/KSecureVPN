package tunneling.vpn

import auth.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logging.LogLevel
import logging.SecureLogger
import session.SessionTracker
import tunneling.readFully
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.SecretKey

/**
 * Experimental VPN server using UDP transport with AES encryption for IP packets.
 * - Clients authenticate via UDP datagrams with AUTH flow.
 * - After auth, client sends CONTROL/IP_REQUEST. Server assigns an IP and returns CONTROL/IP_ASSIGN.
 * - Subsequent frames of type PACKET are forwarded based on destination IP to the corresponding client session.
 * - Uses sequence numbers for basic reliability.
 */
class VpnServer(
    private val port: Int = 9001,
    private val key: SecretKey,
    private val authService: AuthService = AuthService(),
) {
    private val logger = SecureLogger.getInstance()
    private val ipPool = IpPool("10.8.0.0", 24, reserveGateway = true)
    private val routes = RoutingTable()
    private val sessions = mutableMapOf<String, SessionData>() // key: clientAddr:port

    data class SessionData(
        val address: InetAddress,
        val port: Int,
        val sessionId: String,
        var authenticated: Boolean = false,
        var assignedIp: String? = null,
        var sequenceNumber: Int = 0,
    )

    fun start() =
        runBlocking {
            val socket = DatagramSocket(port)
            println("[VPN-SERVER] Listening on UDP $port (virtual network 10.8.0.0/24)")
            val buffer = ByteArray(65536) // Max UDP payload
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                launch(Dispatchers.IO) {
                    handlePacket(socket, packet)
                }
            }
        }

    private fun handlePacket(
        socket: DatagramSocket,
        packet: DatagramPacket,
    ) {
        val clientKey = "${packet.address}:${packet.port}"
        val data = packet.data.copyOfRange(0, packet.length)

        val session = sessions.getOrPut(clientKey) {
            val sessionId = SessionTracker.createSession(clientKey)
            logger.logSessionEvent(sessionId, LogLevel.INFO, "New UDP session from $clientKey")
            SessionData(packet.address, packet.port, sessionId)
        }

        try {
            // Try to read frame
            val frame = PacketFramer.readFrameFromBytes(data, key) ?: run {
                logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Invalid frame from $clientKey")
                return
            }

            when (frame.first) {
                FrameType.AUTH -> {
                    handleAuth(socket, session, frame.second)
                }
                FrameType.CONTROL -> {
                    if (!session.authenticated) {
                        logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Unauthenticated control from $clientKey")
                        return
                    }
                    handleControl(socket, session, frame.second)
                }
                FrameType.PACKET -> {
                    if (!session.authenticated || session.assignedIp == null) {
                        logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Unauthorized packet from $clientKey")
                        return
                    }
                    handlePacketForward(socket, session, frame.second)
                }
                else -> {
                    logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Unknown frame type from $clientKey")
                }
            }
        } catch (e: Exception) {
            logger.logSessionEvent(session.sessionId, LogLevel.ERROR, "Error handling packet: ${e.message}")
        }
    }

    private fun handleAuth(
        socket: DatagramSocket,
        session: SessionData,
        payload: ByteArray,
    ) {
        val s = payload.decodeToString()
        val parts = s.split('\n')
        if (parts.size < 3 || parts[0] != "AUTH") {
            sendResponse(socket, session, tunneling.ResponseCode.AUTH_MALFORMED)
            return
        }
        val username = parts[1]
        val password = parts[2].toCharArray()
        val ok = authService.authenticate(username, password)
        if (!ok) {
            sendResponse(socket, session, tunneling.ResponseCode.AUTH_FAILED)
            logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Auth failed for $username")
            return
        }
        session.authenticated = true
        sendResponse(socket, session, tunneling.ResponseCode.AUTH_SUCCESS)
        logger.logSessionEvent(session.sessionId, LogLevel.INFO, "Auth success for $username")
    }

    private fun handleControl(
        socket: DatagramSocket,
        session: SessionData,
        payload: ByteArray,
    ) {
        if (payload.isEmpty()) return
        when (payload[0]) {
            ControlKind.IP_REQUEST -> {
                val ip = ipPool.allocate() ?: run {
                    logger.logSessionEvent(session.sessionId, LogLevel.WARN, "IP pool exhausted")
                    return
                }
                session.assignedIp = ip.hostAddress
                val ipInt = IPv4.inet4ToInt(ip)
                val responsePayload = byteArrayOf(
                    ((ipInt ushr 24) and 0xFF).toByte(),
                    ((ipInt ushr 16) and 0xFF).toByte(),
                    ((ipInt ushr 8) and 0xFF).toByte(),
                    (ipInt and 0xFF).toByte(),
                )
                sendFrame(socket, session, FrameType.CONTROL, byteArrayOf(ControlKind.IP_ASSIGN) + responsePayload)
                routes.add(ipInt) { packet, length ->
                    runBlocking {
                        sendFrame(socket, session, FrameType.PACKET, packet.copyOfRange(0, length))
                    }
                }
                logger.logSessionEvent(session.sessionId, LogLevel.INFO, "Assigned IP $ip")
            }
        }
    }

    private fun handlePacketForward(
        socket: DatagramSocket,
        session: SessionData,
        payload: ByteArray,
    ) {
        val dst = IPv4.dstAsInt(payload, payload.size) ?: return
        val entry = routes.lookup(dst)
        if (entry != null) {
            entry.sendPacket(payload, payload.size)
        } else {
            logger.logSessionEvent(session.sessionId, LogLevel.DEBUG, "No route for destination IP")
        }
    }

    private fun sendResponse(
        socket: DatagramSocket,
        session: SessionData,
        code: Byte,
    ) {
        val response = byteArrayOf(code)
        val packet = DatagramPacket(response, response.size, session.address, session.port)
        socket.send(packet)
    }

    private fun sendFrame(
        socket: DatagramSocket,
        session: SessionData,
        type: Byte,
        payload: ByteArray,
    ) {
        val frameData = PacketFramer.createFrame(type, payload, key)
        val packet = DatagramPacket(frameData, frameData.size, session.address, session.port)
        socket.send(packet)
    }
}
