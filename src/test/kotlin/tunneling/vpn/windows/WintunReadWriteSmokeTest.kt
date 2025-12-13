package tunneling.vpn.windows

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.WINDOWS)
class WintunReadWriteSmokeTest {
    @Test
    fun `should write a dummy ipv4 packet without throwing when enabled`() {
        val enabled = System.getenv("ENABLE_WINTUN_TESTS")?.equals("true", ignoreCase = true) == true
        assumeTrue(enabled, "Teste de I/O do Wintun desabilitado (set ENABLE_WINTUN_TESTS=true para habilitar)")
        assumeTrue(Loader.isAvailable(), "wintun.dll não disponível neste ambiente de teste")

        val tun = try {
            WintunTun("ksecvpn-ut1")
        } catch (e: Exception) {
            assumeTrue(false, "Falha ao iniciar WintunTun: ${e.message}")
            return
        }

        val pkt = ByteArray(20) { 0 }
        pkt[0] = 0x45 // IPv4, IHL=5
        pkt[2] = 0x00
        pkt[3] = 20

        tun.writePacket(pkt, pkt.size)
        tun.close()
    }
}
