package crypt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AESCipherTest {
    @Test
    fun `generateKey should create valid AES key`() {
        val key = AESCipher.generateKey()
        assertNotNull(key)
        assertEquals("AES", key.algorithm)
        assertEquals(256, key.encoded.size * 8) // 32 bytes = 256 bits
    }

    @Test
    fun `keyFromBytes should create key from valid bytes`() {
        val bytes = ByteArray(32) { it.toByte() }
        val key = AESCipher.keyFromBytes(bytes)
        assertNotNull(key)
        assertEquals("AES", key.algorithm)
        assertArrayEquals(bytes, key.encoded)
    }

    @Test
    fun `keyFromBytes should reject invalid key size`() {
        val invalidBytes = ByteArray(16) // 128 bits, not 256
        assertThrows(IllegalArgumentException::class.java) {
            AESCipher.keyFromBytes(invalidBytes)
        }
    }

    @Test
    fun `encrypt and decrypt should be reversible`() {
        val key = AESCipher.generateKey()
        val plaintext = "Hello, World!".toByteArray()

        val (ciphertext, iv) = AESCipher.encrypt(plaintext, key)
        val decrypted = AESCipher.decrypt(ciphertext, key, iv)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong key should fail`() {
        val key1 = AESCipher.generateKey()
        val key2 = AESCipher.generateKey()
        val plaintext = "Test message".toByteArray()

        val (ciphertext, iv) = AESCipher.encrypt(plaintext, key1)

        assertThrows(Exception::class.java) {
            AESCipher.decrypt(ciphertext, key2, iv)
        }
    }

    @Test
    fun `encrypt should produce different ciphertext for same plaintext`() {
        val key = AESCipher.generateKey()
        val plaintext = "Same message".toByteArray()

        val (ciphertext1, _) = AESCipher.encrypt(plaintext, key)
        val (ciphertext2, _) = AESCipher.encrypt(plaintext, key)

        assertFalse(ciphertext1.contentEquals(ciphertext2))
    }
}
