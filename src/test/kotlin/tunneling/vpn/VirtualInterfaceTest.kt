package tunneling.vpn

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tunneling.vpn.stub.MemoryTun

class VirtualInterfaceTest {

    // @Test
    // fun `MemoryTun should implement VirtualInterface`() {
    //     val tun = MemoryTun("test-tun")
    //
    //     assertEquals("test-tun", tun.name)
    //     assertEquals(1500, tun.mtu)
    //
    //     // Test basic operations
    //     val buffer = ByteArray(100)
    //     val read = tun.readPacket(buffer)
    //     assertTrue(read >= 0) // Should not block indefinitely
    //
    //     tun.writePacket("test data".toByteArray(), 9)
    //     // MemoryTun doesn't store data in this simple implementation
    // }
}