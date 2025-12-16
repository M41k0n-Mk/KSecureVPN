package crypt

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM helper with 12-byte nonces and 16-byte tags.
 */
object GcmCipher {
    private const val AES_ALGO = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_LEN = 12
    private const val TAG_LEN_BITS = 128

    fun generateKey(): SecretKey {
        val kg = KeyGenerator.getInstance(AES_ALGO)
        kg.init(256)
        return kg.generateKey()
    }

    fun keyFromBytes(bytes: ByteArray): SecretKey {
        require(bytes.size == 32) { "AES key must be exactly 32 bytes (256 bits)" }
        return SecretKeySpec(bytes, AES_ALGO)
    }

    fun encrypt(
        plain: ByteArray,
        key: SecretKey,
        aad: ByteArray? = null,
    ): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        val enc = cipher.doFinal(plain)
        return enc to nonce
    }

    fun decrypt(
        cipherText: ByteArray,
        key: SecretKey,
        nonce: ByteArray,
        aad: ByteArray? = null,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }
}
