package tunneling

import kotlinx.coroutines.*
import java.net.Socket

class TunnelClient(private val host: String = "127.0.0.1", private val port: Int = 9000) {
    fun connect() = runBlocking {
        val socket = Socket(host, port)
        println("Connected to server: ${socket.inetAddress.hostAddress}")
        val out = socket.getOutputStream().bufferedWriter()
        out.write("Hello SecureVPN\n")
        out.flush()
        socket.close()
    }
}