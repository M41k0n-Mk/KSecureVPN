package tunneling.vpn.handshake

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Very small binary codec for handshake messages over UDP before data channel starts.
 * Format (all multi-byte fields big-endian):
 *  MAGIC(4)="KSV2" | TYPE(1) | FIELDS...
 */
object Messages {
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte(), '2'.code.toByte())

    const val TYPE_INIT: Byte = 1
    const val TYPE_RESP: Byte = 2
    const val TYPE_FIN: Byte = 3

    data class Init(
        val clientEphemeralPubX509: ByteArray,
        val clientNonce: ByteArray, // 32 bytes
    )

    data class Resp(
        val serverEphemeralPubX509: ByteArray,
        val serverStaticPubX509: ByteArray,
        val serverNonce: ByteArray, // 32 bytes
        val serverProofHmac: ByteArray, // 32 bytes (HMAC over transcript; placeholder)
    )

    data class Fin(
        val clientProofHmac: ByteArray, // 32 bytes
    )

    fun encodeInit(m: Init): ByteArray {
        val cap = 4 + 1 + 2 + m.clientEphemeralPubX509.size + 1 + 32
        val buf = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(TYPE_INIT)
        buf.putShort(m.clientEphemeralPubX509.size.toShort())
        buf.put(m.clientEphemeralPubX509)
        buf.put(32.toByte())
        buf.put(m.clientNonce)
        return buf.array()
    }

    fun decodeInit(bytes: ByteArray): Init? {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (!checkMagic(buf)) return null
        if (buf.remaining() < 1) return null
        val t = buf.get()
        if (t != TYPE_INIT) return null
        if (buf.remaining() < 2) return null
        val pubLen = buf.short.toInt() and 0xFFFF
        if (pubLen <= 0 || pubLen > 256 || buf.remaining() < pubLen + 1) return null
        val pub = ByteArray(pubLen)
        buf.get(pub)
        val nonceLen = buf.get().toInt() and 0xFF
        if (nonceLen != 32 || buf.remaining() < 32) return null
        val nonce = ByteArray(32)
        buf.get(nonce)
        return Init(pub, nonce)
    }

    fun encodeResp(m: Resp): ByteArray {
        val cap = 4 + 1 + 2 + m.serverEphemeralPubX509.size + 2 + m.serverStaticPubX509.size + 1 + 32 + 1 + 32
        val buf = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(TYPE_RESP)
        buf.putShort(m.serverEphemeralPubX509.size.toShort())
        buf.put(m.serverEphemeralPubX509)
        buf.putShort(m.serverStaticPubX509.size.toShort())
        buf.put(m.serverStaticPubX509)
        buf.put(32.toByte())
        buf.put(m.serverNonce)
        buf.put(32.toByte())
        buf.put(m.serverProofHmac)
        return buf.array()
    }

    fun decodeResp(bytes: ByteArray): Resp? {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (!checkMagic(buf)) return null
        if (buf.remaining() < 1) return null
        val t = buf.get()
        if (t != TYPE_RESP) return null
        if (buf.remaining() < 2) return null
        val eLen = buf.short.toInt() and 0xFFFF
        if (eLen <= 0 || eLen > 256 || buf.remaining() < eLen + 2) return null
        val e = ByteArray(eLen)
        buf.get(e)
        val sLen = buf.short.toInt() and 0xFFFF
        if (sLen <= 0 || sLen > 256 || buf.remaining() < sLen + 1 + 32 + 1 + 32) return null
        val s = ByteArray(sLen)
        buf.get(s)
        val nonceLen = buf.get().toInt() and 0xFF
        if (nonceLen != 32 || buf.remaining() < 32 + 1 + 32) return null
        val nonce = ByteArray(32)
        buf.get(nonce)
        val proofLen = buf.get().toInt() and 0xFF
        if (proofLen != 32 || buf.remaining() < 32) return null
        val proof = ByteArray(32)
        buf.get(proof)
        return Resp(e, s, nonce, proof)
    }

    fun encodeFin(m: Fin): ByteArray {
        val cap = 4 + 1 + 1 + 32
        val buf = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)
        buf.put(TYPE_FIN)
        buf.put(32.toByte())
        buf.put(m.clientProofHmac)
        return buf.array()
    }

    fun decodeFin(bytes: ByteArray): Fin? {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (!checkMagic(buf)) return null
        if (buf.remaining() < 1) return null
        val t = buf.get()
        if (t != TYPE_FIN) return null
        if (buf.remaining() < 1 + 32) return null
        val len = buf.get().toInt() and 0xFF
        if (len != 32 || buf.remaining() < 32) return null
        val proof = ByteArray(32)
        buf.get(proof)
        return Fin(proof)
    }

    private fun checkMagic(buf: ByteBuffer): Boolean {
        if (buf.remaining() < 5) return false
        val m0 = ByteArray(4)
        buf.get(m0)
        return m0.contentEquals(MAGIC)
    }
}
