package tunneling.vpn.linux

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import logging.LogLevel
import logging.SecureLogger
import tunneling.vpn.VirtualInterface
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real TUN implementation for Linux using /dev/net/tun and ioctl(TUNSETIFF).
 * Supports IPv4 L3 packets (IFF_TUN) with NO_PI.
 */
class RealTun(
    requestedName: String = "tun0",
    override val mtu: Int = 1500,
) : VirtualInterface {
    override val name: String

    private val logger = SecureLogger.getInstance()
    private val closed = AtomicBoolean(false)

    // File descriptor for /dev/net/tun
    private val fd: Int

    init {
        ensureLinux()
        val libc = CLib.INSTANCE
        val devPath = "/dev/net/tun".toCString()
        val flags = O_RDWR
        val tmpFd = libc.open(devPath, flags)
        if (tmpFd < 0) {
            logger.logSessionEvent("RealTun", LogLevel.ERROR, "Falha ao abrir /dev/net/tun (ret=$tmpFd)")
            throw IOException("open(/dev/net/tun) failed: rc=$tmpFd")
        }

        // Prepare ifreq
        val ifreq = Memory(IFREQ_SIZE.toLong())
        ifreq.clear()
        // ifr_name
        val nameBytes = requestedName.toByteArray(StandardCharsets.US_ASCII)
        val nameLen = nameBytes.size.coerceAtMost(IFNAMSIZ - 1)
        ifreq.write(0, nameBytes, 0, nameLen)
        ifreq.setByte(nameLen.toLong(), 0)
        // ifr_flags (short) placed at offset IFNAMSIZ
        ifreq.setShort(IFNAMSIZ.toLong(), (IFF_TUN or IFF_NO_PI).toShort())

        val rc = libc.ioctl(tmpFd, TUNSETIFF, ifreq)
        if (rc < 0) {
            libc.close(tmpFd)
            logger.logSessionEvent("RealTun", LogLevel.ERROR, "ioctl(TUNSETIFF) falhou (rc=$rc)")
            throw IOException("ioctl(TUNSETIFF) failed: rc=$rc")
        }

        // The kernel may assign a specific name; read back from ifreq
        val actualNameBytes = ifreq.getByteArray(0, IFNAMSIZ)
        var sLen = 0
        while (sLen < actualNameBytes.size && actualNameBytes[sLen].toInt() != 0) sLen++
        name = String(actualNameBytes, 0, sLen, StandardCharsets.US_ASCII)
        fd = tmpFd
        logger.logSessionEvent("RealTun", LogLevel.INFO, "TUN criado: name=$name mtu=$mtu fd=$fd (IFF_TUN|IFF_NO_PI)")
    }

    override fun readPacket(buffer: ByteArray): Int {
        if (closed.get()) return -1
        val libc = CLib.INSTANCE
        val n = libc.read(fd, buffer, buffer.size)
        if (n < 0) {
            // Em alguns ambientes, EAGAIN/EINTR podem ocorrer; trate como 0
            return 0
        }
        return n
    }

    override fun writePacket(packet: ByteArray, length: Int) {
        if (closed.get()) return
        val libc = CLib.INSTANCE
        val toWrite = length.coerceAtMost(packet.size)
        val n = libc.write(fd, packet, toWrite)
        if (n < 0) {
            logger.logSessionEvent("RealTun", LogLevel.ERROR, "write() falhou (rc=$n)")
            throw IOException("write failed: rc=$n")
        }
        if (n != toWrite) {
            logger.logSessionEvent("RealTun", LogLevel.WARN, "write() parcial: esperado=$toWrite escrito=$n")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val libc = CLib.INSTANCE
        val rc = libc.close(fd)
        if (rc < 0) {
            logger.logSessionEvent("RealTun", LogLevel.WARN, "close(fd=$fd) falhou (rc=$rc)")
        } else {
            logger.logSessionEvent("RealTun", LogLevel.INFO, "TUN fechado: name=$name fd=$fd")
        }
    }

    private fun ensureLinux() {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (!os.contains("linux")) {
            throw UnsupportedOperationException("RealTun somente é suportado em Linux")
        }
    }

    private fun String.toCString(): ByteArray {
        val b = this.toByteArray(StandardCharsets.US_ASCII)
        return b + byteArrayOf(0)
    }

    private interface CLib : Library {
        fun open(path: ByteArray, flags: Int): Int
        fun ioctl(fd: Int, request: Int, argp: Pointer): Int
        fun read(fd: Int, buffer: ByteArray, count: Int): Int
        fun write(fd: Int, buffer: ByteArray, count: Int): Int
        fun close(fd: Int): Int

        companion object {
            val INSTANCE: CLib = Native.load("c", CLib::class.java)
        }
    }

    companion object {
        // ioctl numbers/constants
        private const val IFNAMSIZ: Int = 16
        private const val IFREQ_SIZE: Int = 40 // big enough for most libc layouts
        private const val IFF_TUN: Int = 0x0001
        private const val IFF_NO_PI: Int = 0x1000
        private const val TUNSETIFF: Int = 0x400454ca.toInt()

        // open() flags
        private const val O_RDONLY = 0x0000
        private const val O_WRONLY = 0x0001
        private const val O_RDWR = 0x0002

        // errno (não usado diretamente; mantido para referência de códigos comuns)
        private const val EAGAIN = 11
        private const val EINTR = 4
    }
}
