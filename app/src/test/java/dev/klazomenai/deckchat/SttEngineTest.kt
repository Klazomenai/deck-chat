package dev.klazomenai.deckchat

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Mock that satisfies [SttEngine] without loading any JNI library.
 * Used by all JVM unit tests that exercise code paths involving STT.
 */
class MockSttEngine(var returnText: String = "mock transcription") : SttEngine {
    var lastFile: File? = null
    var closeCount = 0

    override suspend fun transcribe(audioFile: File): String {
        lastFile = audioFile
        return returnText
    }

    override fun close() {
        closeCount++
    }
}

class SttEngineTest {

    @Test
    fun `mock transcribe returns configured text`() = runTest {
        val engine = MockSttEngine()
        val file = File.createTempFile("audio", ".pcm")
        try {
            val result = engine.transcribe(file)
            assertEquals("mock transcription", result)
            assertEquals(file, engine.lastFile)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `mock transcribe returns empty string when configured`() = runTest {
        val engine = MockSttEngine(returnText = "")
        val file = File.createTempFile("audio", ".pcm")
        try {
            assertEquals("", engine.transcribe(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `mock transcribe records last file`() = runTest {
        val engine = MockSttEngine()
        val fileA = File.createTempFile("audioA", ".pcm")
        val fileB = File.createTempFile("audioB", ".pcm")
        try {
            engine.transcribe(fileA)
            assertEquals(fileA, engine.lastFile)
            engine.transcribe(fileB)
            assertEquals(fileB, engine.lastFile)
        } finally {
            fileA.delete()
            fileB.delete()
        }
    }

    @Test
    fun `mock close increments counter`() {
        val engine = MockSttEngine()
        assertEquals(0, engine.closeCount)
        engine.close()
        assertEquals(1, engine.closeCount)
        engine.close()
        assertEquals(2, engine.closeCount)
    }
}
