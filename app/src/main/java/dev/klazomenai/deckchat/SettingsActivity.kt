package dev.klazomenai.deckchat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings screen for configuring the Matrix homeserver connection.
 *
 * Stores homeserver URL in [TinkAeadPrefs] via [SecureStorage].
 * Matrix session tokens are stored with Android Keystore encryption.
 * No hardcoded URLs — all configured at runtime.
 */
class SettingsActivity : AppCompatActivity() {

    private val storage get() = (application as DeckChatApplication).secureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val homeserverInput = findViewById<EditText>(R.id.homeserver_url_input)
        val roomIdInput = findViewById<EditText>(R.id.room_id_input)
        val timeoutInput = findViewById<EditText>(R.id.response_timeout_input)
        val debugSwitch = findViewById<SwitchMaterial>(R.id.debug_mode_switch)
        val timingsSwitch = findViewById<SwitchMaterial>(R.id.show_timings_switch)
        val statusText = findViewById<TextView>(R.id.session_status)
        val saveButton = findViewById<Button>(R.id.save_button)
        val clearButton = findViewById<Button>(R.id.clear_session_button)

        // Load saved values
        homeserverInput.setText(storage.homeserverUrl ?: "")
        roomIdInput.setText(storage.roomId ?: "")
        timeoutInput.setText(storage.responseTimeoutSec.toString())
        debugSwitch.isChecked = storage.debugMode
        timingsSwitch.isChecked = storage.showTimings
        updateSessionStatus(statusText)

        saveButton.setOnClickListener {
            // Validate all inputs before saving any
            val url = homeserverInput.text.toString().trim()
            if (url.isEmpty()) {
                homeserverInput.error = "Homeserver URL is required"
                return@setOnClickListener
            }
            if (!url.startsWith("https://")) {
                homeserverInput.error = "URL must start with https://"
                return@setOnClickListener
            }
            val uri = android.net.Uri.parse(url)
            if (uri.host.isNullOrBlank()) {
                homeserverInput.error = "URL must include a hostname"
                return@setOnClickListener
            }
            val roomId = roomIdInput.text.toString().trim()
            val timeoutSec = timeoutInput.text.toString().trim().toIntOrNull()
            if (timeoutSec == null || timeoutSec !in SecureStorage.MIN_RESPONSE_TIMEOUT_SEC..SecureStorage.MAX_RESPONSE_TIMEOUT_SEC) {
                timeoutInput.error = getString(R.string.response_timeout_range_error)
                return@setOnClickListener
            }

            // All valid — persist
            timeoutInput.error = null
            storage.homeserverUrl = url
            storage.roomId = roomId.ifEmpty { null }
            storage.responseTimeoutSec = timeoutSec
            storage.debugMode = debugSwitch.isChecked
            storage.showTimings = timingsSwitch.isChecked

            updateSessionStatus(statusText)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        clearButton.setOnClickListener {
            storage.clearSession()
            updateSessionStatus(statusText)
            Toast.makeText(this, "Session cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSessionStatus(statusText: TextView) {
        if (storage.hasSession()) {
            statusText.text = getString(R.string.session_active, storage.userId ?: "unknown")
        } else {
            statusText.text = getString(R.string.session_none)
        }
    }
}
