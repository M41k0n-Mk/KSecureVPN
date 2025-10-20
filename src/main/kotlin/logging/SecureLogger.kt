package logging

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Configurable secure logger that writes sensitive debug information to protected log files
 * while keeping console output generic and safe.
 */
class SecureLogger(
    private val config: LogConfig = LogConfig(),
) {
    private val lock = ReentrantReadWriteLock()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    init {
        if (config.enabled) {
            ensureLogDirectoryExists()
        }
    }

    private fun ensureLogDirectoryExists() {
        try {
            val logPath = Paths.get(config.logDirectory)
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath)
                // Try to set restrictive permissions on Unix-like systems
                try {
                    Files.setPosixFilePermissions(logPath, PosixFilePermissions.fromString("rwx------"))
                } catch (e: Exception) {
                    // Windows or unsupported - permissions will rely on OS defaults
                }
            }
        } catch (e: Exception) {
            System.err.println("Warning: Could not create log directory: ${e.message}")
        }
    }

    /**
     * Log a decryption error with session context.
     * Console receives only generic message; detailed error goes to protected log file.
     */
    fun logDecryptionError(
        sessionId: String,
        context: String,
        error: Throwable,
        additionalInfo: Map<String, String> = emptyMap(),
    ) {
        val timestamp = Instant.now()
        val formattedTime = dateFormatter.format(timestamp)

        // Generic console message - no sensitive details
        println("Decryption failed for session $sessionId: ${error.javaClass.simpleName}")

        if (!config.enabled) {
            return
        }

        // Detailed log entry for protected file
        val logEntry = buildString {
            appendLine("[$formattedTime] [ERROR] [Session: $sessionId]")
            appendLine("Context: $context")
            appendLine("Error Type: ${error.javaClass.name}")
            appendLine("Error Message: ${error.message}")
            if (config.includeStackTraces) {
                appendLine("Stack Trace:")
                error.stackTrace.take(10).forEach { frame ->
                    appendLine("  at $frame")
                }
            }
            if (additionalInfo.isNotEmpty()) {
                appendLine("Additional Info:")
                additionalInfo.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            appendLine("---")
        }

        writeToLogFile(logEntry)
    }

    /**
     * Log general session event for debugging.
     */
    fun logSessionEvent(
        sessionId: String,
        level: LogLevel,
        message: String,
        details: Map<String, String> = emptyMap(),
    ) {
        if (!config.enabled) {
            return
        }

        val timestamp = Instant.now()
        val formattedTime = dateFormatter.format(timestamp)

        val logEntry = buildString {
            appendLine("[$formattedTime] [${level.name}] [Session: $sessionId]")
            appendLine("Message: $message")
            if (details.isNotEmpty()) {
                appendLine("Details:")
                details.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            appendLine("---")
        }

        writeToLogFile(logEntry)
    }

    private fun writeToLogFile(content: String) {
        if (!config.enabled) {
            return
        }

        lock.write {
            try {
                val logFile = getLogFile()
                Files.writeString(
                    logFile,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )

                // Check and rotate if needed
                checkAndRotateLog(logFile)
            } catch (e: Exception) {
                System.err.println("Warning: Could not write to log file: ${e.message}")
            }
        }
    }

    private fun getLogFile(): Path {
        val fileName = config.logFileName
        return Paths.get(config.logDirectory, fileName)
    }

    private fun checkAndRotateLog(logFile: Path) {
        try {
            val file = logFile.toFile()
            if (file.exists() && file.length() > config.maxLogSizeBytes) {
                val backupName = "${config.logFileName}.${System.currentTimeMillis()}"
                val backupPath = Paths.get(config.logDirectory, backupName)
                Files.move(logFile, backupPath)
            }
        } catch (e: Exception) {
            System.err.println("Warning: Could not rotate log file: ${e.message}")
        }
    }

    companion object {
        // Singleton instance for application-wide use
        @Volatile
        private var instance: SecureLogger? = null

        fun getInstance(): SecureLogger {
            return instance ?: synchronized(this) {
                instance ?: SecureLogger().also { instance = it }
            }
        }

        fun configure(config: LogConfig) {
            synchronized(this) {
                instance = SecureLogger(config)
            }
        }
    }
}

/**
 * Configuration for SecureLogger
 */
data class LogConfig(
    val enabled: Boolean = true,
    val logDirectory: String = "logs",
    val logFileName: String = "ksecurevpn.log",
    val maxLogSizeBytes: Long = 10 * 1024 * 1024, // 10 MB default
    val includeStackTraces: Boolean = true,
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
