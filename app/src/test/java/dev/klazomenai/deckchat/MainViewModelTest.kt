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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var audioFile: File
    private val viewModels = mutableListOf<MainViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        audioFile = File.createTempFile("test", ".pcm")
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.releaseResources() }
        viewModels.clear()
        Dispatchers.resetMain()
        audioFile.delete()
    }

    private fun createViewModel(
        sttResult: String = "",
        matrixClient: MatrixClient? = null,
        roomId: String? = null,
    ): MainViewModel {
        return MainViewModel(
            sttEngine = MockSttEngine(returnText = sttResult),
            ttsEngine = MockTtsEngine(),
            matrixClient = matrixClient,
            roomId = roomId,
            audioFileProvider = { audioFile },
            ioDispatcher = testDispatcher,
        ).also { viewModels.add(it) }
    }

    @Test
    fun `initial state is idle`() {
        val viewModel = createViewModel()
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `toggleRecording from idle returns ACTION_START`() {
        val viewModel = createViewModel()
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_START, action)
    }

    @Test
    fun `toggleRecording from idle transitions to recording`() {
        val viewModel = createViewModel()
        viewModel.toggleRecording()
        assertEquals(PipelineState.Recording, viewModel.state.value)
    }

    @Test
    fun `toggleRecording from recording returns ACTION_STOP`() {
        val viewModel = createViewModel()
        viewModel.toggleRecording() // → Recording
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_STOP, action)
    }

    @Test
    fun `toggleRecording from processing returns null`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Processing("Transcribing"))
        val action = viewModel.toggleRecording()
        assertNull(action)
    }

    @Test
    fun `toggleRecording from speaking returns null`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Speaking("Maren"))
        val action = viewModel.toggleRecording()
        assertNull(action)
    }

    @Test
    fun `toggleRecording from error returns ACTION_START`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.MicBusy))
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_START, action)
    }

    @Test
    fun `resetToIdle sets state to idle`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.MicBusy))
        viewModel.resetToIdle()
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `setState updates state directly`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Transcribed("hello"))
        assertTrue(viewModel.state.value is PipelineState.Transcribed)
        assertEquals("hello", (viewModel.state.value as PipelineState.Transcribed).text)
    }

    @Test
    fun `service event RecordingStarted transitions to recording`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStarted)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PipelineState.Recording, viewModel.state.value)
    }

    @Test
    fun `service event RecordingStopped runs pipeline and returns to idle on blank STT`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        // Mock STT returns empty string by default, so pipeline returns to Idle
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `service event Error transitions to error state`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.Error(PipelineError.PermissionDenied))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        assertEquals(PipelineError.PermissionDenied, (state as PipelineState.Error).error)
    }

    @Test
    fun `setState with PermissionDenied sets error state`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.PermissionDenied))
        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        assertEquals(PipelineError.PermissionDenied, (state as PipelineState.Error).error)
    }

    @Test
    fun `setState with PermissionPermanentlyDenied sets error state`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.PermissionPermanentlyDenied))
        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        assertEquals(PipelineError.PermissionPermanentlyDenied, (state as PipelineState.Error).error)
    }

    @Test
    fun `resetToIdle after permission denial returns to idle`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.PermissionPermanentlyDenied))
        viewModel.resetToIdle()
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `requestStop from recording returns ACTION_STOP`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Recording)
        val action = viewModel.requestStop()
        assertEquals(RecordingService.ACTION_STOP, action)
    }

    @Test
    fun `requestStop from idle returns null without state change`() {
        val viewModel = createViewModel()
        val action = viewModel.requestStop()
        assertNull(action)
        assertEquals(PipelineState.Idle, viewModel.state.value)
    }

    @Test
    fun `requestStop from error returns null without state change`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.PermissionDenied))
        val action = viewModel.requestStop()
        assertNull(action)
        assertTrue(viewModel.state.value is PipelineState.Error)
    }

    @Test
    fun `toggleRecording from permission error returns ACTION_START`() {
        val viewModel = createViewModel()
        viewModel.setState(PipelineState.Error(PipelineError.PermissionDenied))
        val action = viewModel.toggleRecording()
        assertEquals(RecordingService.ACTION_START, action)
        assertEquals(PipelineState.Recording, viewModel.state.value)
    }

    // --- Recording duration ---

    @Test
    fun `recording progress updates duration flow`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingProgress(1500L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1500L, viewModel.recordingDurationMs.value)
    }

    @Test
    fun `recording started resets duration to zero`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingProgress(3000L))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3000L, viewModel.recordingDurationMs.value)

        RecordingService.emitEvent(ServiceEvent.RecordingStarted)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0L, viewModel.recordingDurationMs.value)
    }

    @Test
    fun `recording stopped resets duration to zero`() = runTest {
        val viewModel = createViewModel(sttResult = "")
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingProgress(2000L))
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0L, viewModel.recordingDurationMs.value)
    }

    @Test
    fun `recording progress does not change pipeline state`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setState(PipelineState.Recording)
        RecordingService.emitEvent(ServiceEvent.RecordingProgress(500L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PipelineState.Recording, viewModel.state.value)
    }
}
