package dev.klazomenai.deckchat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen for configuring the Matrix homeserver connection.
 *
 * Stores homeserver URL in EncryptedSharedPreferences via [SecureStorage].
 * Matrix session tokens are stored with Android Keystore encryption.
 * No hardcoded URLs — all configured at runtime.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var storage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        storage = SecureStorage(this)

        val homeserverInput = findViewById<EditText>(R.id.homeserver_url_input)
        val roomIdInput = findViewById<EditText>(R.id.room_id_input)
        val statusText = findViewById<TextView>(R.id.session_status)
        val saveButton = findViewById<Button>(R.id.save_button)
        val clearButton = findViewById<Button>(R.id.clear_session_button)

        // Load saved values
        homeserverInput.setText(storage.homeserverUrl ?: "")
        roomIdInput.setText(storage.roomId ?: "")
        updateSessionStatus(statusText)

        saveButton.setOnClickListener {
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
            storage.homeserverUrl = url

            val roomId = roomIdInput.text.toString().trim()
            storage.roomId = roomId.ifEmpty { null }

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
