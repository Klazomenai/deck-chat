package dev.klazomenai.deckchat

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric baseline for [OnboardingActivity] — step navigation and state restoration.
 *
 * Uses [TestDeckChatApplication] to inject a plain-SharedPreferences [SecureStorage],
 * avoiding the Android Keystore dependency that [TinkAeadPrefs] requires at construction time.
 *
 * Robolectric compatibility workarounds for AppCompat 1.7.x + MaterialComponents:
 *
 * 1. [ShadowResourceManagerInternal]: suppresses AppCompat's vector-drawable setup probe
 *    (checkVectorDrawableSetup), which fails on abc_vector_test from a ContextThemeWrapper
 *    in Robolectric's binary-resource loader.
 *
 * 2. [registerActivityTheme]: Robolectric 4.16.1 + SDK 34 has a bug where
 *    PackageParser.generatePackageInfo returns an empty activities array, so
 *    packageInfos never receives ActivityInfo.theme from the binary manifest.
 *    ShadowActivity.callAttach() then calls addActivityIfNotPresent() which inserts
 *    a blank ActivityInfo (theme=0), meaning setTheme() is never called, and
 *    AppCompatDelegateImpl.createSubDecor() throws "You need to use a Theme.AppCompat theme".
 *    Fix: call shadowOf(pm).addOrUpdateActivity() in @Before to register the activity
 *    with theme=R.style.Theme_DeckChat before any buildActivity() call.
 *
 * Covers #211 baseline ACs and the rotation/error state ACs from #203.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = OnboardingActivityTest.TestDeckChatApplication::class,
    shadows = [OnboardingActivityTest.ShadowResourceManagerInternal::class],
)
class OnboardingActivityTest {

    class TestDeckChatApplication : DeckChatApplication() {
        override val secureStorage: SecureStorage by lazy {
            SecureStorage(
                getSharedPreferences("test_onboarding", Context.MODE_PRIVATE),
                SecureStorage.PlaintextTokenEncryptor(),
            )
        }
    }

    @Implements(className = "androidx.appcompat.widget.ResourceManagerInternal", looseSignatures = true)
    class ShadowResourceManagerInternal {
        @Implementation
        fun checkVectorDrawableSetup(context: Any?) {
            // no-op — suppress AppCompat 1.7.x vector-drawable probe in unit tests
        }
    }

    /**
     * Robolectric 4.16.1 + SDK 34 bug: PackageParser.generatePackageInfo returns an empty
     * activities array, so the binary manifest theme is never stored in packageInfos.
     * Register the activity with its declared theme before each test so ShadowActivity
     * finds a non-zero themeResource and calls setTheme() during attach.
     */
    @Before
    fun registerActivityTheme() {
        val app = RuntimeEnvironment.getApplication()
        val activityInfo = ActivityInfo().apply {
            packageName = app.packageName
            name = OnboardingActivity::class.java.name
            theme = R.style.Theme_DeckChat
        }
        val shadowPm = shadowOf(app.packageManager)
        shadowPm.addOrUpdateActivity(activityInfo)

        // Verify registration took effect before any buildActivity() call
        val rawPkg = shadowPm.getInternalMutablePackageInfo(app.packageName)
        val stored = rawPkg?.activities?.firstOrNull { it.name == OnboardingActivity::class.java.name }
        assertEquals(
            "@Before: activity not found in packageInfos after addOrUpdateActivity",
            OnboardingActivity::class.java.name, stored?.name,
        )
        assertEquals(
            "@Before: theme not set after addOrUpdateActivity (stored.theme)",
            R.style.Theme_DeckChat, stored?.theme ?: 0,
        )
    }

    // --- #211 baseline ---

    @Test
    fun `cold start lands on STEP_LOGIN`() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create().start().resume().get()
        val stepLogin = activity.findViewById<View>(R.id.step_login)
        assertEquals(View.VISIBLE, stepLogin.visibility)
    }

    @Test
    fun `cold start STEP_LOGIN visible within 500ms`() {
        val start = System.nanoTime()
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create().start().resume().get()
        val stepLogin = activity.findViewById<View>(R.id.step_login)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(View.VISIBLE, stepLogin.visibility)
        assertTrue("onCreate exceeded 500ms budget (${elapsedMs}ms)", elapsedMs < 500)
    }

    @Test
    fun `rotation on STEP_LOGIN preserves currentStep`() {
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create().start().resume()
        controller.recreate()
        val activity = controller.get()
        val stepLogin = activity.findViewById<View>(R.id.step_login)
        assertEquals(View.VISIBLE, stepLogin.visibility)
    }

    @Test
    fun `back press on first step finishes activity`() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create().start().resume().get()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }
}
