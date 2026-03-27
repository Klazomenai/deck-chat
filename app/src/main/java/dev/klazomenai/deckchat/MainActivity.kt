package dev.klazomenai.deckchat

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

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
            is PipelineState.Error -> getString(R.string.state_error) to R.color.state_error
        }

        label.text = text
        val color = ContextCompat.getColor(this, colorRes)
        val background = indicator.background
        if (background is GradientDrawable) {
            background.mutate()
            background.setColor(color)
        }
    }
}
