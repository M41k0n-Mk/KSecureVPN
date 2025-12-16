package tunneling.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logging.LogLevel
import logging.SecureLogger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.SecretKey
import tunneling.vpn.linux.ClientNetworking

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
    private var sendSeq: Long = 1L
    private val antiReplay = AntiReplayWindow()

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
            if (response.type != FrameType.AUTH_RESPONSE || response.payload.size != 1) {
                error("Invalid AUTH response")
            }
            val code = response.payload[0]
            if (code != tunneling.ResponseCode.AUTH_SUCCESS) {
                error("Authentication failed: $code")
            }
            logger.logSessionEvent("vpn-client", LogLevel.INFO, "AUTH success")

            // Send IP_REQUEST
            sendFrame(socket, FrameType.CONTROL, byteArrayOf(ControlKind.IP_REQUEST))
            val ipAssign = receiveFrame(socket) ?: error("No IP assignment")
            require(ipAssign.type == FrameType.CONTROL && ipAssign.payload.size >= 5 && ipAssign.payload[0] == ControlKind.IP_ASSIGN) {
                "Unexpected IP assignment"
            }
            val ipInt =
                ((ipAssign.payload[1].toInt() and 0xFF) shl 24) or
                    ((ipAssign.payload[2].toInt() and 0xFF) shl 16) or
                    ((ipAssign.payload[3].toInt() and 0xFF) shl 8) or
                    (ipAssign.payload[4].toInt() and 0xFF)
            val assigned = IPv4.intToInet4(ipInt)
            logger.logSessionEvent("vpn-client", LogLevel.INFO, "Assigned virtual IP ${assigned.hostAddress}")

            // Auto-configuração do cliente (Linux)
            try {
                val os = System.getProperty("os.name")?.lowercase() ?: ""
                if (os.contains("linux")) {
                    val setDefault = System.getenv("KSECUREVPN_CLIENT_SET_DEFAULT_ROUTE")?.toBoolean() ?: false
                    val dns = System.getenv("KSECUREVPN_CLIENT_DNS") ?: "8.8.8.8,8.8.4.4"
                    ClientNetworking.configureLinuxClient(
                        tunName = vInterface.name,
                        ipCidr = "${assigned.hostAddress}/24",
                        mtu = vInterface.mtu,
                        setDefaultRoute = setDefault,
                        dnsServersCsv = dns,
                    )
                }
            } catch (e: Exception) {
                logger.logSessionEvent("vpn-client", LogLevel.WARN, "Falha ao auto-configurar rede no cliente: ${e.message}")
            }

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
                        if (frame.type == FrameType.PACKET) {
                            // Anti-replay: aceita somente sequências válidas
                            if (!antiReplay.accept(frame.seq)) continue
                            val p = frame.payload
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

    /**
     * Sends a framed message over UDP to the server.
     */
    private fun sendFrame(
        socket: DatagramSocket,
        type: Byte,
        payload: ByteArray,
    ) {
        val data = PacketFramer.createFrameWithSeq(type, sendSeq++, payload, key)
        val packet = DatagramPacket(data, data.size, serverAddress, serverPort)
        socket.send(packet)
    }

    /**
     * Receives and parses a framed message from the server over UDP.
     */
    private fun receiveFrame(socket: DatagramSocket): PacketFramer.Frame? {
        val buffer = ByteArray(65536)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val data = packet.data.copyOfRange(0, packet.length)
        return PacketFramer.readFrameFromBytesWithSeq(data, key)
    }
}

private fun Int.toBytes(): ByteArray =
    byteArrayOf(
        ((this ushr 24) and 0xFF).toByte(),
        ((this ushr 16) and 0xFF).toByte(),
        ((this ushr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )
