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
        val authPayload = buildString {
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

        // Read authentication response (optional - server might just accept or close connection)
        try {
            // Try to read a simple ACK response (1 byte) with a short timeout
            val ackBuffer = ByteArray(1)
            val ackRead = readFully(input, ackBuffer, timeoutMillis = 1000, logPrefix = "CLIENT-AUTH-ACK")
            if (ackRead > 0) {
                println("[CLIENT] Received authentication acknowledgment: ${ackBuffer[0]}")
            }
        } catch (ex: Exception) {
            // Authentication response is optional - server might not send one
            println("[CLIENT] No authentication response (this is normal): ${ex.message}")
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
            
            // Try to read a message acknowledgment
            try {
                val msgAckBuffer = ByteArray(1)
                val msgAckRead = readFully(input, msgAckBuffer, timeoutMillis = 2000, logPrefix = "CLIENT-MSG-ACK")
                if (msgAckRead > 0) {
                    println("[CLIENT] Received message acknowledgment: ${msgAckBuffer[0]}")
                }
            } catch (ex: Exception) {
                println("[CLIENT] No message acknowledgment (this is normal): ${ex.message}")
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
            (this and 0xFF).toByte()
        )
}