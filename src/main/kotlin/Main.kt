import crypt.AESCipher
import logging.LogConfig
import logging.SecureLogger
import tunneling.TunnelClient
import tunneling.TunnelServer
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import javax.crypto.SecretKey

/**
 * Load key from environment, validating that when provided the value is base64
 * of exactly 32 bytes. For testing, use [loadKeyFrom].
 */

fun main(args: Array<String>) {
    configureLogging()
    val key: SecretKey = loadKey()
    val mode =
        if (args.isNotEmpty()) {
            args[0]
        } else {
            println("Escolha o modo:")
            println("[1] Server")
            println("[2] Client")
            print("Digite 1 ou 2: ")
            when (readlnOrNull()) {
                "1" -> "server"
                "2" -> "client"
                else -> ""
            }
        }

    when (mode) {
        "server" -> TunnelServer(key = key).start()
        "client" -> TunnelClient(key = key).connect()
        else -> println("Usage: server | client")
    }
}

fun configureLogging() {
    val loggingEnabled = System.getenv("KSECUREVPN_LOGGING_ENABLED")?.toBoolean() ?: true
    val logDirectory = System.getenv("KSECUREVPN_LOG_DIR") ?: "logs"
    val logFileName = System.getenv("KSECUREVPN_LOG_FILE") ?: "ksecurevpn.log"
    val maxLogSizeMB = System.getenv("KSECUREVPN_LOG_MAX_SIZE_MB")?.toLongOrNull() ?: 10L
    val includeStackTraces = System.getenv("KSECUREVPN_LOG_STACK_TRACES")?.toBoolean() ?: true

    val config =
        LogConfig(
            enabled = loggingEnabled,
            logDirectory = logDirectory,
            logFileName = logFileName,
            maxLogSizeBytes = maxLogSizeMB * 1024 * 1024,
            includeStackTraces = includeStackTraces,
        )

    SecureLogger.configure(config)

    if (loggingEnabled) {
        println("Secure logging enabled: logs will be written to $logDirectory/$logFileName")
    } else {
        println("Secure logging disabled")
    }
}

fun loadKey(): SecretKey = loadKeyFrom(System.getenv("KSECUREVPN_KEY"))

fun loadKeyFrom(envKey: String?): SecretKey {
    return if (envKey != null) {
        try {
            val keyBytes = Base64.getDecoder().decode(envKey)
            if (keyBytes.size != 32) {
                throw IllegalArgumentException("KSECUREVPN_KEY must be base64 of a 32-byte AES key.")
            }
            AESCipher.keyFromBytes(keyBytes)
        } catch (e: IllegalArgumentException) {
            // rethrow to preserve message (Base64 decode may throw IllegalArgumentException)
            throw IllegalArgumentException("KSECUREVPN_KEY must be base64 of a 32-byte AES key.")
        }
    } else {
        val generated = AESCipher.generateKey()
        try {
            val out = Paths.get("./ksecurevpn.key")
            val keyB64 = Base64.getEncoder().encodeToString(generated.encoded)
            val attrs =
                try {
                    PosixFilePermissions.fromString("rw-------")
                } catch (e: Exception) {
                    null
                }
            if (attrs != null) {
                val opts = arrayOf<OpenOption>(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                Files.write(out, keyB64.toByteArray(Charsets.UTF_8), *opts)
                try {
                    Files.setPosixFilePermissions(out, attrs)
                } catch (e: Exception) {
                }
            } else {
                Files.write(out, keyB64.toByteArray(Charsets.UTF_8))
            }

            println(
                    "No key printed for security. A development key was written to './ksecurevpn.key' with restrictive permissions."
            )
            println(
                "To use this key, set environment variable KSECUREVPN_KEY to the base64 contents of that file or move it to your secrets manager.",
            )
        } catch (e: Exception) {
            // If we couldn't write the key, provide instructions to generate and store it securely
            println(
                "No key could be printed or written. Generate a 32-byte base64 key locally and set KSECUREVPN_KEY environment variable.",
            )
            println("Example (Linux/macOS):")
            println("  head -c 32 /dev/urandom | base64 | tee ksecurevpn.key")
            println("Then: export KSECUREVPN_KEY=\$(cat ksecurevpn.key)")
        }

        generated
    }
}
