package dev.klazomenai.deckchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [SettingsActivity].
 * Requires a device or emulator.
 *
 * Run with: ./gradlew connectedAndroidTest
 * NOT run in CI (no emulator).
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    // TODO: Implement when device testing infrastructure is available.
    // Tests planned:
    // - Enter homeserver URL, recreate Activity, verify URL persists
    // - Clear session button removes tokens but keeps URL
    // - Invalid URL shows error

    @Test
    fun instrumentationTargetsCorrectPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.klazomenai.deckchat", context.packageName)
    }
}
