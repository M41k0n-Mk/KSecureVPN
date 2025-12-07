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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.SecretKey

/**
 * Experimental VPN client using UDP transport to exchange raw IP packets with the server.
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
    private val serverAddress = InetAddress.getByName(serverHost)

    fun start() =
        runBlocking {
            val socket = DatagramSocket()
            socket.connect(serverAddress, serverPort) // Optional, for convenience

            // Send AUTH frame
            val authPayload =
                buildString {
                    append("AUTH\n")
                    append(username)
                    append('\n')
                    append(password.concatToString())
                }.toByteArray()
            sendFrame(socket, FrameType.AUTH, authPayload)
            logger.logSessionEvent("vpn-client", LogLevel.INFO, "Sent AUTH to $serverHost:$serverPort")

            // Receive AUTH response
            val response = receiveFrame(socket) ?: error("No AUTH response")
            if (response.first != FrameType.AUTH_RESPONSE || response.second.size != 1) {
                error("Invalid AUTH response")
            }
            val code = response.second[0]
            if (code != tunneling.ResponseCode.AUTH_SUCCESS) {
                error("Authentication failed: $code")
            }
            logger.logSessionEvent("vpn-client", LogLevel.INFO, "AUTH success")

            // Send IP_REQUEST
            sendFrame(socket, FrameType.CONTROL, byteArrayOf(ControlKind.IP_REQUEST))
            val ipAssign = receiveFrame(socket) ?: error("No IP assignment")
            require(ipAssign.first == FrameType.CONTROL && ipAssign.second.size >= 5 && ipAssign.second[0] == ControlKind.IP_ASSIGN) {
                "Unexpected IP assignment"
            }
            val ipInt =
                ((ipAssign.second[1].toInt() and 0xFF) shl 24) or
                    ((ipAssign.second[2].toInt() and 0xFF) shl 16) or
                    ((ipAssign.second[3].toInt() and 0xFF) shl 8) or
                    (ipAssign.second[4].toInt() and 0xFF)
            val assigned = IPv4.intToInet4(ipInt)
            logger.logSessionEvent("vpn-client", LogLevel.INFO, "Assigned virtual IP ${assigned.hostAddress}")

            // Start loops
            val scope = CoroutineScope(Dispatchers.IO)
            val toServer: Job =
                scope.launch {
                    val buf = ByteArray(vInterface.mtu + 64)
                    while (isActive) {
                        val n = vInterface.readPacket(buf)
                        if (n <= 0) continue
                        if (n > 1500) continue // MTU/fragmentation: drop oversize
                        val payload = buf.copyOfRange(0, n)
                        sendFrame(socket, FrameType.PACKET, payload)
                    }
                }
            val fromServer: Job =
                scope.launch {
                    while (isActive) {
                        val frame = receiveFrame(socket) ?: break
                        if (frame.first == FrameType.PACKET) {
                            val p = frame.second
                            if (p.size <= vInterface.mtu + 64) {
                                vInterface.writePacket(p, p.size)
                            }
                        }
                    }
                }

            // Wait
            toServer.join()
            fromServer.cancelAndJoin()
            socket.close()
        }

    private fun sendFrame(
        socket: DatagramSocket,
        type: Byte,
        payload: ByteArray,
    ) {
        val data = PacketFramer.createFrame(type, payload, key)
        val packet = DatagramPacket(data, data.size, serverAddress, serverPort)
        socket.send(packet)
    }

    private fun receiveFrame(socket: DatagramSocket): Pair<Byte, ByteArray>? {
        val buffer = ByteArray(65536)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val data = packet.data.copyOfRange(0, packet.length)
        return PacketFramer.readFrameFromBytes(data, key)
    }
}

private fun Int.toBytes(): ByteArray =
    byteArrayOf(
        ((this ushr 24) and 0xFF).toByte(),
        ((this ushr 16) and 0xFF).toByte(),
        ((this ushr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )
