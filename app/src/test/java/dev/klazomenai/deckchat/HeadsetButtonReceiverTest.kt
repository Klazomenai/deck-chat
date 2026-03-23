package dev.klazomenai.deckchat

import android.content.Intent
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HeadsetButtonReceiverTest {

    private val context = RuntimeEnvironment.getApplication()
    private val receiver = HeadsetButtonReceiver()

    private fun mediaButtonIntent(keyCode: Int, action: Int): Intent {
        val event = KeyEvent(action, keyCode)
        return Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, event)
        }
    }

    @Test
    fun `play_pause ACTION_DOWN starts RecordingService`() {
        receiver.onReceive(context, mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_DOWN))

        val started = shadowOf(context).nextStartedService
        assertEquals(RecordingService::class.java.name, started.component?.className)
        assertEquals(RecordingService.ACTION_START, started.action)
    }

    @Test
    fun `play_pause ACTION_UP stops RecordingService`() {
        receiver.onReceive(context, mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_UP))

        val started = shadowOf(context).nextStartedService
        assertEquals(RecordingService::class.java.name, started.component?.className)
        assertEquals(RecordingService.ACTION_STOP, started.action)
    }

    @Test
    fun `headsethook ACTION_DOWN starts RecordingService`() {
        receiver.onReceive(context, mediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_DOWN))

        val started = shadowOf(context).nextStartedService
        assertEquals(RecordingService::class.java.name, started.component?.className)
        assertEquals(RecordingService.ACTION_START, started.action)
    }

    @Test
    fun `other keycode is no-op`() {
        receiver.onReceive(context, mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN))

        val started = shadowOf(context).nextStartedService
        assertNull(started)
    }

    @Test
    fun `non media_button action is no-op`() {
        val intent = Intent("android.intent.action.SOME_OTHER_ACTION")
        receiver.onReceive(context, intent)

        val started = shadowOf(context).nextStartedService
        assertNull(started)
    }

    @Test
    fun `missing key event is no-op`() {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        // No EXTRA_KEY_EVENT
        receiver.onReceive(context, intent)

        val started = shadowOf(context).nextStartedService
        assertNull(started)
    }
}
