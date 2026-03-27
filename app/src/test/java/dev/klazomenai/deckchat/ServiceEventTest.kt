package dev.klazomenai.deckchat

import org.junit.Assert.assertEquals
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
}
