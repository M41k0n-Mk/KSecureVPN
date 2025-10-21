package tunneling

import auth.AuthService
import config.IpAllowlist
import config.ServerConfig
import crypt.AESCipher
import kotlinx.coroutines.*
import logging.LogLevel
import logging.SecureLogger
import session.SessionTracker
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey

/**
 * Secure tunnel server with idiomatic Kotlin design and IPv6 support.
 */
class TunnelServer(
    private val config: ServerConfig,
    private val key: SecretKey,
    private val authService: AuthService = AuthService(),
) {
    // Backward compatibility constructor
    @Deprecated("Use ServerConfig constructor", ReplaceWith("TunnelServer(ServerConfig(bindAddress, port, allowedCidrs), key, authService)"))
    constructor(
        bindAddress: String = "127.0.0.1",
        port: Int = 9000,
        key: SecretKey,
        authService: AuthService = AuthService(),
        allowedCidrs: List<String> = emptyList(),
    ) : this(ServerConfig(bindAddress, port, allowedCidrs), key, authService)

    fun start() =
        runBlocking {
            val server = createServerSocket()
            println("Server listening on ${config.bindAddress}:${config.port}")

            while (true) {
                val client = server.accept()

                if (isClientAllowed(client)) {
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                } else {
                    println("Rejected connection from ${client.inetAddress.hostAddress}: not in allowlist")
                    client.close()
                }
            }
        }

    private fun createServerSocket(): ServerSocket = ServerSocket(config.port, 0, InetAddress.getByName(config.bindAddress))

    private fun isClientAllowed(client: Socket): Boolean {
        val remoteAddress = client.inetAddress.hostAddress
        return IpAllowlist.isAllowed(remoteAddress, config.allowedCidrs)
    }

    private suspend fun handleClient(socket: Socket) {
        val sessionId = SessionTracker.createSession(socket.inetAddress.toString())
        val logger = SecureLogger.getInstance()

        println("Client connected: ${socket.inetAddress} [Session: $sessionId]")
        logger.logSessionEvent(
            sessionId = sessionId,
            level = LogLevel.INFO,
            message = "Client connected",
            details = mapOf("remoteAddress" to socket.inetAddress.toString()),
        )

        val reader = socket.getInputStream()

        // Read first frame -> authentication
        val iv = ByteArray(16)
        val ivRead = readFully(reader, iv, logPrefix = "SERVER-AUTH")
        if (ivRead < iv.size) {
            logger.logSessionEvent(sessionId, LogLevel.WARN, "Incomplete IV read during auth")
            SessionTracker.endSession(sessionId)
            socket.close()
            return
        }

        val lengthBytes = ByteArray(4)
        val lenRead = readFully(reader, lengthBytes, logPrefix = "SERVER-AUTH")
        if (lenRead < lengthBytes.size) {
            logger.logSessionEvent(sessionId, LogLevel.WARN, "Incomplete length read during auth")
            SessionTracker.endSession(sessionId)
            socket.close()
            return
        }
        val cipherLength = lengthBytes.toInt()

        val cipherText = ByteArray(cipherLength)
        val ctRead = readFully(reader, cipherText, logPrefix = "SERVER-AUTH")
        if (ctRead < cipherText.size) {
            logger.logSessionEvent(sessionId, LogLevel.WARN, "Incomplete ciphertext read during auth")
            SessionTracker.endSession(sessionId)
            socket.close()
            return
        }

        val plainAuth =
            try {
                AESCipher.decrypt(cipherText, key, iv)
            } catch (ex: Exception) {
                logger.logDecryptionError(
                    sessionId = sessionId,
                    context = "Authentication phase",
                    error = ex,
                    additionalInfo =
                        mapOf(
                            "remoteAddress" to socket.inetAddress.toString(),
                            "cipherTextLength" to cipherLength.toString(),
                            "ivLength" to iv.size.toString(),
                        ),
                )
                SessionTracker.endSession(sessionId)
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
                println("Authentication failed for user='$username' from ${socket.inetAddress} [Session: $sessionId]")
                logger.logSessionEvent(
                    sessionId = sessionId,
                    level = LogLevel.WARN,
                    message = "Authentication failed - invalid credentials",
                    details = mapOf("username" to username, "remoteAddress" to socket.inetAddress.toString()),
                )
                // Send authentication failure response
                socket.getOutputStream().write(byteArrayOf(ResponseCode.AUTH_FAILED))
                socket.getOutputStream().flush()
                SessionTracker.endSession(sessionId)
                socket.close()
                return
            }
            println("Authentication success for user='$username' from ${socket.inetAddress} [Session: $sessionId]")
            logger.logSessionEvent(
                sessionId = sessionId,
                level = LogLevel.INFO,
                message = "Authentication successful",
                details = mapOf("username" to username),
            )
            // Send authentication success response
            socket.getOutputStream().write(byteArrayOf(ResponseCode.AUTH_SUCCESS))
            socket.getOutputStream().flush()
        } else {
            println("Malformed auth payload from ${socket.inetAddress} [Session: $sessionId]")
            logger.logSessionEvent(
                sessionId = sessionId,
                level = LogLevel.WARN,
                message = "Malformed authentication payload",
                details = mapOf("remoteAddress" to socket.inetAddress.toString()),
            )
            // Send malformed request response
            socket.getOutputStream().write(byteArrayOf(ResponseCode.AUTH_MALFORMED))
            socket.getOutputStream().flush()
            SessionTracker.endSession(sessionId)
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

            val plain =
                try {
                    AESCipher.decrypt(cipherText2, key, ivMsg)
                } catch (ex: Exception) {
                    logger.logDecryptionError(
                        sessionId = sessionId,
                        context = "Message decryption",
                        error = ex,
                        additionalInfo =
                            mapOf(
                                "cipherTextLength" to cipherLength2.toString(),
                                "ivLength" to ivMsg.size.toString(),
                            ),
                    )
                    continue
                }
            println("Received: ${plain.decodeToString()}")
            logger.logSessionEvent(
                sessionId = sessionId,
                level = LogLevel.DEBUG,
                message = "Message received and decrypted",
                details = mapOf("messageLength" to plain.size.toString()),
            )

            // Send message acknowledgment
            socket.getOutputStream().write(byteArrayOf(ResponseCode.MSG_RECEIVED))
            socket.getOutputStream().flush()
        }

        logger.logSessionEvent(
            sessionId = sessionId,
            level = LogLevel.INFO,
            message = "Session ended",
            details = mapOf("remoteAddress" to socket.inetAddress.toString()),
        )
        SessionTracker.endSession(sessionId)
        socket.close()
    }

    private fun ByteArray.toInt(): Int =
        ((this[0].toInt() and 0xFF) shl 24) or
            ((this[1].toInt() and 0xFF) shl 16) or
            ((this[2].toInt() and 0xFF) shl 8) or
            (this[3].toInt() and 0xFF)
}
