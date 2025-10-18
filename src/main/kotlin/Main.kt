import crypt.AESCipher
import tunneling.TunnelClient
import tunneling.TunnelServer

import javax.crypto.SecretKey
import java.util.Base64
import kotlin.system.exitProcess

/**
 * Load key from environment, validating that when provided the value is base64
 * of exactly 32 bytes. For testing, use [loadKeyFrom].
 */
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
        println("Generated key (base64): ${Base64.getEncoder().encodeToString(generated.encoded)}")
        generated
    }
}

fun loadKey(): SecretKey = loadKeyFrom(System.getenv("KSECUREVPN_KEY"))

fun main(args: Array<String>) {
    val key: SecretKey = loadKey()
    val mode = if (args.isNotEmpty()) args[0] else {
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