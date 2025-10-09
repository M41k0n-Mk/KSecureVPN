import crypt.AESCipher
import tunneling.TunnelClient
import tunneling.TunnelServer

import javax.crypto.SecretKey
import java.util.Base64

fun loadKey(): SecretKey {
    val envKey = System.getenv("KSECUREVPN_KEY")
    return if (envKey != null) {
        val keyBytes = Base64.getDecoder().decode(envKey)
        AESCipher.keyFromBytes(keyBytes)
    } else {
        val generated = AESCipher.generateKey()
        println("Generated key (base64): ${Base64.getEncoder().encodeToString(generated.encoded)}")
        generated
    }
}

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