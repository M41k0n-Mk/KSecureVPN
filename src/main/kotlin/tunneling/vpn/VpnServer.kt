package tunneling.vpn

import auth.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logging.LogLevel
import logging.SecureLogger
import session.SessionTracker
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.SecretKey
import tunneling.vpn.linux.RealTun
import tunneling.vpn.linux.SystemNetworking

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
    /**
     * Interface virtual do lado do servidor. Quando null em Linux, será criada uma RealTun("ksecvpn0").
     * Em outros SOs permanece null (sem TUN real no servidor).
     */
    private val serverTun: VirtualInterface? = null,
    /** Se true, tenta configurar IP/rota/NAT automaticamente em Linux. */
    private val autoConfigureSystemNetworking: Boolean = true,
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
        var sendSeq: Long = 1L,
        val antiReplay: AntiReplayWindow = AntiReplayWindow(),
    )

    fun start() =
        runBlocking {
            val socket = DatagramSocket(port)
            println("[VPN-SERVER] Listening on UDP $port (virtual network 10.8.0.0/24)")

            // Inicializa TUN do servidor quando aplicável (Linux)
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            val srvTun: VirtualInterface? = try {
                when {
                    serverTun != null -> serverTun
                    os.contains("linux") -> RealTun("ksecvpn0")
                    else -> null
                }
            } catch (e: Exception) {
                logger.logSessionEvent("server", LogLevel.WARN, "Falha ao criar TUN do servidor: ${e.message}")
                null
            }

            // Autoconfigura rede do sistema (Linux) se solicitado e TUN criada
            if (autoConfigureSystemNetworking && os.contains("linux") && srvTun is RealTun) {
                try {
                    SystemNetworking.configureLinuxForVpn(
                        tunName = srvTun.name,
                        cidr = "10.8.0.1/24",
                        mtu = srvTun.mtu,
                        wanIf = System.getenv("KSECUREVPN_WAN_IFACE"),
                    )
                } catch (e: Exception) {
                    logger.logSessionEvent("server", LogLevel.WARN, "Configuração de rede falhou: ${e.message}")
                }
            }

            // Loop de leitura da TUN (kernel -> clientes) em paralelo
            if (srvTun != null) {
                launch(Dispatchers.IO) {
                    val buf = ByteArray((srvTun.mtu + 64).coerceAtLeast(1600))
                    while (true) {
                        val n = try {
                            srvTun.readPacket(buf)
                        } catch (e: Exception) {
                            logger.logSessionEvent("server", LogLevel.ERROR, "Erro lendo TUN do servidor: ${e.message}")
                            break
                        }
                        if (n <= 0) continue
                        val dst = IPv4.dstAsInt(buf, n) ?: continue
                        val entry = routes.lookup(dst)
                        if (entry != null) {
                            // Entrega ao cliente correspondente
                            try {
                                entry.sendPacket(buf, n)
                            } catch (e: Exception) {
                                logger.logSessionEvent("server", LogLevel.WARN, "Falha ao enviar pacote da TUN para cliente: ${e.message}")
                            }
                        } else {
                            // Sem rota para cliente: pacote provavelmente não é para a sub-rede virtual; ignore
                            logger.logSessionEvent("server", LogLevel.DEBUG, "Sem rota para IP destino vindo da TUN")
                        }
                    }
                }
            } else {
                logger.logSessionEvent("server", LogLevel.INFO, "Executando sem TUN do servidor (modo overlay entre clientes)")
            }

            val buffer = ByteArray(65536) // Max UDP payload
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                launch(Dispatchers.IO) {
                    handlePacket(socket, packet, srvTun)
                }
            }
        }

    /**
     * Handles incoming UDP packets from clients.
     * Parses frames, manages sessions, and routes packets accordingly.
     */
    private fun handlePacket(
        socket: DatagramSocket,
        packet: DatagramPacket,
        srvTun: VirtualInterface?,
    ) {
        val clientKey = "${packet.address}:${packet.port}"
        val data = packet.data.copyOfRange(0, packet.length)

        val session =
            sessions.getOrPut(clientKey) {
                val sessionId = SessionTracker.createSession(clientKey)
                logger.logSessionEvent(sessionId, LogLevel.INFO, "New UDP session from $clientKey")
                SessionData(packet.address, packet.port, sessionId)
            }

        try {
            // Try to read frame
            val frame =
                PacketFramer.readFrameFromBytesWithSeq(data, key)
                    ?: run {
                        logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Invalid frame from $clientKey")
                        return
                    }

            // Anti-replay: rejeita duplicados/fora da janela
            if (!session.antiReplay.accept(frame.seq)) {
                logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Frame rejeitado por anti-replay")
                return
            }

            when (frame.type) {
                FrameType.AUTH -> {
                    handleAuth(socket, session, frame.payload)
                }
                FrameType.CONTROL -> {
                    if (!session.authenticated) {
                        logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Unauthenticated control from $clientKey")
                        return
                    }
                    handleControl(socket, session, frame.payload)
                }
                FrameType.PACKET -> {
                    if (!session.authenticated || session.assignedIp == null) {
                        logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Unauthorized packet from $clientKey")
                        return
                    }
                    handlePacketForward(socket, session, frame.payload, srvTun)
                }
                else -> {
                    logger.logSessionEvent(session.sessionId, LogLevel.WARN, "Unknown frame type from $clientKey")
                }
            }
        } catch (e: Exception) {
            logger.logSessionEvent(session.sessionId, LogLevel.ERROR, "Error handling packet: ${e.message}")
        }
    }

    /**
     * Processes authentication frames from clients.
     * Decrypts and validates credentials, then responds with success or failure.
     */
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

    /**
     * Handles control frames, such as IP requests.
     * Assigns IP addresses and sets up routing for authenticated clients.
     */
    private fun handleControl(
        socket: DatagramSocket,
        session: SessionData,
        payload: ByteArray,
    ) {
        if (payload.isEmpty()) return
        when (payload[0]) {
            ControlKind.IP_REQUEST -> {
                val ip =
                    ipPool.allocate()
                        ?: run {
                            logger.logSessionEvent(session.sessionId, LogLevel.WARN, "IP pool exhausted")
                            return
                        }
                session.assignedIp = ip.hostAddress
                val ipInt = IPv4.inet4ToInt(ip)
                val responsePayload =
                    byteArrayOf(
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

    /**
     * Forwards IP packets to the appropriate client session based on destination IP.
     */
    private fun handlePacketForward(
        socket: DatagramSocket,
        session: SessionData,
        payload: ByteArray,
        srvTun: VirtualInterface?,
    ) {
        val dst = IPv4.dstAsInt(payload, payload.size) ?: return
        val entry = routes.lookup(dst)
        if (entry != null) {
            entry.sendPacket(payload, payload.size)
        } else {
            // Sem rota para cliente: encaminhar para TUN do servidor para que o kernel roteie (egresso)
            if (srvTun != null) {
                try {
                    srvTun.writePacket(payload, payload.size)
                    logger.logSessionEvent(session.sessionId, LogLevel.DEBUG, "Encaminhado para TUN do servidor (egresso)")
                } catch (e: Exception) {
                    logger.logSessionEvent(session.sessionId, LogLevel.ERROR, "Falha ao escrever na TUN do servidor: ${e.message}")
                }
            } else {
                logger.logSessionEvent(session.sessionId, LogLevel.DEBUG, "No route for destination IP")
            }
        }
    }

    private fun sendResponse(
        socket: DatagramSocket,
        session: SessionData,
        code: Byte,
    ) {
        val payload = byteArrayOf(code)
        val frameData = PacketFramer.createFrameWithSeq(FrameType.AUTH_RESPONSE, session.sendSeq++, payload, key)
        val packet = DatagramPacket(frameData, frameData.size, session.address, session.port)
        socket.send(packet)
    }

    private fun sendFrame(
        socket: DatagramSocket,
        session: SessionData,
        type: Byte,
        payload: ByteArray,
    ) {
        val frameData = PacketFramer.createFrameWithSeq(type, session.sendSeq++, payload, key)
        val packet = DatagramPacket(frameData, frameData.size, session.address, session.port)
        socket.send(packet)
    }
}
