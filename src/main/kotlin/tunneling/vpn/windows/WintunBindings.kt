package tunneling.vpn.windows

import com.sun.jna.*
import com.sun.jna.ptr.PointerByReference
import logging.LogLevel
import logging.SecureLogger

/**
 * JNA bindings mínimos para a DLL do Wintun (Windows WireGuard TUN driver).
 * As assinaturas aqui são propositalmente simples, usando Pointer/HANDLE genérico
 * apenas para suportar as operações básicas de abrir/criar adapter, iniciar sessão,
 * enviar/receber pacotes e fechar.
 *
 * Referência: https://www.wintun.net/
 */
internal interface WintunNative : Library {
    fun WintunOpenAdapter(
        pool: WString?,
        name: WString?,
    ): Pointer?

    fun WintunCreateAdapter(
        pool: WString?,
        name: WString?,
        requestedGUID: GUID?
    ): Pointer?

    fun WintunCloseAdapter(adapter: Pointer?)

    fun WintunStartSession(
        adapter: Pointer?,
        capacity: Int,
    ): Pointer?

    fun WintunEndSession(session: Pointer?)

    fun WintunGetAdapterLUID(adapter: Pointer?, luid: Pointer?)

    fun WintunAllocateSendPacket(
        session: Pointer?,
        packetSize: Int,
    ): Pointer?

    fun WintunSendPacket(
        session: Pointer?,
        packet: Pointer?,
    )

    fun WintunReceivePacket(
        session: Pointer?,
    ): Pointer?

    fun WintunReleaseReceivePacket(
        session: Pointer?,
        packet: Pointer?,
    )

    fun WintunGetPacketSize(
        packet: Pointer?
    ): Int

    companion object {
        val INSTANCE: WintunNative? by lazy { Loader.load() }
    }
}

/** GUID struct simplificado para chamada de criação (pode ser null para GUID aleatório). */
internal class GUID : Structure() {
    @JvmField var Data1: Int = 0
    @JvmField var Data2: Short = 0
    @JvmField var Data3: Short = 0
    @JvmField var Data4: ByteArray = ByteArray(8)
    override fun getFieldOrder(): MutableList<String> = mutableListOf("Data1", "Data2", "Data3", "Data4")
}

internal object Loader {
    private val logger = SecureLogger.getInstance()
    private var tried = false
    private var cached: WintunNative? = null

    fun load(): WintunNative? {
        if (tried) return cached
        tried = true
        // Estratégia de carga:
        // 1) Se KSECUREVPN_WINTUN_DLL definido, tentar esse caminho primeiro
        // 2) Tentar por nome padrão "wintun" (no PATH)
        val custom = System.getenv("KSECUREVPN_WINTUN_DLL")
        try {
            if (!custom.isNullOrBlank()) {
                cached = Native.load(custom, WintunNative::class.java)
                logger.logSessionEvent("Wintun", LogLevel.INFO, "wintun.dll carregado do caminho personalizado", mapOf("path" to custom))
                return cached
            }
        } catch (e: UnsatisfiedLinkError) {
            logger.logSessionEvent("Wintun", LogLevel.WARN, "Falha ao carregar Wintun via caminho customizado: ${e.message}")
        } catch (e: Throwable) {
            logger.logSessionEvent("Wintun", LogLevel.WARN, "Erro ao carregar Wintun custom: ${e.message}")
        }

        return try {
            cached = Native.load("wintun", WintunNative::class.java)
            logger.logSessionEvent("Wintun", LogLevel.INFO, "wintun.dll carregado pelo sistema (PATH)")
            cached
        } catch (e: UnsatisfiedLinkError) {
            logger.logSessionEvent("Wintun", LogLevel.WARN, "wintun.dll não encontrado/compatível: ${e.message}")
            null
        } catch (e: Throwable) {
            logger.logSessionEvent("Wintun", LogLevel.WARN, "Falha ao carregar Wintun: ${e.message}")
            null
        }
    }

    fun isAvailable(): Boolean = WintunNative.INSTANCE != null
}
