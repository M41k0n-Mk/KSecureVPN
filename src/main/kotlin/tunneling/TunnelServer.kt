package tunneling

import crypt.AESCipher
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

class TunnelServer(
    private val port: Int = 9000,
    private val key: SecretKey
) {
    fun start() = runBlocking {
        val server = ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))
        println("Server listening on port $port")
        while (true) {
            val client = server.accept()
            launch(Dispatchers.IO) {
                handleClient(client)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        println("Client connected: ${socket.inetAddress}")
        val reader = socket.getInputStream()
        while (true) {
            val iv = ByteArray(16)
            val ivRead = reader.read(iv)
            if (ivRead < iv.size) break

            val lengthBytes = ByteArray(4)
            val lenRead = reader.read(lengthBytes)
            if (lenRead < lengthBytes.size) break
            val cipherLength = lengthBytes.toInt()

            val cipherText = ByteArray(cipherLength)
            val ctRead = reader.read(cipherText)
            if (ctRead < cipherText.size) break

            val plain = try {
                AESCipher.decrypt(cipherText, key, iv)
            } catch (ex: Exception) {
                when (ex) {
                    is BadPaddingException, is IllegalBlockSizeException -> {
                        println("Decryption failed: The provided key is valid but does not match the one used for encryption. Make sure both client and server use the same key.")
                    }
                    else -> {
                        println("Decryption failed: ${ex.message}")
                    }
                }
                continue
            }
            println("Received: ${plain.decodeToString()}")
        }
        socket.close()
    }

    private fun ByteArray.toInt(): Int =
        ((this[0].toInt() and 0xFF) shl 24) or
                ((this[1].toInt() and 0xFF) shl 16) or
                ((this[2].toInt() and 0xFF) shl 8) or
                (this[3].toInt() and 0xFF)
}