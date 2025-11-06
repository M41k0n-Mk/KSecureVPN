package tunneling.vpn.stub

import tunneling.vpn.VirtualInterface
import java.util.concurrent.ArrayBlockingQueue

/**
 * In-memory VirtualInterface for tests and local simulations. Not connected to OS.
 */
class MemoryTun(override val name: String = "memtun0", override val mtu: Int = 1500) : VirtualInterface {
    private val inbound = ArrayBlockingQueue<ByteArray>(1024)

    override fun readPacket(buffer: ByteArray): Int {
        val pkt = inbound.take() // blocking
        val n = pkt.size.coerceAtMost(buffer.size)
        System.arraycopy(pkt, 0, buffer, 0, n)
        return n
    }

    override fun writePacket(packet: ByteArray, length: Int) {
        val bytes = ByteArray(length)
        System.arraycopy(packet, 0, bytes, 0, length)
        inbound.put(bytes)
    }

    override fun close() {
        // nothing
    }
}
