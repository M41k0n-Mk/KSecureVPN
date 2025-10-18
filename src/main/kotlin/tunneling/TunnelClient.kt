package tunneling

import crypt.AESCipher
import kotlinx.coroutines.*
import java.net.Socket
import javax.crypto.SecretKey

class TunnelClient(

    private val host: String = "127.0.0.1",
    private val port: Int = 9000,
    private val key: SecretKey
) {
    fun connect() = runBlocking {
        val socket = Socket(host, port)
        println("Connected to server: ${socket.inetAddress.hostAddress}")
        val out = socket.getOutputStream()

        print("Enter message: ")
        val message = readlnOrNull() ?: ""
        val (cipherText, iv) = AESCipher.encrypt(message.toByteArray(), key)

        out.write(iv)
        out.write(cipherText.size.toBytes())
        out.write(cipherText)
        out.flush()

        socket.close()
    }

    private fun Int.toBytes(): ByteArray =
        byteArrayOf(
            ((this shr 24) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            (this and 0xFF).toByte()
        )
}