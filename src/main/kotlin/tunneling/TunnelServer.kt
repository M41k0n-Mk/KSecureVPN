package tunneling

import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class TunnelServer(private val port: Int = 9000) {
    fun start() = runBlocking {
        val server = ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))
        println("Server listening on port $port")
        while (true) {
            val client = server.accept()
            launch(Dispatchers.IO) {
                handleClient(client)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        println("Client connected: ${socket.inetAddress}")
        val reader = socket.getInputStream().bufferedReader()
        reader.forEachLine {
            println("Received: $it")
        }
        socket.close()
    }
}