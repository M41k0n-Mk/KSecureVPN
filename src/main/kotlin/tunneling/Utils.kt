package tunneling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ResponseCode {
    const val AUTH_FAILED: Byte = 0
    const val AUTH_SUCCESS: Byte = 1
    const val AUTH_MALFORMED: Byte = 2
    const val MSG_RECEIVED: Byte = 3
}

/**
 * Read exactly buffer.size bytes into [buffer], looping until filled, EOF, or timeout.
 * Returns number of bytes actually read (may be less on EOF).
 * If [timeoutMillis] is provided, the overall operation will be cancelled after that many millis.
 */
suspend fun readFully(
    input: InputStream,
    buffer: ByteArray,
    timeoutMillis: Long? = null,
    logPrefix: String = "",
): Int {
    val startTime = System.currentTimeMillis()
    if (logPrefix.isNotEmpty()) {
        println(
            "[$logPrefix] Starting read of ${buffer.size} bytes${if (timeoutMillis != null) " with ${timeoutMillis}ms timeout" else ""}",
        )
    }

    val bytesRead =
        if (timeoutMillis != null) {
            // Run blocking IO in a dedicated thread and wait with a timeout so we can cancel the blocking read
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future =
                    executor.submit<Int> {
                        readFullyBlockingSync(input, buffer)
                    }
                try {
                    future.get(timeoutMillis, TimeUnit.MILLISECONDS)
                } catch (te: TimeoutException) {
                    future.cancel(true)
                    if (logPrefix.isNotEmpty()) {
                        println("[$logPrefix] Read timed out after ${timeoutMillis}ms")
                    }
                    throw te
                }
            } finally {
                executor.shutdownNow()
            }
        } else {
            readFullyBlocking(input, buffer)
        }

    val elapsed = System.currentTimeMillis() - startTime
    if (logPrefix.isNotEmpty()) {
        println("[$logPrefix] Read $bytesRead/${buffer.size} bytes in ${elapsed}ms")
    }

    return bytesRead
}

private suspend fun readFullyBlocking(
    input: InputStream,
    buffer: ByteArray,
): Int =
    withContext(Dispatchers.IO) {
        readFullyBlockingSync(input, buffer)
    }

private fun readFullyBlockingSync(
    input: InputStream,
    buffer: ByteArray,
): Int {
    var offset = 0
    while (offset < buffer.size) {
        val read = input.read(buffer, offset, buffer.size - offset)
        if (read <= 0) return offset
        offset += read
    }
    return offset
}