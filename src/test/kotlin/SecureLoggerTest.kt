import logging.LogConfig
import logging.LogLevel
import logging.SecureLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class SecureLoggerTest {
    @Test
    fun `logger creates log directory when enabled`(@TempDir tempDir: Path) {
        val logDir = tempDir.resolve("test-logs")
        val config = LogConfig(
            enabled = true,
            logDirectory = logDir.toString(),
            logFileName = "test.log"
        )
        
        SecureLogger(config)
        
        assertTrue(Files.exists(logDir))
        assertTrue(Files.isDirectory(logDir))
    }

    @Test
    fun `decryption error logged to file but not console with sensitive details`(@TempDir tempDir: Path) {
        val logDir = tempDir.resolve("test-logs")
        val config = LogConfig(
            enabled = true,
            logDirectory = logDir.toString(),
            logFileName = "test.log"
        )
        
        val logger = SecureLogger(config)
        val sessionId = "test-session-123"
        val error = RuntimeException("Test decryption error")
        
        // Capture console output
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        
        try {
            logger.logDecryptionError(
                sessionId = sessionId,
                context = "Test context",
                error = error,
                additionalInfo = mapOf("key" to "value")
            )
        } finally {
            System.out.flush()
            System.setOut(originalOut)
        }
        
        val consoleOutput = baos.toString()
        
        // Console should have generic message
        assertTrue(consoleOutput.contains(sessionId))
        assertTrue(consoleOutput.contains("Decryption failed"))
        assertFalse(consoleOutput.contains("Test decryption error")) // Detailed message should not be in console
        
        // Log file should have detailed information
        val logFile = logDir.resolve("test.log")
        assertTrue(Files.exists(logFile))
        
        val logContent = Files.readString(logFile)
        assertTrue(logContent.contains(sessionId))
        assertTrue(logContent.contains("Test context"))
        assertTrue(logContent.contains("Test decryption error"))
        assertTrue(logContent.contains("key: value"))
    }

    @Test
    fun `session event logged correctly`(@TempDir tempDir: Path) {
        val logDir = tempDir.resolve("test-logs")
        val config = LogConfig(
            enabled = true,
            logDirectory = logDir.toString(),
            logFileName = "test.log"
        )
        
        val logger = SecureLogger(config)
        val sessionId = "test-session-456"
        
        logger.logSessionEvent(
            sessionId = sessionId,
            level = LogLevel.INFO,
            message = "Test session event",
            details = mapOf("detail1" to "value1")
        )
        
        val logFile = logDir.resolve("test.log")
        assertTrue(Files.exists(logFile))
        
        val logContent = Files.readString(logFile)
        assertTrue(logContent.contains(sessionId))
        assertTrue(logContent.contains("INFO"))
        assertTrue(logContent.contains("Test session event"))
        assertTrue(logContent.contains("detail1: value1"))
    }

    @Test
    fun `logger disabled does not write to file`(@TempDir tempDir: Path) {
        val logDir = tempDir.resolve("test-logs")
        val config = LogConfig(
            enabled = false,
            logDirectory = logDir.toString(),
            logFileName = "test.log"
        )
        
        val logger = SecureLogger(config)
        
        // Suppress console output for this test
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        
        try {
            logger.logSessionEvent(
                sessionId = "test",
                level = LogLevel.INFO,
                message = "Test"
            )
        } finally {
            System.setOut(originalOut)
        }
        
        val logFile = logDir.resolve("test.log")
        assertFalse(Files.exists(logFile))
    }

    @Test
    fun `no sensitive key or plaintext in logs`(@TempDir tempDir: Path) {
        val logDir = tempDir.resolve("test-logs")
        val config = LogConfig(
            enabled = true,
            logDirectory = logDir.toString(),
            logFileName = "test.log"
        )
        
        val logger = SecureLogger(config)
        val sessionId = "test-session-789"
        val error = RuntimeException("Decryption failed")
        
        // Suppress console output
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        
        try {
            // Log should not contain any base64-like strings that could be keys
            logger.logDecryptionError(
                sessionId = sessionId,
                context = "Authentication",
                error = error
            )
        } finally {
            System.setOut(originalOut)
        }
        
        val logFile = logDir.resolve("test.log")
        val logContent = Files.readString(logFile)
        
        // Verify no long base64 strings (potential keys) are in the logs
        val base64Regex = Regex("[A-Za-z0-9+/]{40,}={0,2}")
        assertFalse(base64Regex.containsMatchIn(logContent), 
            "Log file must not contain base64 key material")
    }
}
