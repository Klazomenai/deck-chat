package dev.klazomenai.deckchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineStateTest {

    @Test
    fun `idle is singleton`() {
        assertTrue(PipelineState.Idle === PipelineState.Idle)
    }

    @Test
    fun `recording is singleton`() {
        assertTrue(PipelineState.Recording === PipelineState.Recording)
    }

    @Test
    fun `processing carries stage name`() {
        val state = PipelineState.Processing("Transcribing")
        assertEquals("Transcribing", state.stage)
    }

    @Test
    fun `transcribed carries text`() {
        val state = PipelineState.Transcribed("hello world")
        assertEquals("hello world", state.text)
    }

    @Test
    fun `speaking carries crew name`() {
        val state = PipelineState.Speaking("Maren")
        assertEquals("Maren", state.crewName)
    }

    @Test
    fun `error wraps pipeline error`() {
        val error = PipelineError.MicBusy
        val state = PipelineState.Error(error)
        assertEquals(PipelineError.MicBusy, state.error)
    }

    @Test
    fun `all error types are distinct`() {
        val errors: List<PipelineError> = listOf(
            PipelineError.PermissionDenied,
            PipelineError.PermissionPermanentlyDenied,
            PipelineError.MicBusy,
            PipelineError.AudioInitFailed,
            PipelineError.ModelMissing,
            PipelineError.BluetoothLost,
            PipelineError.SttFailed("stt error"),
            PipelineError.TtsFailed("tts error"),
            PipelineError.MatrixFailed("matrix error"),
        )
        // All unique
        assertEquals(errors.size, errors.toSet().size)
    }

    @Test
    fun `stt failed carries message`() {
        val error = PipelineError.SttFailed("model load failed")
        assertEquals("model load failed", error.message)
    }

    @Test
    fun `tts failed carries message`() {
        val error = PipelineError.TtsFailed("voice not found")
        assertEquals("voice not found", error.message)
    }

    @Test
    fun `matrix failed carries message`() {
        val error = PipelineError.MatrixFailed("connection refused")
        assertEquals("connection refused", error.message)
    }

    @Test
    fun `processing equality based on stage`() {
        assertEquals(
            PipelineState.Processing("Transcribing"),
            PipelineState.Processing("Transcribing"),
        )
        assertNotEquals(
            PipelineState.Processing("Transcribing"),
            PipelineState.Processing("Sending"),
        )
    }

    @Test
    fun `when expression is exhaustive over all states`() {
        val states: List<PipelineState> = listOf(
            PipelineState.Idle,
            PipelineState.Recording,
            PipelineState.Processing("test"),
            PipelineState.Transcribed("text"),
            PipelineState.Speaking("Maren"),
            PipelineState.Error(PipelineError.MicBusy),
        )
        for (state in states) {
            val label = when (state) {
                is PipelineState.Idle -> "idle"
                is PipelineState.Recording -> "recording"
                is PipelineState.Processing -> "processing"
                is PipelineState.Transcribed -> "transcribed"
                is PipelineState.Speaking -> "speaking"
                is PipelineState.Error -> "error"
            }
            assertTrue(label.isNotEmpty())
        }
    }
}
