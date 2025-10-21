import config.IpAllowlist
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class IpAllowlistTest {
    
    @Test
    fun `IPv4 single IP matching`() {
        val allowed = listOf("127.0.0.1")
        
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("127.0.0.1", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("192.168.1.1", allowed)) }
        )
    }

    @Test
    fun `IPv4 CIDR matching`() {
        val allowed = listOf("192.168.1.0/24")
        
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("192.168.1.1", allowed)) },
            { assertTrue(IpAllowlist.isAllowed("192.168.1.255", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("192.168.2.1", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("192.168.0.255", allowed)) }
        )
    }
    
    @Test
    fun `IPv6 single IP matching`() {
        val allowed = listOf("::1", "2001:db8::1")
        
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("::1", allowed)) },
            { assertTrue(IpAllowlist.isAllowed("2001:db8::1", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("::2", allowed)) }
        )
    }
    
    @Test
    fun `IPv6 CIDR matching`() {
        val allowed = listOf("2001:db8::/32")
        
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("2001:db8::1", allowed)) },
            { assertTrue(IpAllowlist.isAllowed("2001:db8:1234::5678", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("2001:db9::1", allowed)) }
        )
    }

    @Test
    fun `empty allowlist allows all`() {
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("8.8.8.8", emptyList())) },
            { assertTrue(IpAllowlist.isAllowed("::1", emptyList())) }
        )
    }
    
    @Test
    fun `mixed IPv4 and IPv6 allowlist`() {
        val allowed = listOf("127.0.0.1", "192.168.1.0/24", "::1", "2001:db8::/32")
        
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("127.0.0.1", allowed)) },
            { assertTrue(IpAllowlist.isAllowed("192.168.1.100", allowed)) },
            { assertTrue(IpAllowlist.isAllowed("::1", allowed)) },
            { assertTrue(IpAllowlist.isAllowed("2001:db8::cafe", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("10.0.0.1", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("::2", allowed)) }
        )
    }
    
    @Test
    fun `malformed entries are ignored`() {
        val allowed = listOf("127.0.0.1", "invalid.ip", "192.168.1.0/999", "", "  ")
        
        assertAll(
            { assertTrue(IpAllowlist.isAllowed("127.0.0.1", allowed)) },
            { assertFalse(IpAllowlist.isAllowed("192.168.1.1", allowed)) }
        )
    }
}
