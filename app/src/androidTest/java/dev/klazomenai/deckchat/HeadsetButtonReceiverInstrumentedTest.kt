package dev.klazomenai.deckchat

import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [HeadsetButtonReceiver].
 * Invokes [HeadsetButtonReceiver.onReceive] directly to verify it does not crash.
 * We call onReceive directly rather than sending a real broadcast because
 * ACTION_MEDIA_BUTTON is a protected broadcast on modern Android and would be
 * intercepted by the system media session. Manifest registration is verified
 * implicitly by the app functioning on-device.
 *
 * Full service lifecycle testing is covered in [RecordingServiceTest].
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class HeadsetButtonReceiverInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        // Ensure RecordingService is stopped even if ACTION_UP was swallowed
        // by background start restrictions.
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        try {
            context.startService(stopIntent)
        } catch (_: IllegalStateException) {
            context.stopService(stopIntent)
        }
    }

    @Test
    fun broadcastDispatchDoesNotCrash() {
        val receiver = HeadsetButtonReceiver()

        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, event)
        }

        // Dispatch ACTION_DOWN directly to the receiver
        receiver.onReceive(context, intent)

        // Send ACTION_UP to clean up — without this, the service may remain
        // running with the mic held open and a lingering notification.
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
        val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, upEvent)
        }
        receiver.onReceive(context, upIntent)
    }
}
