package dev.klazomenai.deckchat

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [RecordingService].
 * Requires a device or emulator with microphone access.
 *
 * Run with: ./gradlew connectedAndroidTest
 * NOT run in CI (no emulator).
 */
@RunWith(AndroidJUnit4::class)
class RecordingServiceTest {

    // TODO: Implement when device testing infrastructure is available.
    // Tests planned:
    // - onStartCommand(ACTION_START) → AudioRecord starts, foreground notification shown
    // - onStartCommand(ACTION_STOP) → PCM file created in cacheDir, listener callback invoked
    // - Permission denied → service stops gracefully

    @Test
    fun contextIsAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context)
    }
}
