import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class NoKeyPrintTest {

    @Test
    fun `generated key not printed to stdout`() {
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        try {
            // call loadKeyFrom with null to trigger generation path
            loadKeyFrom(null)
        } finally {
            System.out.flush()
            System.setOut(originalOut)
        }

        val out = baos.toString(Charsets.UTF_8.name())

        // A 32-byte key encoded in base64 is 44 chars (with padding). Ensure no long base64-like string is printed.
        val base64Regex = Regex("[A-Za-z0-9+/]{40,}={0,2}")
        assertFalse(base64Regex.containsMatchIn(out), "Stdout must not contain base64 key material. Output was: \n$out")
    }
}
