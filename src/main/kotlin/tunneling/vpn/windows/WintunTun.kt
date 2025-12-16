package tunneling.vpn.windows

import com.sun.jna.Pointer
import logging.LogLevel
import logging.SecureLogger
import tunneling.vpn.VirtualInterface
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementação de VirtualInterface para Windows usando Wintun.
 * Opera em nível L3 (IPv4) similar ao RealTun do Linux.
 */
class WintunTun(
    requestedName: String = "ksecvpn0",
    override val mtu: Int = 1500,
) : VirtualInterface {
    override val name: String

    private val logger = SecureLogger.getInstance()
    private val closed = AtomicBoolean(false)

    private val api = WintunNative.INSTANCE
    private val adapter: Pointer
    private val session: Pointer

    init {
        ensureWindows()
        if (api == null) {
            throw UnsupportedOperationException("wintun.dll não está disponível (Loader.isAvailable() == false)")
        }

        val pool = com.sun.jna.WString("KSecureVPN")
        val desired = com.sun.jna.WString(requestedName)

        // Tentar abrir adapter existente
        var adp = api.WintunOpenAdapter(pool, desired)
        if (adp == null) {
            // Criar novo adapter (GUID null → random)
            adp = api.WintunCreateAdapter(pool, desired, null)
            if (adp == null) {
                logger.logSessionEvent("WintunTun", LogLevel.ERROR, "Falha ao criar adapter Wintun '$requestedName'")
                throw IOException("WintunCreateAdapter falhou para '$requestedName'")
            }
            logger.logSessionEvent("WintunTun", LogLevel.INFO, "Adapter Wintun criado", mapOf("name" to requestedName))
        } else {
            logger.logSessionEvent("WintunTun", LogLevel.INFO, "Adapter Wintun aberto", mapOf("name" to requestedName))
        }

        // Iniciar sessão com capacidade (ring buffer). 4 MiB é suficiente para protótipo.
        val capacity = 4 * 1024 * 1024
        val sess = api.WintunStartSession(adp, capacity)
        if (sess == null) {
            api.WintunCloseAdapter(adp)
            logger.logSessionEvent("WintunTun", LogLevel.ERROR, "Falha ao iniciar sessão Wintun")
            throw IOException("WintunStartSession falhou")
        }

        adapter = adp
        session = sess
        name = requestedName
        logger.logSessionEvent(
            "WintunTun",
            LogLevel.INFO,
            "Sessão Wintun iniciada",
            mapOf("name" to name, "mtu" to mtu.toString(), "capacity" to capacity.toString()),
        )
    }

    override fun readPacket(buffer: ByteArray): Int {
        if (closed.get()) return -1
        val p = api!!.WintunReceivePacket(session)
        if (p == null) {
            // Sem pacote no momento; comportamento não-bloqueante curto
            return 0
        }
        try {
            val size = api.WintunGetPacketSize(p)
            val n = size.coerceAtMost(buffer.size)
            p.read(0L, buffer, 0, n)
            return n
        } finally {
            api.WintunReleaseReceivePacket(session, p)
        }
    }

    override fun writePacket(
        packet: ByteArray,
        length: Int,
    ) {
        if (closed.get()) return
        val toWrite = length.coerceAtMost(packet.size)
        val p = api!!.WintunAllocateSendPacket(session, toWrite)
        if (p == null) {
            logger.logSessionEvent("WintunTun", LogLevel.ERROR, "WintunAllocateSendPacket retornou null")
            throw IOException("WintunAllocateSendPacket falhou")
        }
        // Copiar bytes para o buffer fornecido pela DLL
        p.write(0L, packet, 0, toWrite)
        api.WintunSendPacket(session, p)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            api?.WintunEndSession(session)
        } catch (e: Throwable) {
            logger.logSessionEvent("WintunTun", LogLevel.WARN, "Erro ao finalizar sessão Wintun: ${e.message}")
        }
        try {
            api?.WintunCloseAdapter(adapter)
        } catch (e: Throwable) {
            logger.logSessionEvent("WintunTun", LogLevel.WARN, "Erro ao fechar adapter Wintun: ${e.message}")
        }
        logger.logSessionEvent("WintunTun", LogLevel.INFO, "WintunTun fechado", mapOf("name" to name))
    }

    private fun ensureWindows() {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (!os.contains("windows")) {
            throw UnsupportedOperationException("WintunTun somente é suportado no Windows")
        }
    }
}
