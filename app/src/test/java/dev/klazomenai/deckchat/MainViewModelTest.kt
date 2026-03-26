package dev.klazomenai.deckchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        val viewModel = MainViewModel()
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `toggleRecording from idle returns ACTION_START`() {
        val viewModel = MainViewModel()
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_START, action)
    }

    @Test
    fun `toggleRecording from idle transitions to recording`() {
        val viewModel = MainViewModel()
        viewModel.toggleRecording()
        assertEquals(PipelineState.Recording, viewModel.state.value)
    }

    @Test
    fun `toggleRecording from recording returns ACTION_STOP`() {
        val viewModel = MainViewModel()
        viewModel.toggleRecording() // → Recording
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_STOP, action)
    }

    @Test
    fun `toggleRecording from processing returns null`() {
        val viewModel = MainViewModel()
        viewModel.setState(PipelineState.Processing("Transcribing"))
        val action = viewModel.toggleRecording()
        assertNull(action)
    }

    @Test
    fun `toggleRecording from speaking returns null`() {
        val viewModel = MainViewModel()
        viewModel.setState(PipelineState.Speaking("Maren"))
        val action = viewModel.toggleRecording()
        assertNull(action)
    }

    @Test
    fun `toggleRecording from error returns ACTION_START`() {
        val viewModel = MainViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.MicBusy))
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_START, action)
    }

    @Test
    fun `resetToIdle sets state to idle`() {
        val viewModel = MainViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.MicBusy))
        viewModel.resetToIdle()
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `setState updates state directly`() {
        val viewModel = MainViewModel()
        viewModel.setState(PipelineState.Transcribed("hello"))
        assertTrue(viewModel.state.value is PipelineState.Transcribed)
        assertEquals("hello", (viewModel.state.value as PipelineState.Transcribed).text)
    }

    @Test
    fun `service event RecordingStarted transitions to recording`() = runTest {
        val viewModel = MainViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStarted)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PipelineState.Recording, viewModel.state.value)
    }

    @Test
    fun `service event RecordingStopped transitions to processing`() = runTest {
        val viewModel = MainViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is PipelineState.Processing)
    }

    @Test
    fun `service event Error transitions to error state`() = runTest {
        val viewModel = MainViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.Error(PipelineError.PermissionDenied))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        assertEquals(PipelineError.PermissionDenied, (state as PipelineState.Error).error)
    }
}
