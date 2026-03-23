package dev.klazomenai.deckchat

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
    suspend fun stop()
    fun isLoggedIn(): Boolean
}

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
