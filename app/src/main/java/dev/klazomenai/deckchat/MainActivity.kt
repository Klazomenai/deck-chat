package dev.klazomenai.deckchat

import android.Manifest
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
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

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
        setContentView(R.layout.activity_main)

        val stateLabel = findViewById<TextView>(R.id.state_label)
        val stateIndicator = findViewById<View>(R.id.state_indicator)
        val settingsFab = findViewById<FloatingActionButton>(R.id.settings_fab)

        settingsFab.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateStateUi(state, stateLabel, stateIndicator)
                }
            }
        }
    }

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

        label.text = text
        val color = ContextCompat.getColor(this, colorRes)
        val background = indicator.background
        if (background is GradientDrawable) {
            background.mutate()
            background.setColor(color)
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

    private fun errorText(error: PipelineError): String = when (error) {
        is PipelineError.PermissionDenied -> getString(R.string.error_permission_denied)
        is PipelineError.PermissionPermanentlyDenied -> getString(R.string.error_permission_permanently_denied)
        else -> getString(R.string.state_error)
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
