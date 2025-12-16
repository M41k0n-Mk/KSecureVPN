package tunneling.vpn

import crypt.GcmCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.SecretKey

/**
 * Helpers to send and receive framed VPN messages over the existing encrypted channel format:
 * IV(16) | LEN(4, big endian) | CIPHERTEXT(LEN)
 *
 * Plaintext structure created by higher-level callers:
 * TYPE(1) | PAYLOAD
 */
object PacketFramer {
    data class Frame(val type: Byte, val seq: Long, val payload: ByteArray)

    // New API with sequence
    suspend fun sendFrameWithSeq(
        out: OutputStream,
        key: SecretKey,
        type: Byte,
        seq: Long,
        payload: ByteArray,
    ) {
        val plain = ByteArray(1 + 8 + payload.size)
        plain[0] = type
        plain.writeLong(1, seq)
        System.arraycopy(payload, 0, plain, 9, payload.size)
        val (cipher, nonce) = GcmCipher.encrypt(plain, key, null)
        withContext(Dispatchers.IO) {
            out.write(nonce)
            out.write(cipher.size.toBytes())
            out.write(cipher)
            out.flush()
        }
    }

    /** Creates a framed message as byte array for UDP. */
    fun createFrameWithSeq(
        type: Byte,
        seq: Long,
        payload: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val plain = ByteArray(1 + 8 + payload.size)
        plain[0] = type
        plain.writeLong(1, seq)
        System.arraycopy(payload, 0, plain, 9, payload.size)
        val (cipher, nonce) = GcmCipher.encrypt(plain, key, null)
        val frame = ByteArray(12 + 4 + cipher.size)
        System.arraycopy(nonce, 0, frame, 0, 12)
        System.arraycopy(cipher.size.toBytes(), 0, frame, 12, 4)
        System.arraycopy(cipher, 0, frame, 16, cipher.size)
        return frame
    }

    /** Reads one frame from InputStream. Returns Frame or null on invalid. */
    suspend fun readFrameWithSeq(
        input: InputStream,
        key: SecretKey,
    ): Frame? {
        val nonce = ByteArray(12)
        val lenBytes = ByteArray(4)
        withContext(Dispatchers.IO) {
            if (input.read(nonce) != 12) return@withContext null
            if (input.read(lenBytes) != 4) return@withContext null
        }
        val len = lenBytes.toInt()
        if (len <= 0 || len > 65540) return null
        val cipher = ByteArray(len)
        withContext(Dispatchers.IO) {
            if (input.read(cipher) != len) return@withContext null
        }
        val plain = GcmCipher.decrypt(cipher, key, nonce, null)
        if (plain.size < 9) return null
        val type = plain[0]
        val seq = plain.readLong(1)
        val payload = plain.copyOfRange(9, plain.size)
        return Frame(type, seq, payload)
    }

    /** Reads one frame from byte array. Returns Frame or null on invalid. */
    fun readFrameFromBytesWithSeq(
        data: ByteArray,
        key: SecretKey,
    ): Frame? {
        if (data.size < 16) return null
        val nonce = data.copyOfRange(0, 12)
        val lenBytes = data.copyOfRange(12, 16)
        val len = lenBytes.toInt()
        if (len <= 0 || len > 65540 || 16 + len > data.size) return null
        val cipher = data.copyOfRange(16, 16 + len)
        val plain = GcmCipher.decrypt(cipher, key, nonce, null)
        if (plain.size < 9) return null
        val type = plain[0]
        val seq = plain.readLong(1)
        val payload = plain.copyOfRange(9, plain.size)
        return Frame(type, seq, payload)
    }

    // ----- Backward-compatible wrappers (legacy API expected by existing tests) -----
    suspend fun sendFrame(
        out: OutputStream,
        key: SecretKey,
        type: Byte,
        payload: ByteArray,
    ) = sendFrameWithSeq(out, key, type, 1L, payload)

    fun createFrame(
        type: Byte,
        payload: ByteArray,
        key: SecretKey,
    ): ByteArray = createFrameWithSeq(type, 1L, payload, key)

    suspend fun readFrame(
        input: InputStream,
        key: SecretKey,
    ): Pair<Byte, ByteArray>? = readFrameWithSeq(input, key)?.let { it.type to it.payload }

    fun readFrameFromBytes(
        data: ByteArray,
        key: SecretKey,
    ): Pair<Byte, ByteArray>? = readFrameFromBytesWithSeq(data, key)?.let { it.type to it.payload }
}

private fun Int.toBytes(): ByteArray =
    byteArrayOf(
        ((this ushr 24) and 0xFF).toByte(),
        ((this ushr 16) and 0xFF).toByte(),
        ((this ushr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )

private fun ByteArray.toInt(): Int =
    ((this[0].toInt() and 0xFF) shl 24) or
        ((this[1].toInt() and 0xFF) shl 16) or
        ((this[2].toInt() and 0xFF) shl 8) or
        (this[3].toInt() and 0xFF)

private fun ByteArray.writeLong(
    offset: Int,
    value: Long,
) {
    var v = value
    for (i in 7 downTo 0) {
        this[offset + (7 - i)] = ((v ushr (i * 8)) and 0xFF).toByte()
    }
}

private fun ByteArray.readLong(offset: Int): Long {
    var r = 0L
    for (i in 0 until 8) {
        r = (r shl 8) or ((this[offset + i].toInt() and 0xFF).toLong())
    }
    return r
}
