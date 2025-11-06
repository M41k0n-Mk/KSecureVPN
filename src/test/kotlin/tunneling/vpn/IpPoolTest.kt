package tunneling.vpn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class IpPoolTest {
    @Test
    fun `allocates sequential addresses and releases`() {
        val pool = IpPool("10.8.0.0", 24, reserveGateway = true)
        val ip1 = pool.allocate()
        val ip2 = pool.allocate()
        assertNotNull(ip1)
        assertNotNull(ip2)
        assertEquals("10.8.0.2", ip1!!.hostAddress)
        assertEquals("10.8.0.3", ip2!!.hostAddress)
        pool.release(ip1)
        val ip3 = pool.allocate()
        assertEquals("10.8.0.2", ip3!!.hostAddress)
    }
}
