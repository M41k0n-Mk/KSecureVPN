package tunneling.vpn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IPv4Test {
    @Test
    fun `parse ipv4 header and dst`() {
        // Minimal IPv4 header for a dummy packet to 10.8.0.2 from 10.8.0.3
        val pkt = ByteArray(20)
        pkt[0] = 0x45 // version=4, ihl=5
        // total length 20 bytes
        pkt[2] = 0x00
        pkt[3] = 0x14
        // src 10.8.0.3
        pkt[12] = 10
        pkt[13] = 8
        pkt[14] = 0
        pkt[15] = 3
        // dst 10.8.0.2
        pkt[16] = 10
        pkt[17] = 8
        pkt[18] = 0
        pkt[19] = 2

        val ihl = IPv4.headerLength(pkt, pkt.size)
        assertEquals(20, ihl)
        val tl = IPv4.totalLength(pkt, pkt.size)
        assertEquals(20, tl)
        val dst = IPv4.dstAsInt(pkt, pkt.size)!!
        val src = IPv4.srcAsInt(pkt, pkt.size)!!
        assertEquals("10.8.0.2", IPv4.intToInet4(dst).hostAddress)
        assertEquals("10.8.0.3", IPv4.intToInet4(src).hostAddress)
    }
}
