package dev.klazomenai.deckchat

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mock that satisfies [TtsEngine] without loading any JNI library.
 * Used by all JVM unit tests that exercise code paths involving TTS.
 */
class MockTtsEngine(
    var shouldThrow: Boolean = false,
    var throwMessage: String = "TTS failed",
) : TtsEngine {
    data class SpeakCall(val crewName: String, val text: String)

    val calls = mutableListOf<SpeakCall>()
    var closeCount = 0

    override suspend fun speak(crewName: String, text: String) {
        calls.add(SpeakCall(crewName, text))
        if (shouldThrow) throw RuntimeException(throwMessage)
    }

    override fun close() {
        closeCount++
    }
}

class TtsEngineTest {

    @Test
    fun `speak records crew name and text`() = runTest {
        val engine = MockTtsEngine()
        engine.speak("maren", "status report")
        assertEquals(1, engine.calls.size)
        assertEquals("maren", engine.calls[0].crewName)
        assertEquals("status report", engine.calls[0].text)
    }

    @Test
    fun `speak records multiple calls in order`() = runTest {
        val engine = MockTtsEngine()
        engine.speak("maren", "first message")
        engine.speak("crest", "second message")
        assertEquals(2, engine.calls.size)
        assertEquals("maren", engine.calls[0].crewName)
        assertEquals("crest", engine.calls[1].crewName)
    }

    @Test
    fun `speak with empty text records call`() = runTest {
        val engine = MockTtsEngine()
        engine.speak("maren", "")
        assertEquals(1, engine.calls.size)
        assertTrue(engine.calls[0].text.isEmpty())
    }

    @Test
    fun `close increments counter`() {
        val engine = MockTtsEngine()
        assertEquals(0, engine.closeCount)
        engine.close()
        assertEquals(1, engine.closeCount)
        engine.close()
        assertEquals(2, engine.closeCount)
    }
}
