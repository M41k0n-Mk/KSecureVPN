package tunneling

import crypt.AESCipher
import kotlinx.coroutines.*
import java.net.Socket
import javax.crypto.SecretKey

class TunnelClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 9000,
    private val key: SecretKey,
) {
    fun connect() =
        runBlocking {
            val startTime = System.currentTimeMillis()
            println("[CLIENT] Connecting to $host:$port...")

            val socket = Socket(host, port)
            println("[CLIENT] Connected to server: ${socket.inetAddress.hostAddress}")

            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            print("Username: ")
            val username = readlnOrNull() ?: ""
            print("Password: ")
            val password = System.console()?.readPassword()?.concatToString() ?: readlnOrNull().orEmpty()

            println("[CLIENT] Preparing authentication for user: $username")
            val authPayload =
                buildString {
                    append("AUTH\n")
                    append(username)
                    append('\n')
                    append(password)
                }.toByteArray()

            val authStartTime = System.currentTimeMillis()
            val (authCipher, authIv) = AESCipher.encrypt(authPayload, key)
            println("[CLIENT] Sending authentication (${authCipher.size + authIv.size + 4} bytes total)")

            out.write(authIv)
            out.write(authCipher.size.toBytes())
            out.write(authCipher)
            out.flush()

            val authSentTime = System.currentTimeMillis()
            println("[CLIENT] Authentication sent in ${authSentTime - authStartTime}ms")

            // Read authentication response
            try {
                val authResponseBuffer = ByteArray(1)
                val authResponseRead = readFully(input, authResponseBuffer, timeoutMillis = 5000, logPrefix = "CLIENT-AUTH-RESPONSE")

                if (authResponseRead > 0) {
                    when (authResponseBuffer[0]) {
                        ResponseCode.AUTH_FAILED -> {
                            println("[CLIENT] ❌ Authentication FAILED - Invalid credentials")
                            socket.close()
                            return@runBlocking
                        }
                        ResponseCode.AUTH_SUCCESS -> {
                            println("[CLIENT] ✅ Authentication SUCCESS")
                        }
                        ResponseCode.AUTH_MALFORMED -> {
                            println("[CLIENT] ❌ Authentication FAILED - Malformed request")
                            socket.close()
                            return@runBlocking
                        }
                        else -> {
                            println("[CLIENT] ⚠️ Unknown authentication response: ${authResponseBuffer[0]}")
                        }
                    }
                } else {
                    println("[CLIENT] ❌ No authentication response received")
                    socket.close()
                    return@runBlocking
                }
            } catch (ex: Exception) {
                println("[CLIENT] ❌ Authentication timeout or error: ${ex.message}")
                socket.close()
                return@runBlocking
            }

            // After auth, allow sending a normal message
            print("Enter message: ")
            val message = readlnOrNull() ?: ""

            if (message.isNotEmpty()) {
                val msgStartTime = System.currentTimeMillis()
                println("[CLIENT] Encrypting message: '$message' (${message.length} chars)")

                val (cipherText, iv) = AESCipher.encrypt(message.toByteArray(), key)
                println("[CLIENT] Sending message (${cipherText.size + iv.size + 4} bytes total)")

                out.write(iv)
                out.write(cipherText.size.toBytes())
                out.write(cipherText)
                out.flush()

                val msgSentTime = System.currentTimeMillis()
                println("[CLIENT] Message sent in ${msgSentTime - msgStartTime}ms")

                // Read message acknowledgment
                try {
                    val msgAckBuffer = ByteArray(1)
                    val msgAckRead = readFully(input, msgAckBuffer, timeoutMillis = 3000, logPrefix = "CLIENT-MSG-ACK")

                    if (msgAckRead > 0) {
                        when (msgAckBuffer[0]) {
                            ResponseCode.MSG_RECEIVED -> {
                                println("[CLIENT] ✅ Message received and processed by server")
                            }
                            else -> {
                                println("[CLIENT] ⚠️ Unknown message response: ${msgAckBuffer[0]}")
                            }
                        }
                    } else {
                        println("[CLIENT] ⚠️ No message acknowledgment received")
                    }
                } catch (ex: Exception) {
                    println("[CLIENT] ⚠️ Message acknowledgment timeout: ${ex.message}")
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            println("[CLIENT] Session completed in ${totalTime}ms")
            socket.close()
        }

    private fun Int.toBytes(): ByteArray =
        byteArrayOf(
            ((this shr 24) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            (this and 0xFF).toByte(),
        )
}
