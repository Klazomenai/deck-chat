package dev.klazomenai.deckchat

import kotlinx.coroutines.flow.StateFlow

/**
 * Matrix client abstraction for E2EE messaging.
 *
 * Sends transcribed text to a Matrix room and receives crew responses.
 * E2EE is handled transparently by the underlying SDK.
 *
 * The bridge bot sends responses with a body-prefix convention:
 * `[crewName:verbosity] response text`
 * because the SDK's typed API strips custom JSON event fields.
 * See issue #19 for M2 raw event access.
 */
interface MatrixClient {
    suspend fun login(homeserverUrl: String, username: String, password: String)
    suspend fun restoreSession()
    suspend fun sendMessage(roomId: String, text: String)
    fun startSync(onMessage: (CrewMessage) -> Unit)
    suspend fun listenToRoom(roomId: String)
    /**
     * Register a callback for sync status updates (e.g. "Waiting for room sync").
     *
     * The callback may be invoked from a background thread. Callers that need to
     * update UI are responsible for switching to the main thread.
     */
    fun setSyncStatusCallback(callback: ((String) -> Unit)?)
    /** Stops syncing. Does NOT clear the session or log out. */
    suspend fun stop()
    fun isLoggedIn(): Boolean

    /**
     * Recent undecryptable events, newest last. Bounded to the most recent entries.
     * Surfaced via the debug transcript (issue #167) for diagnosing key backup /
     * cross-signing issues without needing `adb logcat`.
     */
    val utdEvents: StateFlow<List<UtdEvent>>
}

/**
 * A single UTD (unable-to-decrypt) observation. `sender` is only populated when the
 * event surfaces via the room timeline — the SDK's global UTD delegate does not
 * expose it directly.
 */
data class UtdEvent(
    val eventId: String,
    val sender: String?,
    val cause: String,
    val timestampMs: Long,
)

/**
 * Parsed crew response from a Matrix message body prefix.
 * Format: `[crewName:verbosity] body text`
 */
data class CrewMessage(
    val crewName: String,
    val verbosity: String,
    val body: String,
    val sender: String,
)

/**
 * Parses body-prefix convention: `[crewName:verbosity] body text`
 * Returns null if the body doesn't match the expected format.
 */
fun parseCrewMessage(body: String, sender: String): CrewMessage? {
    val match = CREW_PREFIX_REGEX.matchEntire(body) ?: return null
    return CrewMessage(
        crewName = match.groupValues[1],
        verbosity = match.groupValues[2],
        body = match.groupValues[3],
        sender = sender,
    )
}

private val CREW_PREFIX_REGEX = Regex("""\[(\w+):(\w+)]\s*(.+)""", RegexOption.DOT_MATCHES_ALL)
