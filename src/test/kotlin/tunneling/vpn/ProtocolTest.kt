package tunneling.vpn

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProtocolTest {
    @Test
    fun `FrameType should have correct values`() {
        assertEquals(0x10.toByte(), FrameType.CONTROL)
        assertEquals(0x11.toByte(), FrameType.PACKET)
    }

    @Test
    fun `ControlKind should have correct values`() {
        assertEquals(0x01.toByte(), ControlKind.IP_REQUEST)
        assertEquals(0x02.toByte(), ControlKind.IP_ASSIGN)
        assertEquals(0x03.toByte(), ControlKind.KEEPALIVE)
    }
}
