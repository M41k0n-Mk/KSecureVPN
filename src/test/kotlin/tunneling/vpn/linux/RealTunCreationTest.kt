package tunneling.vpn.linux

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Paths

@EnabledOnOs(OS.LINUX)
class RealTunCreationTest {
    @Test
    fun `should create and close tun device on linux when permitted`() {
        val devTunExists = Files.exists(Paths.get("/dev/net/tun"))
        assumeTrue(devTunExists, "/dev/net/tun não existe neste ambiente de teste")

        val tun = try {
            RealTun("ksecvpn-ut0")
        } catch (e: Exception) {
            // Sem CAP_NET_ADMIN ou sem permissões suficientes: pular o teste
            assumeTrue(false, "Sem permissões para criar TUN: ${e.message}")
            return
        }

        assertTrue(tun.name.isNotBlank())
        tun.close()
        // fechamento idempotente não deve falhar
        tun.close()
    }
}
