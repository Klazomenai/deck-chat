package dev.klazomenai.deckchat

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [SettingsActivity].
 * Runs on emulator or physical device.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    private lateinit var storage: SecureStorage

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = SecureStorage(context)
        storage.homeserverUrl = null
        storage.clearSession()
    }

    @After
    fun tearDown() {
        storage.homeserverUrl = null
        storage.clearSession()
    }

    @Test
    fun homeserverUrlPersistsAcrossActivityRecreate() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.homeserver_url_input))
                .perform(clearText(), typeText("https://matrix.example.com"), closeSoftKeyboard())
            onView(withId(R.id.save_button))
                .perform(click())

            scenario.recreate()

            onView(withId(R.id.homeserver_url_input))
                .check(matches(withText("https://matrix.example.com")))
        }
    }

    @Test
    fun clearSessionRemovesTokensButKeepsUrl() {
        storage.homeserverUrl = "https://matrix.example.com"
        storage.userId = "@user:example.com"
        storage.accessToken = "token123"

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.clear_session_button))
                .perform(click())

            assertEquals("https://matrix.example.com", storage.homeserverUrl)
            assertNull(storage.userId)
            assertNull(storage.accessToken)
            assertFalse(storage.hasSession())
        }
    }

    @Test
    fun invalidUrlShowsError() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            // Empty URL
            onView(withId(R.id.homeserver_url_input))
                .perform(clearText(), closeSoftKeyboard())
            onView(withId(R.id.save_button))
                .perform(click())
            onView(withId(R.id.homeserver_url_input))
                .check(matches(hasErrorText("Homeserver URL is required")))

            // Missing https
            onView(withId(R.id.homeserver_url_input))
                .perform(clearText(), typeText("http://matrix.example.com"), closeSoftKeyboard())
            onView(withId(R.id.save_button))
                .perform(click())
            onView(withId(R.id.homeserver_url_input))
                .check(matches(hasErrorText("URL must start with https://")))

            // No hostname
            onView(withId(R.id.homeserver_url_input))
                .perform(clearText(), typeText("https://"), closeSoftKeyboard())
            onView(withId(R.id.save_button))
                .perform(click())
            onView(withId(R.id.homeserver_url_input))
                .check(matches(hasErrorText("URL must include a hostname")))
        }
    }
}
