package dev.klazomenai.deckchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for the end-to-end voice pipeline orchestration in [MainViewModel].
 * Covers: local echo, Matrix online mode, blank transcription, error paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sttEngine: MockSttEngine
    private lateinit var ttsEngine: MockTtsEngine
    private lateinit var matrixClient: MockMatrixClient
    private lateinit var audioFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sttEngine = MockSttEngine()
        ttsEngine = MockTtsEngine()
        matrixClient = MockMatrixClient()
        audioFile = File.createTempFile("test-audio", ".pcm")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        audioFile.delete()
    }

    private fun createViewModel(
        matrixClient: MockMatrixClient? = null,
        roomId: String? = null,
    ): MainViewModel {
        return MainViewModel(
            sttEngine = sttEngine,
            ttsEngine = ttsEngine,
            matrixClient = matrixClient,
            roomId = roomId,
            audioFileProvider = { audioFile },
        )
    }

    // --- Local Echo Mode ---

    @Test
    fun `local echo - STT result is spoken back with default crew`() = runTest {
        sttEngine.returnText = "ahoy there"
        val viewModel = createViewModel()
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        assertEquals(PipelineState.Idle, viewModel.state.value)
        assertEquals(1, sttEngine.transcribeCallCount)
        assertEquals(1, ttsEngine.calls.size)
        assertEquals("maren", ttsEngine.calls[0].crewName)
        assertEquals("ahoy there", ttsEngine.calls[0].text)
    }

    @Test
    fun `local echo - blank transcription returns to idle without TTS`() = runTest {
        sttEngine.returnText = "   "
        val viewModel = createViewModel()
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        assertEquals(PipelineState.Idle, viewModel.state.value)
        assertEquals(1, sttEngine.transcribeCallCount)
        assertEquals(0, ttsEngine.calls.size)
    }

    @Test
    fun `local echo - empty transcription returns to idle without TTS`() = runTest {
        sttEngine.returnText = ""
        val viewModel = createViewModel()
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        assertEquals(PipelineState.Idle, viewModel.state.value)
        assertEquals(0, ttsEngine.calls.size)
    }

    // --- Error Paths ---

    @Test
    fun `STT failure transitions to SttFailed error`() = runTest {
        sttEngine.shouldThrow = true
        sttEngine.throwMessage = "model not loaded"
        val viewModel = createViewModel()
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        val error = (state as PipelineState.Error).error
        assertTrue(error is PipelineError.SttFailed)
        assertEquals("model not loaded", (error as PipelineError.SttFailed).message)
        assertEquals(0, ttsEngine.calls.size)
    }

    @Test
    fun `TTS failure in local echo transitions to TtsFailed error`() = runTest {
        sttEngine.returnText = "hello crew"
        ttsEngine.shouldThrow = true
        ttsEngine.throwMessage = "audio init failed"
        val viewModel = createViewModel()
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        val error = (state as PipelineState.Error).error
        assertTrue(error is PipelineError.TtsFailed)
        assertEquals("audio init failed", (error as PipelineError.TtsFailed).message)
    }

    // --- Matrix Online Mode ---

    @Test
    fun `online mode - sends to Matrix and speaks crew response`() = runTest {
        sttEngine.returnText = "what is our heading"
        val viewModel = createViewModel(matrixClient = matrixClient, roomId = "!room:example.com")
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        // Advance enough to run STT + send, but not past the 30s timeout
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        // Should have sent to Matrix
        assertEquals(1, matrixClient.sentMessages.size)
        assertEquals("!room:example.com", matrixClient.sentMessages[0].roomId)
        assertEquals("what is our heading", matrixClient.sentMessages[0].text)

        // Simulate crew response before timeout
        matrixClient.simulateMessage(
            CrewMessage(crewName = "crest", verbosity = "normal", body = "bearing north-northeast", sender = "@crest:example.com")
        )
        advanceUntilIdle()

        // Should have completed the pipeline
        assertEquals(PipelineState.Idle, viewModel.state.value)
        assertEquals(1, ttsEngine.calls.size)
        assertEquals("crest", ttsEngine.calls[0].crewName)
        assertEquals("bearing north-northeast", ttsEngine.calls[0].text)
    }

    @Test
    fun `online mode - Matrix send failure transitions to MatrixFailed error`() = runTest {
        sttEngine.returnText = "check the charts"
        matrixClient.shouldThrowOnSend = true
        matrixClient.sendThrowMessage = "room not found"
        val viewModel = createViewModel(matrixClient = matrixClient, roomId = "!room:example.com")
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        val error = (state as PipelineState.Error).error
        assertTrue(error is PipelineError.MatrixFailed)
        assertEquals("room not found", (error as PipelineError.MatrixFailed).message)
        assertEquals(0, ttsEngine.calls.size)
    }

    @Test
    fun `online mode - starts Matrix sync at init`() = runTest {
        createViewModel(matrixClient = matrixClient, roomId = "!room:example.com")
        advanceUntilIdle()

        assertTrue(matrixClient.syncStarted)
        assertEquals("!room:example.com", matrixClient.listenedRoomId)
    }

    @Test
    fun `no Matrix sync when roomId is null`() = runTest {
        createViewModel(matrixClient = matrixClient, roomId = null)
        advanceUntilIdle()

        assertTrue(!matrixClient.syncStarted)
    }

    // --- Timeout ---

    @Test
    fun `online mode - response timeout transitions to MatrixFailed with timeout message`() = runTest {
        sttEngine.returnText = "anyone there"
        val viewModel = createViewModel(matrixClient = matrixClient, roomId = "!room:example.com")
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        // Advance past STT + send but not past timeout
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        // Should have sent to Matrix
        assertEquals(1, matrixClient.sentMessages.size)

        // Advance past the 30s timeout without sending a response
        testScheduler.advanceTimeBy(MainViewModel.RESPONSE_TIMEOUT_MS + 1_000)
        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertTrue(state is PipelineState.Error)
        val error = (state as PipelineState.Error).error
        assertTrue(error is PipelineError.MatrixFailed)
        assertEquals("timeout", (error as PipelineError.MatrixFailed).message)
        assertEquals(0, ttsEngine.calls.size)
    }

    // --- Pipeline uses correct audio file ---

    @Test
    fun `pipeline passes audioFileProvider result to STT`() = runTest {
        sttEngine.returnText = "test"
        val viewModel = createViewModel()
        advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        advanceUntilIdle()

        assertEquals(audioFile, sttEngine.lastFile)
    }
}
