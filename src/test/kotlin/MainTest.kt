import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

class MainTest {

    @Test
    fun `loadKeyFrom accepts valid 32 byte base64 key`() {
        val keyBytes = ByteArray(32) { it.toByte() }
        val b64 = Base64.getEncoder().encodeToString(keyBytes)
        val key = loadKeyFrom(b64)
        assertNotNull(key)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun `loadKeyFrom rejects key with wrong length`() {
        val keyBytes = ByteArray(16) { it.toByte() } // 16 bytes only
        val b64 = Base64.getEncoder().encodeToString(keyBytes)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            loadKeyFrom(b64)
        }
        assertEquals("KSECUREVPN_KEY must be base64 of a 32-byte AES key.", ex.message)
    }

    @Test
    fun `loadKeyFrom rejects non base64 input`() {
        val bad = "not-base64!!!"
        val ex = assertThrows(IllegalArgumentException::class.java) {
            loadKeyFrom(bad)
        }
        assertEquals("KSECUREVPN_KEY must be base64 of a 32-byte AES key.", ex.message)
    }
}
