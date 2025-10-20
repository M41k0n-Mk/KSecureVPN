import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import session.SessionTracker

class SessionTrackerTest {
    @Test
    fun `createSession generates unique session ID`() {
        val remoteAddress = "192.168.1.100"
        val sessionId = SessionTracker.createSession(remoteAddress)

        assertNotNull(sessionId)
        assertTrue(sessionId.startsWith("sess-"))
        assertTrue(sessionId.length > 10)
    }

    @Test
    fun `createSession stores session info`() {
        val remoteAddress = "192.168.1.100"
        val sessionId = SessionTracker.createSession(remoteAddress)

        val sessionInfo = SessionTracker.getSession(sessionId)
        assertNotNull(sessionInfo)
        assertEquals(sessionId, sessionInfo?.sessionId)
        assertEquals(remoteAddress, sessionInfo?.remoteAddress)
        assertTrue(sessionInfo?.createdAt ?: 0 > 0)
    }

    @Test
    fun `endSession removes session from tracking`() {
        val remoteAddress = "192.168.1.100"
        val sessionId = SessionTracker.createSession(remoteAddress)

        assertNotNull(SessionTracker.getSession(sessionId))

        SessionTracker.endSession(sessionId)

        assertNull(SessionTracker.getSession(sessionId))
    }

    @Test
    fun `getActiveSessions returns all active sessions`() {
        // Clear any existing sessions by creating and ending a dummy session
        val initialSessions = SessionTracker.getActiveSessions()

        val session1 = SessionTracker.createSession("192.168.1.100")
        val session2 = SessionTracker.createSession("192.168.1.101")

        val activeSessions = SessionTracker.getActiveSessions()
        assertTrue(activeSessions.size >= 2)
        assertTrue(activeSessions.any { it.sessionId == session1 })
        assertTrue(activeSessions.any { it.sessionId == session2 })

        // Clean up
        SessionTracker.endSession(session1)
        SessionTracker.endSession(session2)
    }

    @Test
    fun `multiple sessions have different IDs`() {
        val sessionIds = mutableSetOf<String>()

        repeat(10) {
            val sessionId = SessionTracker.createSession("192.168.1.$it")
            sessionIds.add(sessionId)
        }

        // All session IDs should be unique
        assertEquals(10, sessionIds.size)

        // Clean up
        sessionIds.forEach { SessionTracker.endSession(it) }
    }
}
