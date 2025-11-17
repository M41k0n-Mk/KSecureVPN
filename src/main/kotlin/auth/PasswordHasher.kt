package auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val DEFAULT_ITERATIONS = 100_000
    private const val KEY_LENGTH = 256

    fun generateSalt(length: Int = 16): ByteArray {
        val s = ByteArray(length)
        SecureRandom().nextBytes(s)
        return s
    }

    fun hash(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    fun verify(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        expectedHash: ByteArray,
    ): Boolean {
        val derived = hash(password, salt, iterations)
        return MessageDigest.isEqual(derived, expectedHash)
    }

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(s: String): ByteArray = Base64.getDecoder().decode(s)
}
