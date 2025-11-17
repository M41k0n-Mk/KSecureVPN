package tunneling.vpn

import crypt.AESCipher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class PacketFramerTest {
    @Test
    fun `send and read a frame roundtrip`() =
        runBlocking {
            val key = AESCipher.generateKey()
            val out = PipedOutputStream()
            val input = PipedInputStream(out, 4096)

            val payload =
                "hello-packet".toByteArray()
            PacketFramer.sendFrame(out, key, FrameType.PACKET, payload)

            val frame = PacketFramer.readFrame(input, key)
            requireNotNull(frame)
            assertEquals(FrameType.PACKET, frame.first)
            assertArrayEquals(payload, frame.second)
        }
}
