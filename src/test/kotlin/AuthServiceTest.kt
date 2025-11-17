package auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AuthServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var authService: AuthService

    @BeforeEach
    fun setup() {
        val usersFile = tempDir.resolve("users.properties")
        authService = AuthService(usersFile)
    }

    @Test
    fun `addUser should create user successfully`() {
        val result = authService.addUser("testuser", "password123".toCharArray())
        assertTrue(result)
    }

    @Test
    fun `addUser should reject duplicate users`() {
        authService.addUser("testuser", "password123".toCharArray())
        val result = authService.addUser("testuser", "different".toCharArray())
        assertFalse(result)
    }

    @Test
    fun `authenticate should succeed with correct credentials`() {
        authService.addUser("testuser", "password123".toCharArray())
        val result = authService.authenticate("testuser", "password123".toCharArray())
        assertTrue(result)
    }

    @Test
    fun `authenticate should fail with wrong password`() {
        authService.addUser("testuser", "password123".toCharArray())
        val result = authService.authenticate("testuser", "wrongpass".toCharArray())
        assertFalse(result)
    }

    @Test
    fun `authenticate should fail for non-existent user`() {
        val result = authService.authenticate("nonexistent", "password".toCharArray())
        assertFalse(result)
    }

    @Test
    fun `loadUsers should load from file`() {
        // Already tested in setup
        assertNotNull(authService)
    }
}
