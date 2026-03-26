package dev.klazomenai.deckchat

/**
 * Represents the current state of the voice pipeline.
 *
 * The state machine flows: Idle → Recording → Processing → Transcribed → Speaking → Idle.
 * Any state can transition to Error; returning to Idle is handled explicitly by the caller.
 */
sealed class PipelineState {
    data object Idle : PipelineState()
    data object Recording : PipelineState()
    data class Processing(val stage: String) : PipelineState()
    data class Transcribed(val text: String) : PipelineState()
    data class Speaking(val crewName: String) : PipelineState()
    data class Error(val error: PipelineError) : PipelineState()
}

/**
 * Categorised pipeline errors with enough context to show actionable UI feedback.
 */
sealed class PipelineError {
    data object PermissionDenied : PipelineError()
    data object PermissionPermanentlyDenied : PipelineError()
    data object MicBusy : PipelineError()
    data object AudioInitFailed : PipelineError()
    data object ModelMissing : PipelineError()
    data object BluetoothLost : PipelineError()
    data class SttFailed(val message: String) : PipelineError()
    data class TtsFailed(val message: String) : PipelineError()
    data class MatrixFailed(val message: String) : PipelineError()
}
