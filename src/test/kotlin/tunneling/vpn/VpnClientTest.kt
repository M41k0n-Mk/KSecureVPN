package tunneling.vpn

import crypt.AESCipher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import tunneling.vpn.stub.MemoryTun
import java.util.concurrent.TimeUnit

class VpnClientTest {

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `client should create with valid parameters`() {
        val key = AESCipher.generateKey()
        val tun = MemoryTun("test-tun")

        val client = VpnClient(
            serverHost = "127.0.0.1",
            serverPort = 9001,
            key = key,
            username = "test",
            password = "pass".toCharArray(),
            vInterface = tun
        )

        assertNotNull(client)
        // Cannot test private properties
    }
}