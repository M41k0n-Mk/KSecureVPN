package tunneling.vpn.windows

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.WINDOWS)
class WintunCreationTest {
    @Test
    fun `should create and close wintun session on windows when dll available`() {
        // Pula se a DLL não puder ser carregada
        assumeTrue(Loader.isAvailable(), "wintun.dll não disponível neste ambiente de teste")

        val tun =
            try {
                WintunTun("ksecvpn-ut0")
            } catch (e: Exception) {
                // Em ambientes sem driver devidamente instalado, aceite pular o teste
                assumeTrue(false, "Falha ao iniciar WintunTun: ${e.message}")
                return
            }

        assertNotNull(tun)
        tun.close()
        // idempotente
        tun.close()
    }
}
