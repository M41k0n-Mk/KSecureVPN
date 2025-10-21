import config.ConfigLoader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class ConfigLoaderTest {
    
    @Test
    fun `default configuration`() {
        val config = ConfigLoader.load()
        
        assertAll(
            { assertEquals("127.0.0.1", config.bindAddress) },
            { assertEquals(9000, config.port) },
            { assertTrue(config.allowedCidrs.isEmpty()) }
        )
    }
    
    @Test
    fun `CLI arguments override defaults`() {
        val cliArgs = mapOf(
            "bind" to "0.0.0.0",
            "port" to "8080",
            "allowed" to "192.168.1.0/24,::1"
        )
        
        val config = ConfigLoader.load(cliArgs)
        
        assertAll(
            { assertEquals("0.0.0.0", config.bindAddress) },
            { assertEquals(8080, config.port) },
            { assertEquals(listOf("192.168.1.0/24", "::1"), config.allowedCidrs) }
        )
    }
    
    @Test
    fun `invalid port falls back to default`() {
        val cliArgs = mapOf("port" to "invalid")
        val config = ConfigLoader.load(cliArgs)
        
        assertEquals(9000, config.port)
    }
    
    @Test
    fun `invalid bind address falls back to default`() {
        val cliArgs = mapOf("bind" to "invalid.address")
        val config = ConfigLoader.load(cliArgs)
        
        assertEquals("127.0.0.1", config.bindAddress)
    }
    
    @Test
    fun `server and client configs are properly configured`() {
        val cliArgs = mapOf(
            "bind" to "192.168.1.100",
            "port" to "8080",
            "allowed" to "192.168.1.0/24"
        )
        
        val config = ConfigLoader.load(cliArgs)
        
        assertAll(
            { assertEquals("192.168.1.100", config.serverConfig.bindAddress) },
            { assertEquals(8080, config.serverConfig.port) },
            { assertEquals(listOf("192.168.1.0/24"), config.serverConfig.allowedCidrs) },
            { assertEquals("192.168.1.100", config.clientConfig.targetHost) },
            { assertEquals(8080, config.clientConfig.targetPort) }
        )
    }
}