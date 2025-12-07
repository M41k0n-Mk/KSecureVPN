package tunneling.vpn

import crypt.AESCipher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
            assertNotNull(frame)
            assertEquals(FrameType.PACKET, frame!!.first)
            assertArrayEquals(payload, frame.second)
        }

    @Test
    fun `create and read frame from bytes roundtrip`() {
        val key = AESCipher.generateKey()
        val payload = "test-payload".toByteArray()

        val frameData = PacketFramer.createFrame(FrameType.PACKET, payload, key)
        val frame = PacketFramer.readFrameFromBytes(frameData, key)

        requireNotNull(frame)
        assertEquals(FrameType.PACKET, frame.first)
        assertArrayEquals(payload, frame.second)
    }

    @Test
    fun `create and read AUTH frame from bytes`() {
        val key = AESCipher.generateKey()
        val authPayload = "AUTH\ntest\npass".toByteArray()

        val frameData = PacketFramer.createFrame(FrameType.AUTH, authPayload, key)
        val frame = PacketFramer.readFrameFromBytes(frameData, key)

        assertNotNull(frame)
        assertEquals(FrameType.AUTH, frame!!.first)
        assertArrayEquals(authPayload, frame.second)
    }
}
