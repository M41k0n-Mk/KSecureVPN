package tunneling.vpn

import auth.AuthService
import crypt.AESCipher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VpnServerTest {
    @Test
    fun `server should create with valid parameters`() {
        val key = AESCipher.generateKey()
        val authService = AuthService()
        authService.addUser("testuser", "password".toCharArray())

        val server = VpnServer(port = 9001, key = key, authService = authService)

        assertNotNull(server)
        // Cannot test start without mocking
    }
}
