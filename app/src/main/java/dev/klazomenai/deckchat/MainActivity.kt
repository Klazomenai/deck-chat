package dev.klazomenai.deckchat

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        val appContext = applicationContext
        val storage = (application as DeckChatApplication).secureStorage
        val sttEngine = SherpaOnnxSttEngine(appContext)
        val ttsEngine = SherpaOnnxTtsEngine(appContext)
        val matrixClient: MatrixClient? = if (storage.hasSession()) RustMatrixClient(appContext, storage) else null
        val roomId = storage.roomId
        val voiceProfile = storage.voiceProfile ?: "maren"
        val timeoutMs = storage.responseTimeoutSec.toLong() * 1000L
        MainViewModel.Factory(sttEngine, ttsEngine, matrixClient, roomId,
            audioFileProvider = { File(appContext.cacheDir, "recording.pcm") },
            defaultCrew = voiceProfile,
            responseTimeoutMs = timeoutMs,
        )
    }
    private val storage get() = (application as DeckChatApplication).secureStorage
    private var currentIndicatorColor: Int = 0
    private var colorAnimator: ValueAnimator? = null
    private var debugMode = false
    private var showTimings = false
    private lateinit var debugUserText: TextView
    private lateinit var debugCrewText: TextView
    private lateinit var debugTimingStt: TextView
    private lateinit var debugTimingBridge: TextView
    private lateinit var debugTimingTts: TextView
    private lateinit var debugTimingTotal: TextView
    private lateinit var debugUtdText: TextView

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecordingService()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            viewModel.setState(PipelineState.Error(PipelineError.PermissionPermanentlyDenied))
        } else {
            viewModel.setState(PipelineState.Error(PipelineError.PermissionDenied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!storage.onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val stateLabel = findViewById<TextView>(R.id.state_label)
        val stateDetail = findViewById<TextView>(R.id.state_detail)
        val stateIndicator = findViewById<View>(R.id.state_indicator)
        debugUserText = findViewById(R.id.debug_user_text)
        debugCrewText = findViewById(R.id.debug_crew_text)
        debugTimingStt = findViewById(R.id.debug_timing_stt)
        debugTimingBridge = findViewById(R.id.debug_timing_bridge)
        debugTimingTts = findViewById(R.id.debug_timing_tts)
        debugTimingTotal = findViewById(R.id.debug_timing_total)
        debugUtdText = findViewById(R.id.debug_utd_text)
        val settingsFab = findViewById<FloatingActionButton>(R.id.settings_fab)
        val pttFab = findViewById<FloatingActionButton>(R.id.ptt_fab)

        debugMode = storage.debugMode
        showTimings = storage.showTimings

        settingsFab.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        pttFab.setOnClickListener {
            when (viewModel.state.value) {
                is PipelineState.Recording -> onStopRequested()
                else -> onRecordRequested()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateStateUi(state, stateLabel, stateDetail, stateIndicator)
                    updatePttFab(state, pttFab)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordingDurationMs.collect { durationMs ->
                    if (viewModel.state.value is PipelineState.Recording && durationMs > 0L) {
                        val seconds = durationMs / 1000.0
                        val formatted = String.format(java.util.Locale.ROOT, "%.1fs", seconds)
                        stateLabel.text = getString(R.string.state_recording_duration, formatted)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.lastUserText, viewModel.lastCrewResponse, viewModel.lastTimings, viewModel.lastUtd) { _, _, _, _ -> }
                    .collect { updateTranscript() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        debugMode = storage.debugMode
        showTimings = storage.showTimings
        updateTranscript()
    }

    private fun updateTranscript() {
        val userText = viewModel.lastUserText.value
        val crewMsg = viewModel.lastCrewResponse.value
        val t = viewModel.lastTimings.value
        val timingsVisible = debugMode && showTimings && t != null

        if (debugMode && userText != null) {
            debugUserText.text = getString(R.string.debug_transcript_you, userText)
            debugUserText.visibility = View.VISIBLE
        } else {
            debugUserText.visibility = View.GONE
        }
        if (timingsVisible && userText != null) {
            debugTimingStt.text = getString(R.string.debug_timing_stt, formatDuration(t!!.sttMs))
            debugTimingStt.visibility = View.VISIBLE
        } else {
            debugTimingStt.visibility = View.GONE
        }
        val bridgeMs = t?.bridgeMs
        if (timingsVisible && bridgeMs != null) {
            debugTimingBridge.text = getString(R.string.debug_timing_bridge, formatDuration(bridgeMs))
            debugTimingBridge.visibility = View.VISIBLE
        } else {
            debugTimingBridge.visibility = View.GONE
        }
        if (debugMode && crewMsg != null && userText != null) {
            val name = CrewRegistry.lookup(crewMsg.crewName).displayName
            debugCrewText.text = getString(R.string.debug_transcript_crew, name, crewMsg.body)
            debugCrewText.visibility = View.VISIBLE
        } else {
            debugCrewText.visibility = View.GONE
        }
        if (timingsVisible) {
            debugTimingTts.text = getString(R.string.debug_timing_tts, formatDuration(t!!.ttsMs))
            debugTimingTts.visibility = View.VISIBLE
            debugTimingTotal.text = getString(R.string.debug_timing_total, formatDuration(t.totalMs))
            debugTimingTotal.visibility = View.VISIBLE
        } else {
            debugTimingTts.visibility = View.GONE
            debugTimingTotal.visibility = View.GONE
        }
        val utd = viewModel.lastUtd.value
        if (debugMode && utd != null) {
            val sender = utd.sender ?: getString(R.string.debug_utd_unknown_sender)
            val shortId = utd.eventId.take(UTD_EVENT_ID_DISPLAY_LEN)
            debugUtdText.text = getString(R.string.debug_utd, sender, shortId, utd.cause)
            debugUtdText.visibility = View.VISIBLE
        } else {
            debugUtdText.visibility = View.GONE
        }
    }

    private fun formatDuration(ms: Long): String =
        String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0)

    /**
     * Called by UI controls (FAB, headset) to initiate recording.
     * Checks RECORD_AUDIO permission before starting the service.
     */
    fun onRecordRequested() {
        val state = viewModel.state.value
        if (state !is PipelineState.Idle && state !is PipelineState.Error) return
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED -> {
                startRecordingService()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> {
                showPermissionRationale()
            }
            else -> {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecordingService() {
        val action = viewModel.toggleRecording() ?: return
        val intent = Intent(this, RecordingService::class.java).apply {
            this.action = action
        }
        if (action == RecordingService.ACTION_START) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    /** Stop recording — called directly, no permission check needed. */
    fun onStopRequested() {
        val action = viewModel.requestStop() ?: return
        startService(Intent(this, RecordingService::class.java).apply {
            this.action = action
        })
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_rationale_title))
            .setMessage(getString(R.string.permission_rationale_message))
            .setPositiveButton(getString(R.string.permission_rationale_grant)) { _, _ ->
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(getString(R.string.permission_rationale_deny)) { _, _ ->
                viewModel.setState(PipelineState.Error(PipelineError.PermissionDenied))
            }
            .show()
    }

    /** Opens the app's system settings page for manual permission grant. */
    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun updateStateUi(
        state: PipelineState,
        label: TextView,
        detail: TextView,
        indicator: View,
    ) {
        val (text, colorRes) = when (state) {
            is PipelineState.Idle -> getString(R.string.state_idle) to R.color.state_idle
            is PipelineState.Recording -> getString(R.string.state_recording) to R.color.state_recording
            is PipelineState.Processing -> state.stage to R.color.state_processing
            is PipelineState.Transcribed -> getString(R.string.state_transcribed) to R.color.state_transcribed
            is PipelineState.Speaking -> getString(R.string.state_speaking, state.crewName) to R.color.state_speaking
            is PipelineState.Error -> errorText(state.error) to R.color.state_error
        }

        // Show detail text for transcription results and error messages
        when (state) {
            is PipelineState.Transcribed -> {
                detail.text = state.text
                detail.visibility = View.VISIBLE
            }
            is PipelineState.Error -> {
                val detailMsg = when (val err = state.error) {
                    is PipelineError.SttFailed -> err.message
                    is PipelineError.TtsFailed -> err.message
                    is PipelineError.MatrixFailed -> err.message
                    else -> null
                }
                if (detailMsg != null) {
                    detail.text = detailMsg
                    detail.visibility = View.VISIBLE
                } else {
                    detail.text = ""
                    detail.visibility = View.GONE
                }
            }
            else -> {
                detail.text = ""
                detail.visibility = View.GONE
            }
        }

        // Crossfade text label: fade out, swap text, fade in
        label.animate().cancel()
        val currentText = label.text?.toString().orEmpty()
        if (currentText.isEmpty() || currentText == text) {
            // First render or no text change — set directly, no animation
            label.text = text
            label.alpha = 1f
        } else {
            label.animate()
                .alpha(0f)
                .setDuration(CROSSFADE_DURATION_MS / 2)
                .withEndAction {
                    label.text = text
                    label.animate()
                        .alpha(1f)
                        .setDuration(CROSSFADE_DURATION_MS / 2)
                        .start()
                }
                .start()
        }

        // Animate indicator colour
        val targetColor = ContextCompat.getColor(this, colorRes)
        val background = indicator.background
        if (background is GradientDrawable) {
            background.mutate()
            if (currentIndicatorColor == 0) {
                // First render — set directly, no animation
                background.setColor(targetColor)
                currentIndicatorColor = targetColor
            } else if (currentIndicatorColor != targetColor) {
                colorAnimator?.cancel()
                colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentIndicatorColor, targetColor).apply {
                    duration = COLOR_FADE_DURATION_MS
                    addUpdateListener { animator ->
                        val color = animator.animatedValue as Int
                        background.setColor(color)
                        currentIndicatorColor = color
                    }
                    start()
                }
            }
        }

        // Handle permission error states with actionable dialogs
        if (state is PipelineState.Error) {
            when (state.error) {
                is PipelineError.PermissionPermanentlyDenied -> {
                    showPermanentDenialDialog()
                }
                else -> { /* Other errors handled by #32 */ }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        colorAnimator?.cancel()
        colorAnimator = null
    }

    companion object {
        private const val CROSSFADE_DURATION_MS = 300L
        private const val COLOR_FADE_DURATION_MS = 400L
        private const val UTD_EVENT_ID_DISPLAY_LEN = 12
    }

    private fun updatePttFab(state: PipelineState, fab: FloatingActionButton) {
        when (state) {
            is PipelineState.Recording -> {
                fab.setImageResource(R.drawable.ic_stop)
                fab.contentDescription = getString(R.string.cd_ptt_stop)
                fab.isEnabled = true
            }
            is PipelineState.Processing, is PipelineState.Speaking, is PipelineState.Transcribed -> {
                fab.setImageResource(android.R.drawable.ic_btn_speak_now)
                fab.contentDescription = getString(R.string.cd_ptt_start)
                fab.isEnabled = false
            }
            else -> {
                fab.setImageResource(android.R.drawable.ic_btn_speak_now)
                fab.contentDescription = getString(R.string.cd_ptt_start)
                fab.isEnabled = true
            }
        }
    }

    private fun errorText(error: PipelineError): String = when (error) {
        is PipelineError.PermissionDenied -> getString(R.string.error_permission_denied)
        is PipelineError.PermissionPermanentlyDenied -> getString(R.string.error_permission_permanently_denied)
        is PipelineError.MicBusy -> getString(R.string.error_mic_busy)
        is PipelineError.AudioInitFailed -> getString(R.string.error_audio_init_failed)
        is PipelineError.ModelMissing -> getString(R.string.error_model_missing)
        is PipelineError.BluetoothLost -> getString(R.string.error_bluetooth_lost)
        is PipelineError.SttFailed -> getString(R.string.error_stt_failed, error.message)
        is PipelineError.TtsFailed -> getString(R.string.error_tts_failed, error.message)
        is PipelineError.MatrixFailed -> getString(R.string.error_matrix_failed, error.message)
        is PipelineError.ResponseTimeout -> getString(R.string.error_response_timeout)
        is PipelineError.PipelineCancelled -> getString(R.string.error_pipeline_cancelled)
    }

    private fun showPermanentDenialDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_settings_title))
            .setMessage(getString(R.string.permission_settings_message))
            .setPositiveButton(getString(R.string.permission_settings_open)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.permission_rationale_deny), null)
            .setOnDismissListener { viewModel.resetToIdle() }
            .show()
    }
}
