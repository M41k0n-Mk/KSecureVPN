package tunneling

import config.ClientConfig
import crypt.AESCipher
import kotlinx.coroutines.*
import java.net.Socket
import javax.crypto.SecretKey

/**
 * Secure tunnel client with idiomatic Kotlin design.
 */
class TunnelClient(
    private val config: ClientConfig,
    private val key: SecretKey,
) {
    // Backward compatibility constructor
    @Deprecated("Use ClientConfig constructor", ReplaceWith("TunnelClient(ClientConfig(host, port), key)"))
    constructor(host: String = "127.0.0.1", port: Int = 9000, key: SecretKey) : 
        this(ClientConfig(host, port), key)
    fun connect() =
        runBlocking {
            val startTime = System.currentTimeMillis()
            println("[CLIENT] Connecting to ${config.targetHost}:${config.targetPort}...")

            val socket = Socket(config.targetHost, config.targetPort)
            println("[CLIENT] Connected to server: ${socket.inetAddress.hostAddress}")

            val credentials = collectCredentials()
            
            socket.use { conn ->
                val authResult = authenticateWithServer(conn, credentials)
                if (!authResult) return@runBlocking
                
                handleMessageExchange(conn)
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            println("[CLIENT] Session completed in ${totalTime}ms")
        }

    private fun collectCredentials(): UserCredentials {
        print("Username: ")
        val username = readlnOrNull() ?: ""
        print("Password: ")
        val password = System.console()?.readPassword()?.concatToString() ?: readlnOrNull().orEmpty()
        return UserCredentials(username, password)
    }

    private suspend fun authenticateWithServer(socket: Socket, credentials: UserCredentials): Boolean {
        println("[CLIENT] Preparing authentication for user: ${credentials.username}")
        
        val authPayload = credentials.toAuthPayload()
        val authStartTime = System.currentTimeMillis()
        
        return runCatching {
            sendEncryptedData(socket, authPayload)
            val authSentTime = System.currentTimeMillis()
            println("[CLIENT] Authentication sent in ${authSentTime - authStartTime}ms")
            
            handleAuthResponse(socket)
        }.getOrElse { ex ->
            println("[CLIENT] ❌ Authentication timeout or error: ${ex.message}")
            false
        }
    }

    private suspend fun handleAuthResponse(socket: Socket): Boolean {
        val responseBuffer = ByteArray(1)
        val bytesRead = readFully(socket.getInputStream(), responseBuffer, timeoutMillis = 5000, logPrefix = "CLIENT-AUTH-RESPONSE")
        
        return when {
            bytesRead <= 0 -> {
                println("[CLIENT] ❌ No authentication response received")
                false
            }
            else -> when (val responseCode = responseBuffer[0]) {
                ResponseCode.AUTH_SUCCESS -> {
                    println("[CLIENT] ✅ Authentication SUCCESS")
                    true
                }
                ResponseCode.AUTH_FAILED -> {
                    println("[CLIENT] ❌ Authentication FAILED - Invalid credentials")
                    false
                }
                ResponseCode.AUTH_MALFORMED -> {
                    println("[CLIENT] ❌ Authentication FAILED - Malformed request")
                    false
                }
                else -> {
                    println("[CLIENT] ⚠️ Unknown authentication response: $responseCode")
                    false
                }
            }
        }

    }

    private suspend fun handleMessageExchange(socket: Socket) {
        print("Enter message: ")
        val message = readlnOrNull() ?: ""

        if (message.isNotEmpty()) {
            val msgStartTime = System.currentTimeMillis()
            println("[CLIENT] Encrypting message: '$message' (${message.length} chars)")

            sendEncryptedData(socket, message.toByteArray())
            val msgSentTime = System.currentTimeMillis()
            println("[CLIENT] Message sent in ${msgSentTime - msgStartTime}ms")

            handleMessageAcknowledgment(socket)
        }
    }

    private suspend fun handleMessageAcknowledgment(socket: Socket) {
        runCatching {
            val ackBuffer = ByteArray(1)
            val bytesRead = readFully(socket.getInputStream(), ackBuffer, timeoutMillis = 3000, logPrefix = "CLIENT-MSG-ACK")

            when {
                bytesRead <= 0 -> println("[CLIENT] ⚠️ No message acknowledgment received")
                ackBuffer[0] == ResponseCode.MSG_RECEIVED -> println("[CLIENT] ✅ Message received and processed by server")
                else -> println("[CLIENT] ⚠️ Unknown message response: ${ackBuffer[0]}")
            }
        }.onFailure { ex ->
            println("[CLIENT] ⚠️ Message acknowledgment timeout: ${ex.message}")
        }
    }

    private suspend fun sendEncryptedData(socket: Socket, data: ByteArray) {
        val (cipherText, iv) = AESCipher.encrypt(data, key)
        val totalSize = cipherText.size + iv.size + 4
        println("[CLIENT] Sending encrypted data ($totalSize bytes total)")

        socket.getOutputStream().apply {
            write(iv)
            write(cipherText.size.toBytes())
            write(cipherText)
            flush()
        }
    }

    private fun Int.toBytes(): ByteArray =
        byteArrayOf(
            ((this shr 24) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            (this and 0xFF).toByte(),
        )
}

/**
 * User credentials for authentication.
 */
private data class UserCredentials(
    val username: String,
    val password: String
) {
    fun toAuthPayload(): ByteArray = buildString {
        append("AUTH\n")
        append(username)
        append('\n')
        append(password)
    }.toByteArray()
}
