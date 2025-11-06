package tunneling.vpn

import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Simple IPv4 pool allocator for a /24 like 10.8.0.0/24.
 * Skips network(.0) and gateway(.1), starts allocating from .2 by default.
 */
class IpPool(cidrBase: String = "10.8.0.0", private val prefixLen: Int = 24, reserveGateway: Boolean = true) {
    private val network: Int
    private val mask: Int
    private val free = ConcurrentLinkedQueue<Int>()
    private val inUse = ConcurrentHashMap.newKeySet<Int>()

    init {
        require(prefixLen in 8..30) { "prefixLen out of range" }
        val addr = InetAddress.getByName(cidrBase) as Inet4Address
        network = inet4ToInt(addr.address)
        mask = if (prefixLen == 0) 0 else (-1 shl (32 - prefixLen))
        val size = 1 shl (32 - prefixLen)
        // populate usable hosts
        val start = 1 + if (reserveGateway) 1 else 0 // skip .0 (network) and optionally .1 (gateway)
        val end = size - 2 // skip broadcast
        for (i in start..end) {
            free.add((network and mask) + i)
        }
    }

    fun allocate(): Inet4Address? {
        val ip = free.poll() ?: return null
        inUse.add(ip)
        return intToInet4(ip)
    }

    fun release(address: Inet4Address) {
        val ip = inet4ToInt(address.address)
        if (inUse.remove(ip)) {
            free.add(ip)
        }
    }

    fun gateway(): Inet4Address = intToInet4((network and mask) + 1)

    private fun inet4ToInt(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).int

    private fun intToInet4(value: Int): Inet4Address =
        InetAddress.getByAddress(
            byteArrayOf(
                ((value ushr 24) and 0xFF).toByte(),
                ((value ushr 16) and 0xFF).toByte(),
                ((value ushr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            ),
        ) as Inet4Address
}
