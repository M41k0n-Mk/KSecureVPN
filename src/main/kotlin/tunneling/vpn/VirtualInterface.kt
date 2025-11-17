package tunneling.vpn

import java.io.Closeable

/**
 * Abstraction for a virtual network interface (TUN/TAP).
 *
 * Implementations should provide a way to read raw L3/L2 frames and write them back to the OS stack.
 * For first iteration we focus on TUN (L3 IPv4), without extra packet information (NO_PI).
 */
interface VirtualInterface : Closeable {
    /** Human-readable interface name, e.g. "tun0" */
    val name: String

    /** Maximum transmission unit of the underlying interface. */
    val mtu: Int

    /**
     * Blocking read of a single IP packet into [buffer].
     * Returns number of bytes written to [buffer], or -1 on EOF.
     * The buffer should be sized at least to [mtu] + IPv4 header.
     */
    fun readPacket(buffer: ByteArray): Int

    /** Write a raw IP packet to the interface (0..length bytes from [packet]). */
    fun writePacket(
        packet: ByteArray,
        length: Int,
    )

    override fun close()
}
