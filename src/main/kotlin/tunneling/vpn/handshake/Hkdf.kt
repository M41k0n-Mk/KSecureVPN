package tunneling.vpn.handshake

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal HKDF-SHA256 implementation (RFC 5869): extract + expand.
 * Used for key derivation in Noise protocol.
 */
object Hkdf {
    private const val HMAC = "HmacSHA256"

    fun extract(
        salt: ByteArray?,
        ikm: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(HMAC)
        val key = SecretKeySpec((salt ?: ByteArray(32) { 0 }), HMAC)
        mac.init(key)
        return mac.doFinal(ikm)
    }

    fun expand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length > 0 && length <= 255 * 32) { "invalid HKDF length" }
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(prk, HMAC))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, toCopy)
            pos += toCopy
            counter++
        }
        return out
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}
