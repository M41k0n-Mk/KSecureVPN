package session

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates and tracks unique session IDs for VPN connections.
 * Each client connection gets a unique session ID for tracking and logging purposes.
 */
object SessionTracker {
    private val sessions = ConcurrentHashMap<String, SessionInfo>()

    /**
     * Generate a new session ID and register it.
     * Returns a unique session identifier.
     */
    fun createSession(remoteAddress: String): String {
        val sessionId = generateSessionId()
        val sessionInfo =
            SessionInfo(
                sessionId = sessionId,
                remoteAddress = remoteAddress,
                createdAt = System.currentTimeMillis(),
            )
        sessions[sessionId] = sessionInfo
        return sessionId
    }

    /**
     * Remove session from tracking when connection ends.
     */
    fun endSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * Get session information if it exists.
     */
    fun getSession(sessionId: String): SessionInfo? = sessions[sessionId]

    /**
     * Get all active sessions (for monitoring/debugging).
     */
    fun getActiveSessions(): List<SessionInfo> = sessions.values.toList()

    private fun generateSessionId(): String {
        // Generate a compact but unique session ID
        // Format: timestamp-uuid (first 8 chars of UUID for brevity)
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        return "sess-$timestamp-$uuid"
    }
}

/**
 * Information about an active VPN session.
 */
data class SessionInfo(
    val sessionId: String,
    val remoteAddress: String,
    val createdAt: Long,
)
