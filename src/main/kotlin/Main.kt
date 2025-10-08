import tunneling.TunnelClient
import tunneling.TunnelServer

fun main(args: Array<String>) {
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
        "server" -> TunnelServer().start()
        "client" -> TunnelClient().connect()
        else -> println("Usage: server | client")
    }
}