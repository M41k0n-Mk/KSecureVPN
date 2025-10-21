package config

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * IP allowlist supporting IPv4 and IPv6 CIDR notation and single addresses.
 * Idiomatic Kotlin implementation with clean code principles.
 */
object IpAllowlist {
    
    /**
     * Check if a remote IP matches any allowed CIDR/IP entries.
     * Supports IPv4 (192.168.1.0/24), IPv6 (::1/128), and single addresses.
     */
    fun isAllowed(remoteIp: String, allowedCidrs: List<String>): Boolean {
        if (allowedCidrs.isEmpty()) return true
        
        val addr = remoteIp.parseInetAddress() ?: return false
        
        return allowedCidrs
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .any { entry -> addr.matchesEntry(entry) }
    }
    
    private fun String.parseInetAddress(): InetAddress? = 
        runCatching { InetAddress.getByName(this) }.getOrNull()
    
    private fun InetAddress.matchesEntry(entry: String): Boolean = 
        if ('/' in entry) matchesCidr(entry) else matchesSingleIp(entry)
    
    private fun InetAddress.matchesSingleIp(entry: String): Boolean {
        val entryAddr = entry.parseInetAddress() ?: return false
        return hostAddress == entryAddr.hostAddress
    }
    
    private fun InetAddress.matchesCidr(cidr: String): Boolean {
        val (networkStr, prefixStr) = cidr.split('/', limit = 2)
            .takeIf { it.size == 2 } ?: return false
        
        val network = networkStr.parseInetAddress() ?: return false
        val prefix = prefixStr.toIntOrNull() ?: return false
        
        return when {
            this is Inet4Address && network is Inet4Address -> matchesIpv4Cidr(network, prefix)
            this is Inet6Address && network is Inet6Address -> matchesIpv6Cidr(network, prefix)
            else -> false
        }
    }
    
    private fun Inet4Address.matchesIpv4Cidr(network: Inet4Address, prefix: Int): Boolean {
        if (prefix !in 0..32) return false
        if (prefix == 0) return true
        
        val thisBits = address.toInt()
        val networkBits = network.address.toInt()
        val mask = (-1 shl (32 - prefix))
        
        return (thisBits and mask) == (networkBits and mask)
    }
    
    private fun Inet6Address.matchesIpv6Cidr(network: Inet6Address, prefix: Int): Boolean {
        if (prefix !in 0..128) return false
        if (prefix == 0) return true
        
        val thisBytes = address
        val networkBytes = network.address
        
        val fullBytes = prefix / 8
        val remainderBits = prefix % 8
        
        // Compare full bytes
        if (!thisBytes.sliceArray(0 until fullBytes).contentEquals(networkBytes.sliceArray(0 until fullBytes))) {
            return false
        }
        
        // Compare remaining bits if any
        if (remainderBits > 0 && fullBytes < 16) {
            val mask = (0xFF shl (8 - remainderBits)) and 0xFF
            val thisByte = thisBytes[fullBytes].toInt() and 0xFF
            val networkByte = networkBytes[fullBytes].toInt() and 0xFF
            return (thisByte and mask) == (networkByte and mask)
        }
        
        return true
    }
    
    private fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).int
}
