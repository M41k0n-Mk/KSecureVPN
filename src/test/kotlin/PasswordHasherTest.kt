package auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PasswordHasherTest {
    @Test
    fun `hash should produce consistent results for same input`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("password123".toCharArray(), salt)
        val hash2 = PasswordHasher.hash("password123".toCharArray(), salt)

        assertArrayEquals(hash1, hash2)
    }

    @Test
    fun `hash should produce different results for different passwords`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("password1".toCharArray(), salt)
        val hash2 = PasswordHasher.hash("password2".toCharArray(), salt)

        assertFalse(hash1.contentEquals(hash2))
    }

    @Test
    fun `verify should succeed for correct password`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("password123".toCharArray(), salt)

        assertTrue(PasswordHasher.verify("password123".toCharArray(), salt, 100_000, hash))
    }

    @Test
    fun `verify should fail for incorrect password`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("password123".toCharArray(), salt)

        assertFalse(PasswordHasher.verify("wrongpass".toCharArray(), salt, 100_000, hash))
    }

    @Test
    fun `generateSalt should create random salt`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()

        assertNotNull(salt1)
        assertNotNull(salt2)
        assertEquals(16, salt1.size)
        assertFalse(salt1.contentEquals(salt2)) // Should be different
    }

    @Test
    fun `toBase64 and fromBase64 should be reversible`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val b64 = PasswordHasher.toBase64(bytes)
        val decoded = PasswordHasher.fromBase64(b64)

        assertArrayEquals(bytes, decoded)
    }
}
