import auth.AuthService
import crypt.AESCipher
import logging.LogConfig
import logging.SecureLogger
import tunneling.vpn.VpnClient
import tunneling.vpn.VpnServer
import tunneling.vpn.linux.RealTun
import tunneling.vpn.stub.MemoryTun
import tunneling.vpn.windows.WintunTun
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
            println("Modos disponíveis:")
            println("[1] VPN Demo (troca de pacotes IP)")
            println("[2] Iniciar Servidor VPN")
            println("[3] Conectar Cliente VPN")
            println("[4] Sair")
            print("Digite 1-4: ")
            when (readlnOrNull()) {
                "1" -> "vpn-demo"
                "2" -> "server"
                "3" -> "client"
                else -> ""
            }
        }

    when (mode) {
        "vpn-demo" -> {
            println("Executando VPN Demo...")
            // Import dinâmico ou executar VpnDemo.main()
            // Como não podemos importar aqui, sugerir executar separado
            println("Por favor, execute: kotlin VpnDemoKt")
        }
        "server" -> {
            println("Iniciando servidor VPN na porta 9001...")
            val authService = AuthService()
            authService.addUser("test", "password".toCharArray())
            val server = VpnServer(port = 9001, key = key, authService = authService)
            server.start()
        }
        "client" -> {
            println("Conectando cliente VPN ao localhost:9001...")
            val tun = createBestTunOrFallback()
            val client =
                VpnClient(
                    serverHost = "127.0.0.1",
                    serverPort = 9001,
                    key = key,
                    username = "test",
                    password = "password".toCharArray(),
                    vInterface = tun,
                )
            client.start()
        }
        else -> println("Modo inválido. Use 'vpn-demo', 'server' ou 'client'.")
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
                "No key printed for security. A development key was written to './ksecurevpn.key' with restrictive permissions.",
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

/**
 * Tenta criar a melhor VirtualInterface disponível para o SO atual.
 * - Windows: Wintun (wintun.dll) → fallback MemoryTun
 * - Linux: RealTun (/dev/net/tun) → fallback MemoryTun
 * - Outros: MemoryTun
 */
private fun createBestTunOrFallback(): tunneling.vpn.VirtualInterface {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    return try {
        when {
            os.contains("windows") -> {
                println("[client] Tentando Wintun (Windows)...")
                WintunTun("ksecvpn0")
            }
            os.contains("linux") -> {
                println("[client] Tentando RealTun (Linux /dev/net/tun)...")
                RealTun("ksecvpn0")
            }
            else -> {
                println("[client] SO não suportado para TUN real; usando MemoryTun")
                MemoryTun("memtun0")
            }
        }
    } catch (e: Exception) {
        println("[client] Falha ao criar TUN real (${e.message}); usando MemoryTun")
        MemoryTun("memtun0")
    }
}
