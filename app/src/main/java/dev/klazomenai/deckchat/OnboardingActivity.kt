package dev.klazomenai.deckchat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {

    private lateinit var storage: SecureStorage
    private var currentStep = 0

    private lateinit var stepViews: List<View>
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button
    private lateinit var stepIndicator: TextView

    // Permission launchers — must be registered at class level
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { updatePermissionStatus() }

    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { updatePermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        storage = SecureStorage(this)

        stepIndicator = findViewById(R.id.step_indicator)
        btnBack = findViewById(R.id.btn_back)
        btnNext = findViewById(R.id.btn_next)

        stepViews = listOf(
            findViewById(R.id.step_login),
            findViewById(R.id.step_voice),
            findViewById(R.id.step_permissions),
            findViewById(R.id.step_bluetooth),
        )

        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(KEY_CURRENT_STEP, 0)
        }

        setupPermissionsStep()
        showStep(currentStep)

        btnBack.setOnClickListener { goBack() }
        btnNext.setOnClickListener { goNext() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_STEP, currentStep)
    }

    private fun showStep(step: Int) {
        stepViews.forEachIndexed { i, view ->
            view.visibility = if (i == step) View.VISIBLE else View.GONE
        }

        stepIndicator.text = getString(R.string.onboarding_step_indicator, step + 1, TOTAL_STEPS)
        btnBack.visibility = if (step > 0) View.VISIBLE else View.GONE
        btnNext.text = if (step == LAST_STEP) getString(R.string.onboarding_finish) else getString(R.string.onboarding_next)
        btnNext.isEnabled = true

        if (step == STEP_PERMISSIONS) {
            updatePermissionStatus()
        }
    }

    private fun goBack() {
        if (currentStep > 0) {
            currentStep--
            showStep(currentStep)
        } else {
            finish()
        }
    }

    private fun goNext() {
        when (currentStep) {
            STEP_LOGIN -> validateAndLogin()
            STEP_VOICE -> { saveVoiceProfile(); advanceStep() }
            STEP_PERMISSIONS -> advanceStep()
            STEP_BLUETOOTH -> finishOnboarding()
        }
    }

    private fun advanceStep() {
        if (currentStep < LAST_STEP) {
            currentStep++
            showStep(currentStep)
        }
    }

    // --- Step 1: Login ---

    private fun validateAndLogin() {
        val homeserverInput = findViewById<EditText>(R.id.onboarding_homeserver_url)
        val usernameInput = findViewById<EditText>(R.id.onboarding_username)
        val passwordInput = findViewById<EditText>(R.id.onboarding_password)
        val roomIdInput = findViewById<EditText>(R.id.onboarding_room_id)
        val loginProgress = findViewById<ProgressBar>(R.id.login_progress)
        val loginError = findViewById<TextView>(R.id.login_error)

        val url = homeserverInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val roomId = roomIdInput.text.toString().trim()

        if (url.isEmpty()) { homeserverInput.error = "Required"; return }
        if (!url.startsWith("https://")) { homeserverInput.error = "Must start with https://"; return }
        if (Uri.parse(url).host.isNullOrBlank()) { homeserverInput.error = "Invalid URL"; return }
        if (username.isEmpty()) { usernameInput.error = "Required"; return }
        if (password.isEmpty()) { passwordInput.error = "Required"; return }
        if (roomId.isEmpty()) { roomIdInput.error = "Required"; return }

        loginProgress.visibility = View.VISIBLE
        loginError.visibility = View.GONE
        btnNext.isEnabled = false

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = RustMatrixClient(applicationContext, storage)
                    client.login(url, username, password)
                }
                storage.roomId = roomId
                loginProgress.visibility = View.GONE
                advanceStep()
            } catch (e: Exception) {
                loginProgress.visibility = View.GONE
                loginError.text = e.message ?: "Login failed"
                loginError.visibility = View.VISIBLE
                btnNext.isEnabled = true
            }
        }
    }

    // --- Step 2: Voice ---

    private fun saveVoiceProfile() {
        val radioGroup = findViewById<RadioGroup>(R.id.voice_radio_group)
        storage.voiceProfile = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_crest -> "crest"
            else -> "maren"
        }
    }

    // --- Step 3: Permissions ---

    private fun setupPermissionsStep() {
        findViewById<Button>(R.id.btn_grant_audio).setOnClickListener {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        val btButton = findViewById<Button>(R.id.btn_grant_bluetooth)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btButton.setOnClickListener {
                requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            btButton.isEnabled = false
            findViewById<TextView>(R.id.bt_permission_status).text =
                getString(R.string.onboarding_permission_granted)
        }
    }

    private fun updatePermissionStatus() {
        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        findViewById<TextView>(R.id.audio_permission_status).text =
            if (audioGranted) getString(R.string.onboarding_permission_granted)
            else getString(R.string.onboarding_permission_required)

        findViewById<Button>(R.id.btn_grant_audio).isEnabled = !audioGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val btGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

            findViewById<TextView>(R.id.bt_permission_status).text =
                if (btGranted) getString(R.string.onboarding_permission_granted)
                else getString(R.string.onboarding_permission_optional)

            findViewById<Button>(R.id.btn_grant_bluetooth).isEnabled = !btGranted
        }
    }

    // --- Step 4: Finish ---

    private fun finishOnboarding() {
        storage.onboardingComplete = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val STEP_LOGIN = 0
        private const val STEP_VOICE = 1
        private const val STEP_PERMISSIONS = 2
        private const val STEP_BLUETOOTH = 3
        private const val LAST_STEP = STEP_BLUETOOTH
        private const val TOTAL_STEPS = 4
        private const val KEY_CURRENT_STEP = "current_step"
    }
}
