package tunneling

import auth.AuthService
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
    private val key: SecretKey,
    private val authService: AuthService = AuthService()
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

        // Read first frame -> authentication
        val iv = ByteArray(16)
        val ivRead = readFully(reader, iv, logPrefix = "SERVER-AUTH")
        if (ivRead < iv.size) {
            socket.close(); return
        }

        val lengthBytes = ByteArray(4)
        val lenRead = readFully(reader, lengthBytes, logPrefix = "SERVER-AUTH")
        if (lenRead < lengthBytes.size) { socket.close(); return }
        val cipherLength = lengthBytes.toInt()

        val cipherText = ByteArray(cipherLength)
        val ctRead = readFully(reader, cipherText, logPrefix = "SERVER-AUTH")
        if (ctRead < cipherText.size) { socket.close(); return }

        val plainAuth = try {
            AESCipher.decrypt(cipherText, key, iv)
        } catch (ex: Exception) {
            println("Decryption failed during auth for ${socket.inetAddress}: ${ex.message}")
            socket.close()
            return
        }

        val authStr = plainAuth.decodeToString()
        val lines = authStr.split('\n')
        if (lines.size >= 3 && lines[0] == "AUTH") {
            val username = lines[1]
            val password = lines[2].toCharArray()
            val ok = authService.authenticate(username, password)
            if (!ok) {
                println("Authentication failed for user='$username' from ${socket.inetAddress}")
                socket.close()
                return
            }
            println("Authentication success for user='$username' from ${socket.inetAddress}")
        } else {
            println("Malformed auth payload from ${socket.inetAddress}")
            socket.close()
            return
        }

        // Now proceed to read normal frames (messages)
        while (true) {
            val ivMsg = ByteArray(16)
            val ivMsgRead = readFully(reader, ivMsg, logPrefix = "SERVER-MSG")
            if (ivMsgRead < ivMsg.size) break

            val lengthBytes2 = ByteArray(4)
            val lenRead2 = readFully(reader, lengthBytes2, logPrefix = "SERVER-MSG")
            if (lenRead2 < lengthBytes2.size) break
            val cipherLength2 = lengthBytes2.toInt()

            val cipherText2 = ByteArray(cipherLength2)
            val ctRead2 = readFully(reader, cipherText2, logPrefix = "SERVER-MSG")
            if (ctRead2 < cipherText2.size) break

            val plain = try {
                AESCipher.decrypt(cipherText2, key, ivMsg)
            } catch (ex: Exception) {
                when (ex) {
                    is BadPaddingException, is IllegalBlockSizeException -> {
                        println("Decryption failed: possible key mismatch.")
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

    // readFully moved to tunneling.StreamUtils

    private fun ByteArray.toInt(): Int =
        ((this[0].toInt() and 0xFF) shl 24) or
                ((this[1].toInt() and 0xFF) shl 16) or
                ((this[2].toInt() and 0xFF) shl 8) or
                (this[3].toInt() and 0xFF)
}