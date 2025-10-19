import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStream
import tunneling.readFully

class StreamUtilsTest {

    private class ChunkedInput(private val data: ByteArray, private val chunkSize: Int) : InputStream() {
        private var pos = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val toRead = minOf(chunkSize, len, data.size - pos)
            System.arraycopy(data, pos, b, off, toRead)
            pos += toRead
            return toRead
        }

        override fun read(): Int = throw UnsupportedOperationException()
    }

    private class BlockingInput : InputStream() {
        // never returns data or EOF
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            Thread.sleep(10000)
            return -1
        }

        override fun read(): Int = throw UnsupportedOperationException()
    }

    @Test
    fun `readFully reads across partial chunks`() = runBlocking {
        val data = ByteArray(50) { it.toByte() }
        val input = ChunkedInput(data, chunkSize = 7)
        val buf = ByteArray(50)
        val read = readFully(input, buf)
        assertEquals(50, read)
        assertArrayEquals(data, buf)
    }

    @Test
    fun `readFully returns less on EOF`() = runBlocking {
        val data = ByteArray(10) { it.toByte() }
        val input = ChunkedInput(data, chunkSize = 5)
        val buf = ByteArray(20)
        val read = readFully(input, buf)
        assertEquals(10, read)
        assertArrayEquals(data + ByteArray(10), buf)
    }

    @Test
    fun `readFully times out when provided`() {
        val input = BlockingInput()
        val buf = ByteArray(5)
        val start = System.currentTimeMillis()
        val thrown = try {
            runBlocking {
                readFully(input, buf, timeoutMillis = 100, logPrefix = "TEST-TIMEOUT")
            }
            null
        } catch (ex: Exception) {
            ex
        }
        val elapsed = System.currentTimeMillis() - start
        assertNotNull(thrown)
        // ensure it timed out roughly within a small window (not strict)
        assertTrue(elapsed < 5000)
    }
}
