package tunneling.vpn.linux

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Paths

@EnabledOnOs(OS.LINUX)
class RealTunReadWriteSmokeTest {
    @Test
    fun `should write a dummy ipv4 packet without throwing when enabled`() {
        val enabled = System.getenv("ENABLE_TUN_TESTS")?.equals("true", ignoreCase = true) == true
        assumeTrue(enabled, "Teste de I/O do TUN desabilitado (set ENABLE_TUN_TESTS=true para habilitar)")

        val devTunExists = Files.exists(Paths.get("/dev/net/tun"))
        assumeTrue(devTunExists, "/dev/net/tun não existe neste ambiente de teste")

        val tun = try {
            RealTun("ksecvpn-ut1")
        } catch (e: Exception) {
            // Sem CAP_NET_ADMIN ou sem permissões suficientes: pular o teste
            assumeTrue(false, "Sem permissões para criar TUN: ${e.message}")
            return
        }

        // Pacote IPv4 mínimo com header fictício (não necessariamente válido para o kernel)
        val pkt = ByteArray(20) { 0 }
        pkt[0] = 0x45 // Version=4, IHL=5
        pkt[2] = 0x00
        pkt[3] = 20 // total length baixo

        // Apenas garantir que a chamada não lança exceção
        tun.writePacket(pkt, pkt.size)
        tun.close()
    }
}
