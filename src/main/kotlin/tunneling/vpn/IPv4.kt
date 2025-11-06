package tunneling.vpn

import java.net.Inet4Address
import java.net.InetAddress

object IPv4 {
    /** Returns header length in bytes from an IPv4 packet buffer, or -1 if invalid. */
    fun headerLength(packet: ByteArray, length: Int): Int {
        if (length < 20) return -1
        val versionIhl = packet[0].toInt() and 0xFF
        val version = (versionIhl ushr 4) and 0xF
        if (version != 4) return -1
        val ihlWords = (versionIhl and 0xF)
        val ihl = ihlWords * 4
        if (ihl < 20 || ihl > length) return -1
        return ihl
    }

    /** Extracts total length field from IPv4 header, or -1 if invalid. */
    fun totalLength(packet: ByteArray, length: Int): Int {
        if (length < 4) return -1
        val tl = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        return if (tl in 20..length) tl else -1
    }

    /** Returns IPv4 destination as Int (network byte order), or null if invalid. */
    fun dstAsInt(packet: ByteArray, length: Int): Int? {
        val ihl = headerLength(packet, length)
        if (ihl < 20) return null
        val off = 16
        return ((packet[off + 0].toInt() and 0xFF) shl 24) or
            ((packet[off + 1].toInt() and 0xFF) shl 16) or
            ((packet[off + 2].toInt() and 0xFF) shl 8) or
            (packet[off + 3].toInt() and 0xFF)
    }

    fun srcAsInt(packet: ByteArray, length: Int): Int? {
        val ihl = headerLength(packet, length)
        if (ihl < 20) return null
        val off = 12
        return ((packet[off + 0].toInt() and 0xFF) shl 24) or
            ((packet[off + 1].toInt() and 0xFF) shl 16) or
            ((packet[off + 2].toInt() and 0xFF) shl 8) or
            (packet[off + 3].toInt() and 0xFF)
    }

    fun intToInet4(ip: Int): Inet4Address =
        InetAddress.getByAddress(
            byteArrayOf(
                ((ip ushr 24) and 0xFF).toByte(),
                ((ip ushr 16) and 0xFF).toByte(),
                ((ip ushr 8) and 0xFF).toByte(),
                (ip and 0xFF).toByte(),
            ),
        ) as Inet4Address

    fun inet4ToInt(address: Inet4Address): Int {
        val b = address.address
        return ((b[0].toInt() and 0xFF) shl 24) or
            ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or
            (b[3].toInt() and 0xFF)
    }
}
