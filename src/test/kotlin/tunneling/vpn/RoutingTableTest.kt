package tunneling.vpn

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class RoutingTableTest {
    @Test
    fun `add lookup remove`() {
        val rt = RoutingTable()
        val ip = (10 shl 24) or (8 shl 16) or (0 shl 8) or 2
        var called = false
        rt.add(ip) { _, _ -> called = true }
        val e = rt.lookup(ip)
        assertNotNull(e)
        e!!.sendPacket(ByteArray(1), 1)
        assert(called)
        rt.remove(ip)
        assertNull(rt.lookup(ip))
    }
}
