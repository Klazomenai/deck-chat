package dev.klazomenai.deckchat

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * ViewModel for MainActivity. Holds the pipeline state, collects service events,
 * and orchestrates the voice pipeline: Recording → STT → Matrix → TTS → Idle.
 *
 * When Matrix is not configured ([matrixClient] or [roomId] is null), runs in
 * local echo mode: STT result is spoken back via TTS with the default crew voice.
 */
class MainViewModel(
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val matrixClient: MatrixClient?,
    private val roomId: String?,
    private val audioFileProvider: () -> File,
    private val defaultCrew: String = DEFAULT_CREW,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private var pendingResponse: CompletableDeferred<CrewMessage>? = null

    init {
        viewModelScope.launch {
            RecordingService.serviceEvents.collect { event ->
                when (event) {
                    is ServiceEvent.RecordingStarted -> {
                        _recordingDurationMs.value = 0L
                        _state.value = PipelineState.Recording
                    }
                    is ServiceEvent.RecordingProgress -> {
                        _recordingDurationMs.value = event.durationMs
                    }
                    is ServiceEvent.RecordingStopped -> {
                        _recordingDurationMs.value = 0L
                        _state.value = PipelineState.Processing("Transcribing")
                        runPipeline()
                    }
                    is ServiceEvent.Error -> _state.value = PipelineState.Error(event.error)
                }
            }
        }

        // Start Matrix sync if configured
        if (matrixClient != null && roomId != null) {
            initMatrixSync()
        }
    }

    private fun initMatrixSync() {
        matrixClient ?: return
        val room = roomId ?: return

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    matrixClient.restoreSession()

                    matrixClient.startSync { crewMessage ->
                        viewModelScope.launch {
                            pendingResponse?.complete(crewMessage)
                        }
                    }

                    matrixClient.listenToRoom(room)
                }
            } catch (e: Exception) {
                try {
                    withContext(ioDispatcher) { matrixClient.stop() }
                } catch (_: Exception) { /* best-effort cleanup */ }
                _state.value = PipelineState.Error(
                    PipelineError.MatrixFailed(e.message ?: "Matrix init failed"),
                )
            }
        }
    }

    private fun runPipeline() {
        viewModelScope.launch {
            try {
                // STT
                val audioFile = audioFileProvider()
                val text = sttEngine.transcribe(audioFile)

                if (text.isBlank()) {
                    _state.value = PipelineState.Idle
                    return@launch
                }

                _state.value = PipelineState.Transcribed(text)

                if (matrixClient != null && roomId != null) {
                    // Online mode: send to Matrix, await crew response, speak it
                    val responseDeferred = CompletableDeferred<CrewMessage>()
                    pendingResponse = responseDeferred
                    val response: CrewMessage
                    try {
                        _state.value = PipelineState.Processing("Sending")
                        withContext(ioDispatcher) { matrixClient.sendMessage(roomId, text) }

                        _state.value = PipelineState.Processing("Waiting for crew")
                        response = try {
                            withTimeout(RESPONSE_TIMEOUT_MS) { responseDeferred.await() }
                        } catch (e: TimeoutCancellationException) {
                            throw RuntimeException("timeout")
                        }
                    } finally {
                        pendingResponse = null
                    }

                    val displayName = CrewRegistry.lookup(response.crewName).displayName
                    _state.value = PipelineState.Speaking(displayName)
                    ttsEngine.speak(response.crewName, response.body)
                } else {
                    // Local echo: TTS reads back transcription with default crew
                    val displayName = CrewRegistry.lookup(defaultCrew).displayName
                    _state.value = PipelineState.Speaking(displayName)
                    ttsEngine.speak(defaultCrew, text)
                }

                _state.value = PipelineState.Idle
            } catch (e: Exception) {
                _state.value = PipelineState.Error(classifyError(e))
            }
        }
    }

    // Stage strings are set and checked in this file only. Extract to a sealed type
    // when localising UI strings (M2) — see Copilot review on PR #89.
    private fun classifyError(e: Exception): PipelineError {
        val state = _state.value
        return when {
            state is PipelineState.Processing && state.stage == "Transcribing" ->
                PipelineError.SttFailed(e.message ?: "STT failed")
            state is PipelineState.Processing && (state.stage == "Sending" || state.stage == "Waiting for crew") ->
                PipelineError.MatrixFailed(e.message ?: "Matrix failed")
            state is PipelineState.Speaking ->
                PipelineError.TtsFailed(e.message ?: "TTS failed")
            else -> PipelineError.SttFailed(e.message ?: "Pipeline failed")
        }
    }

    /**
     * Toggle recording on/off. Called from UI (FAB, headset button).
     * Returns the intent action to dispatch, or null if no action needed.
     */
    fun toggleRecording(): String? {
        return when (_state.value) {
            is PipelineState.Idle, is PipelineState.Error -> {
                _state.value = PipelineState.Recording
                RecordingService.ACTION_START
            }
            is PipelineState.Recording -> {
                RecordingService.ACTION_STOP
            }
            else -> null
        }
    }

    /**
     * Request stop — only returns ACTION_STOP when currently recording.
     * Does not mutate state for non-recording states (unlike [toggleRecording]).
     */
    fun requestStop(): String? {
        return if (_state.value is PipelineState.Recording) {
            RecordingService.ACTION_STOP
        } else {
            null
        }
    }

    /** Reset to idle — used after error display or TTS completion. */
    fun resetToIdle() {
        _state.value = PipelineState.Idle
    }

    /** Set state directly — used by the activity for pipeline stages not driven by service events. */
    fun setState(newState: PipelineState) {
        _state.value = newState
    }

    override fun onCleared() {
        super.onCleared()
        releaseResources()
    }

    @VisibleForTesting
    internal fun releaseResources() {
        pendingResponse?.cancel()
        pendingResponse = null
        sttEngine.close()
        ttsEngine.close()
        matrixClient?.let { client ->
            runBlocking(Dispatchers.IO + NonCancellable) { client.stop() }
        }
    }

    class Factory(
        private val sttEngine: SttEngine,
        private val ttsEngine: TtsEngine,
        private val matrixClient: MatrixClient?,
        private val roomId: String?,
        private val audioFileProvider: () -> File,
        private val defaultCrew: String = DEFAULT_CREW,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(sttEngine, ttsEngine, matrixClient, roomId, audioFileProvider, defaultCrew, ioDispatcher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        internal const val RESPONSE_TIMEOUT_MS = 30_000L
        internal const val DEFAULT_CREW = "maren"
    }
}
