package dev.klazomenai.deckchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        try {
            viewModels.forEach { it.releaseResources() }
            viewModels.clear()
        } finally {
            Dispatchers.resetMain()
            if (::audioFile.isInitialized) {
                audioFile.delete()
            }
        }
    }

    private fun createViewModel(
        sttResult: String = "",
        matrixClient: MatrixClient? = null,
        roomId: String? = null,
        defaultCrew: String = "maren",
    ): MainViewModel {
        return MainViewModel(
            sttEngine = MockSttEngine(returnText = sttResult),
            ttsEngine = MockTtsEngine(),
            matrixClient = matrixClient,
            roomId = roomId,
            audioFileProvider = { audioFile },
            defaultCrew = defaultCrew,
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

    // --- Debug transcript ---

    @Test
    fun `lastUserText and lastCrewResponse default to null`() {
        val viewModel = createViewModel()
        assertNull(viewModel.lastUserText.value)
        assertNull(viewModel.lastCrewResponse.value)
    }

    @Test
    fun `recording started clears transcript`() = runTest {
        val viewModel = createViewModel(sttResult = "hello crew")
        testDispatcher.scheduler.advanceUntilIdle()

        // Drive pipeline to set lastUserText
        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("hello crew", viewModel.lastUserText.value)

        // New recording should clear it
        RecordingService.emitEvent(ServiceEvent.RecordingStarted)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.lastUserText.value)
        assertNull(viewModel.lastCrewResponse.value)
    }

    @Test
    fun `local echo pipeline sets lastUserText`() = runTest {
        val viewModel = createViewModel(sttResult = "hello crew")
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("hello crew", viewModel.lastUserText.value)
    }

    @Test
    fun `blank STT does not set lastUserText`() = runTest {
        val viewModel = createViewModel(sttResult = "")
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.lastUserText.value)
    }

    // --- Pipeline timings ---

    @Test
    fun `lastTimings defaults to null`() {
        val viewModel = createViewModel()
        assertNull(viewModel.lastTimings.value)
    }

    @Test
    fun `recording started clears timings`() = runTest {
        val viewModel = createViewModel(sttResult = "hello")
        testDispatcher.scheduler.advanceUntilIdle()

        // Run pipeline to set timings (local echo)
        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.lastTimings.value)

        // New recording should clear
        RecordingService.emitEvent(ServiceEvent.RecordingStarted)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.lastTimings.value)
    }

    @Test
    fun `local echo pipeline sets stt and tts timings with null bridge`() = runTest {
        val viewModel = createViewModel(sttResult = "hello")
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        val timings = viewModel.lastTimings.value
        assertNotNull(timings)
        assertTrue(timings!!.sttMs >= 0)
        assertNull(timings.bridgeMs)
        assertTrue(timings.ttsMs >= 0)
        assertTrue(timings.totalMs >= 0)
    }

    @Test
    fun `blank STT does not set timings`() = runTest {
        val viewModel = createViewModel(sttResult = "")
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.lastTimings.value)
    }

    // --- UTD surface (#167) ---

    @Test
    fun `lastUtd defaults to null when no matrix client`() {
        val viewModel = createViewModel()
        assertNull(viewModel.lastUtd.value)
    }

    @Test
    fun `lastUtd defaults to null with matrix client and no events`() = runTest {
        val client = MockMatrixClient()
        val viewModel = createViewModel(matrixClient = client, roomId = "!room:example.com")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.lastUtd.value)
    }

    @Test
    fun `lastUtd updates when matrix client emits UTD event`() = runTest {
        val client = MockMatrixClient()
        val viewModel = createViewModel(matrixClient = client, roomId = "!room:example.com")
        testDispatcher.scheduler.advanceUntilIdle()

        val event = UtdEvent(
            eventId = "\$abc123",
            sender = "@bot:example.com",
            cause = "UNKNOWN_DEVICE",
            timestampMs = 1_700_000_000_000L,
        )
        client.simulateUtd(event)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(event, viewModel.lastUtd.value)
    }

    @Test
    fun `lastUtd reflects most recent UTD when multiple emitted`() = runTest {
        val client = MockMatrixClient()
        val viewModel = createViewModel(matrixClient = client, roomId = "!room:example.com")
        testDispatcher.scheduler.advanceUntilIdle()

        val first = UtdEvent("\$a", "@a:example.com", "UNKNOWN_DEVICE", 1L)
        val second = UtdEvent("\$b", "@b:example.com", "WITHHELD_BY_SENDER", 2L)
        client.simulateUtd(first)
        client.simulateUtd(second)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(second, viewModel.lastUtd.value)
    }

    // --- Delegation settling ---

    private fun createOnlineViewModel(
        sttResult: String = "hello",
        matrixClient: MockMatrixClient = MockMatrixClient(),
        roomId: String = "!room:example.com",
    ): Pair<MainViewModel, MockMatrixClient> {
        val tts = MockTtsEngine()
        val vm = MainViewModel(
            sttEngine = MockSttEngine(returnText = sttResult),
            ttsEngine = tts,
            matrixClient = matrixClient,
            roomId = roomId,
            audioFileProvider = { audioFile },
            ioDispatcher = testDispatcher,
        ).also { viewModels.add(it) }
        return vm to matrixClient
    }

    @Test
    fun `single crew message spoken after settling`() = runTest {
        val (viewModel, _) = createOnlineViewModel()
        testDispatcher.scheduler.advanceUntilIdle() // init + sync

        // Trigger pipeline — use runCurrent to advance without blowing through timeout
        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.runCurrent() // process event
        testDispatcher.scheduler.runCurrent() // process pipeline launch + STT + send

        // Pipeline is now suspended at crewMessages.receive()
        viewModel.crewMessages.trySend(CrewMessage("maren", "dispatch", "Aye aye", "@bridge:example.com"))
        testDispatcher.scheduler.runCurrent()

        // Response received but settle window not elapsed — should not be spoken yet
        assertNull(viewModel.lastCrewResponse.value)

        // Advance past settling window so pipeline completes
        testDispatcher.scheduler.advanceTimeBy(MainViewModel.DELEGATION_SETTLE_MS + 100)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Aye aye", viewModel.lastCrewResponse.value?.body)
        assertEquals("maren", viewModel.lastCrewResponse.value?.crewName)
    }

    @Test
    fun `delegation chain speaks last message`() = runTest {
        val (viewModel, _) = createOnlineViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.runCurrent()

        // Maren responds first
        viewModel.crewMessages.trySend(CrewMessage("maren", "dispatch", "Passing to Crest", "@bridge:example.com"))
        testDispatcher.scheduler.runCurrent()

        // Maren received but settle window not elapsed — should not be finalized
        assertNull(viewModel.lastCrewResponse.value)

        // Crest responds 2s later (within settle window)
        testDispatcher.scheduler.advanceTimeBy(2_000)
        viewModel.crewMessages.trySend(CrewMessage("crest", "dispatch", "Signal received", "@bridge:example.com"))
        testDispatcher.scheduler.runCurrent()

        // Crest received but settle window restarted — still not finalized
        assertNull(viewModel.lastCrewResponse.value)

        // Advance past settle window from last message
        testDispatcher.scheduler.advanceTimeBy(MainViewModel.DELEGATION_SETTLE_MS + 100)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Signal received", viewModel.lastCrewResponse.value?.body)
        assertEquals("crest", viewModel.lastCrewResponse.value?.crewName)
    }

    @Test
    fun `messages ignored when not awaiting response`() = runTest {
        val (viewModel, mock) = createOnlineViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Pipeline is idle — not awaiting response.
        // simulateMessage goes through the callback which checks awaitingResponse.
        mock.simulateMessage(CrewMessage("maren", "dispatch", "Stale", "@bridge:example.com"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Channel should be empty since awaitingResponse was false
        assertTrue(viewModel.crewMessages.tryReceive().isFailure)
    }

    // --- Voice profile ---

    @Test
    fun `local echo uses configured voice profile`() = runTest {
        val tts = MockTtsEngine()
        val viewModel = MainViewModel(
            sttEngine = MockSttEngine(returnText = "hello"),
            ttsEngine = tts,
            matrixClient = null,
            roomId = null,
            audioFileProvider = { audioFile },
            defaultCrew = "crest",
            ioDispatcher = testDispatcher,
        ).also { viewModels.add(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        RecordingService.emitEvent(ServiceEvent.RecordingStopped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, tts.calls.size)
        assertEquals("crest", tts.calls[0].crewName)
    }
}
