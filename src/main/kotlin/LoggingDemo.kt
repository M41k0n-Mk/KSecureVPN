import logging.LogConfig
import logging.LogLevel
import logging.SecureLogger
import session.SessionTracker
import javax.crypto.BadPaddingException

/**
 * Demonstration of the secure logging and session tracking features.
 */
fun main() {
    println("=== KSecureVPN Logging Demo ===\n")

    // Configure logging
    val config =
        LogConfig(
            enabled = true,
            logDirectory = "logs",
            logFileName = "demo.log",
            maxLogSizeBytes = 10 * 1024 * 1024,
            includeStackTraces = true,
        )
    SecureLogger.configure(config)
    println("✅ Secure logging configured: logs/demo.log\n")

    // Create some sample sessions
    println("Creating sample sessions...")
    val session1 = SessionTracker.createSession("192.168.1.100")
    val session2 = SessionTracker.createSession("192.168.1.101")
    println("  Session 1: $session1")
    println("  Session 2: $session2\n")

    val logger = SecureLogger.getInstance()

    // Log some events
    println("Logging session events...")
    logger.logSessionEvent(
        sessionId = session1,
        level = LogLevel.INFO,
        message = "Client connected successfully",
        details =
            mapOf(
                "remoteAddress" to "192.168.1.100",
                "protocol" to "TCP",
            ),
    )
    println("  ✅ Logged: Client connected for $session1")

    logger.logSessionEvent(
        sessionId = session2,
        level = LogLevel.INFO,
        message = "Authentication successful",
        details =
            mapOf(
                "username" to "testuser",
                "method" to "password",
            ),
    )
    println("  ✅ Logged: Authentication successful for $session2\n")

    // Simulate and log a decryption error
    println("Simulating decryption error...")
    val error = BadPaddingException("Given final block not properly padded. Such issues can be due to bad key, wrong ciphertext, etc.")

    logger.logDecryptionError(
        sessionId = session1,
        context = "Message decryption phase",
        error = error,
        additionalInfo =
            mapOf(
                "cipherTextLength" to "256",
                "ivLength" to "16",
                "remoteAddress" to "192.168.1.100",
            ),
    )
    println("  ✅ Decryption error logged for $session1")
    println("     Console shows only: 'Decryption failed for session $session1: BadPaddingException'")
    println("     Full details are in the log file\n")

    // End sessions
    println("Ending sessions...")
    SessionTracker.endSession(session1)
    SessionTracker.endSession(session2)
    println("  ✅ Sessions ended\n")

    // Show what's in the log file
    println("=== Log File Contents ===")
    try {
        val logFile = java.nio.file.Paths.get("logs/demo.log")
        if (java.nio.file.Files.exists(logFile)) {
            val content = java.nio.file.Files.readString(logFile)
            println(content)
        } else {
            println("Log file not yet created")
        }
    } catch (e: Exception) {
        println("Could not read log file: ${e.message}")
    }

    println("\n=== Summary ===")
    println("✅ Session tracking: Each connection has a unique ID")
    println("✅ Secure logging: Detailed errors logged to protected file")
    println("✅ Console safety: Only generic messages on console")
    println("✅ No sensitive data: Keys and plaintext never logged")
    println("\nSee logs/demo.log for detailed session and error information.")
}
