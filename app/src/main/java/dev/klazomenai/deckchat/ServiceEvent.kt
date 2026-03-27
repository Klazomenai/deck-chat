package dev.klazomenai.deckchat

/**
 * Events emitted by [RecordingService] via its companion [RecordingService.serviceEvents] SharedFlow.
 *
 * The ViewModel collects these to drive [PipelineState] transitions without
 * requiring a bound service connection.
 */
sealed class ServiceEvent {
    /** Recording has started successfully. */
    data object RecordingStarted : ServiceEvent()

    /** Recording stopped normally — PCM file is ready. */
    data object RecordingStopped : ServiceEvent()

    /** An error occurred in the service. */
    data class Error(val error: PipelineError) : ServiceEvent()
}
