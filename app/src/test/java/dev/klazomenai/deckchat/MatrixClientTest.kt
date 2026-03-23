package dev.klazomenai.deckchat

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mock that satisfies [MatrixClient] without loading the matrix-rust-sdk JNI.
 * Used by JVM tests that exercise code paths involving Matrix messaging.
 */
class MockMatrixClient : MatrixClient {
    data class SentMessage(val roomId: String, val text: String)

    val sentMessages = mutableListOf<SentMessage>()
    var loggedIn = false
    var onMessageCallback: ((CrewMessage) -> Unit)? = null
    var loginCount = 0
    var stopCount = 0

    override suspend fun login(homeserverUrl: String, username: String, password: String) {
        loggedIn = true
        loginCount++
    }

    override suspend fun restoreSession() {
        loggedIn = true
    }

    override suspend fun sendMessage(roomId: String, text: String) {
        sentMessages.add(SentMessage(roomId, text))
    }

    override fun startSync(onMessage: (CrewMessage) -> Unit) {
        onMessageCallback = onMessage
    }

    override suspend fun stop() {
        stopCount++
    }

    override fun isLoggedIn(): Boolean = loggedIn

    /** Simulate receiving a message (for testing downstream handlers) */
    fun simulateMessage(crewMessage: CrewMessage) {
        onMessageCallback?.invoke(crewMessage)
    }
}

class MatrixClientTest {

    // --- Body-prefix parser tests ---

    @Test
    fun `parseCrewMessage extracts crew name, verbosity, and body`() {
        val result = parseCrewMessage("[maren:dispatch] Here is your status report.", "@bot:example.com")
        assertEquals("maren", result?.crewName)
        assertEquals("dispatch", result?.verbosity)
        assertEquals("Here is your status report.", result?.body)
        assertEquals("@bot:example.com", result?.sender)
    }

    @Test
    fun `parseCrewMessage handles different crew names and verbosities`() {
        val result = parseCrewMessage("[crest:signal] Signal received.", "@bot:example.com")
        assertEquals("crest", result?.crewName)
        assertEquals("signal", result?.verbosity)
        assertEquals("Signal received.", result?.body)
    }

    @Test
    fun `parseCrewMessage returns null for plain text`() {
        val result = parseCrewMessage("Just a normal message", "@user:example.com")
        assertNull(result)
    }

    @Test
    fun `parseCrewMessage returns null for empty string`() {
        val result = parseCrewMessage("", "@user:example.com")
        assertNull(result)
    }

    @Test
    fun `parseCrewMessage returns null for malformed prefix`() {
        assertNull(parseCrewMessage("[maren] no verbosity", "@bot:example.com"))
        assertNull(parseCrewMessage("maren:dispatch no brackets", "@bot:example.com"))
        assertNull(parseCrewMessage("[:dispatch] no name", "@bot:example.com"))
    }

    @Test
    fun `parseCrewMessage handles multiline body`() {
        val body = "[maren:dispatch] Line one\nLine two\nLine three"
        val result = parseCrewMessage(body, "@bot:example.com")
        assertEquals("maren", result?.crewName)
        assertEquals("Line one\nLine two\nLine three", result?.body)
    }

    // --- Mock client tests ---

    @Test
    fun `mock login sets loggedIn`() = runTest {
        val client = MockMatrixClient()
        assertFalse(client.isLoggedIn())
        client.login("https://matrix.example.com", "user", "pass")
        assertTrue(client.isLoggedIn())
    }

    @Test
    fun `mock sendMessage records messages`() = runTest {
        val client = MockMatrixClient()
        client.sendMessage("!room:example.com", "hello")
        assertEquals(1, client.sentMessages.size)
        assertEquals("!room:example.com", client.sentMessages[0].roomId)
        assertEquals("hello", client.sentMessages[0].text)
    }

    @Test
    fun `mock simulateMessage invokes callback`() {
        val client = MockMatrixClient()
        val received = mutableListOf<CrewMessage>()
        client.startSync { received.add(it) }

        client.simulateMessage(CrewMessage("maren", "dispatch", "test response", "@bot:example.com"))

        assertEquals(1, received.size)
        assertEquals("maren", received[0].crewName)
    }

    @Test
    fun `mock stop increments counter but preserves login`() = runTest {
        val client = MockMatrixClient()
        client.login("https://matrix.example.com", "user", "pass")
        assertTrue(client.isLoggedIn())
        client.stop()
        assertTrue(client.isLoggedIn()) // stop() stops syncing, not logout
        assertEquals(1, client.stopCount)
    }
}
