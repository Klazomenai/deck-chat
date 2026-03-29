package dev.klazomenai.deckchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceEventTest {

    @Test
    fun `recording started is singleton`() {
        assertTrue(ServiceEvent.RecordingStarted === ServiceEvent.RecordingStarted)
    }

    @Test
    fun `recording stopped is singleton`() {
        assertTrue(ServiceEvent.RecordingStopped === ServiceEvent.RecordingStopped)
    }

    @Test
    fun `error carries pipeline error`() {
        val event = ServiceEvent.Error(PipelineError.MicBusy)
        assertEquals(PipelineError.MicBusy, event.error)
    }

    @Test
    fun `error equality based on wrapped error`() {
        assertEquals(
            ServiceEvent.Error(PipelineError.PermissionDenied),
            ServiceEvent.Error(PipelineError.PermissionDenied),
        )
    }

    @Test
    fun `recording progress carries duration`() {
        val event = ServiceEvent.RecordingProgress(1500L)
        assertEquals(1500L, event.durationMs)
    }

    @Test
    fun `recording progress equality based on duration`() {
        assertEquals(
            ServiceEvent.RecordingProgress(1000L),
            ServiceEvent.RecordingProgress(1000L),
        )
        assertNotEquals(
            ServiceEvent.RecordingProgress(1000L),
            ServiceEvent.RecordingProgress(2000L),
        )
    }
}
