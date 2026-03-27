package dev.klazomenai.deckchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity. Holds the pipeline state and collects service events.
 *
 * Observes [RecordingService.serviceEvents] to translate service-level events into
 * UI-facing [PipelineState] transitions.
 */
class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            RecordingService.serviceEvents.collect { event ->
                when (event) {
                    is ServiceEvent.RecordingStarted -> _state.value = PipelineState.Recording
                    is ServiceEvent.RecordingStopped -> _state.value = PipelineState.Processing("Transcribing")
                    is ServiceEvent.Error -> _state.value = PipelineState.Error(event.error)
                }
            }
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
            else -> null // Ignore during processing/speaking
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
}
