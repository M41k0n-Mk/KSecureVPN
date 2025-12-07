package tunneling.vpn

import crypt.AESCipher
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
    suspend fun sendFrame(
        out: OutputStream,
        key: SecretKey,
        type: Byte,
        payload: ByteArray,
    ) {
        val plain = ByteArray(1 + payload.size)
        plain[0] = type
        System.arraycopy(payload, 0, plain, 1, payload.size)
        val (cipher, iv) = AESCipher.encrypt(plain, key)
        withContext(Dispatchers.IO) {
            out.write(iv)
            out.write(cipher.size.toBytes())
            out.write(cipher)
            out.flush()
        }
    }

    /** Creates a framed message as byte array for UDP. */
    fun createFrame(
        type: Byte,
        payload: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val plain = ByteArray(1 + payload.size)
        plain[0] = type
        System.arraycopy(payload, 0, plain, 1, payload.size)
        val (cipher, iv) = AESCipher.encrypt(plain, key)
        val frame = ByteArray(16 + 4 + cipher.size)
        System.arraycopy(iv, 0, frame, 0, 16)
        System.arraycopy(cipher.size.toBytes(), 0, frame, 16, 4)
        System.arraycopy(cipher, 0, frame, 20, cipher.size)
        return frame
    }

    /** Reads one frame from InputStream. Returns Pair(type, payload) or null on invalid. */
    suspend fun readFrame(
        input: InputStream,
        key: SecretKey,
    ): Pair<Byte, ByteArray>? {
        val iv = ByteArray(16)
        val lenBytes = ByteArray(4)
        withContext(Dispatchers.IO) {
            if (input.read(iv) != 16) return@withContext null
            if (input.read(lenBytes) != 4) return@withContext null
        }
        val len = lenBytes.toInt()
        if (len <= 0 || len > 65540) return null
        val cipher = ByteArray(len)
        withContext(Dispatchers.IO) {
            if (input.read(cipher) != len) return@withContext null
        }
        val plain = AESCipher.decrypt(cipher, key, iv)
        if (plain.isEmpty()) return null
        val type = plain[0]
        val payload = plain.copyOfRange(1, plain.size)
        return type to payload
    }

    /** Reads one frame from byte array. Returns Pair(type, payload) or null on invalid. */
    fun readFrameFromBytes(
        data: ByteArray,
        key: SecretKey,
    ): Pair<Byte, ByteArray>? {
        if (data.size < 20) return null
        val iv = data.copyOfRange(0, 16)
        val lenBytes = data.copyOfRange(16, 20)
        val len = lenBytes.toInt()
        if (len <= 0 || len > 65540 || 20 + len > data.size) return null
        val cipher = data.copyOfRange(20, 20 + len)
        val plain = AESCipher.decrypt(cipher, key, iv)
        if (plain.isEmpty()) return null
        val type = plain[0]
        val payload = plain.copyOfRange(1, plain.size)
        return type to payload
    }
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
