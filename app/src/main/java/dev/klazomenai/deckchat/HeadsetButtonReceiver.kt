package dev.klazomenai.deckchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/**
 * Receives ACTION_MEDIA_BUTTON broadcasts from Bluetooth headsets.
 *
 * ACTION_DOWN on KEYCODE_HEADSETHOOK or KEYCODE_MEDIA_PLAY_PAUSE starts
 * RecordingService. ACTION_UP stops it. All other keycodes are ignored.
 *
 * Double-press is not handled for MVP — each press/release is independent.
 * See issue #17 for MediaSession spike (future replacement for this receiver).
 */
class HeadsetButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val event = extractKeyEvent(intent) ?: return

        if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK &&
            event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return
        }

        val serviceIntent = Intent(context, RecordingService::class.java)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                serviceIntent.action = RecordingService.ACTION_START
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: IllegalStateException) {
                    // Foreground service start may be blocked by background restrictions
                    // on Android 12+ (ForegroundServiceStartNotAllowedException).
                }
            }
            KeyEvent.ACTION_UP -> {
                serviceIntent.action = RecordingService.ACTION_STOP
                try {
                    context.startService(serviceIntent)
                } catch (_: IllegalStateException) {
                    // App may be in background if process was killed between DOWN/UP.
                    // Service isn't running anyway — safe to ignore.
                }
            }
        }
    }

    private fun extractKeyEvent(intent: Intent): KeyEvent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }
}
