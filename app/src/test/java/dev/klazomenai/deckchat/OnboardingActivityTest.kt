package dev.klazomenai.deckchat

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Covers #211 baseline ACs (cold start, activity-creation budget, step rotation across all
 * four onboarding steps, first-step back-press) and #203 ACs (rotation preserves InProgress
 * without re-firing login; rotation preserves Error UI). Multi-step back-press coverage is
 * deferred to #226.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = OnboardingActivityTest.TestDeckChatApplication::class,
    shadows = [OnboardingActivityTest.ShadowResourceManagerInternal::class],
)
class OnboardingActivityTest {

    /**
     * Bundle key duplicated from [OnboardingActivity.KEY_CURRENT_STEP] (private). If the
     * production constant is renamed, the multi-step rotation tests will fail noisily
     * with a wrong-step assertion, which is the correct failure mode for a contract break.
     */
    private val keyCurrentStep = "current_step"

    private val testDispatcher = UnconfinedTestDispatcher()

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
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Pre-populates the activity's [ViewModelStore][androidx.lifecycle.ViewModelStore] with
     * a test [OnboardingViewModel] whose `loginAction` is controllable. The activity's
     * subsequent `ViewModelProvider(this, OnboardingViewModel.Factory(application))` call
     * finds the pre-loaded VM by key and returns it, so [OnboardingViewModel.Factory.create]
     * (which would build a real [RustMatrixClient]) never runs.
     *
     * Must be called between `Robolectric.buildActivity()` and `controller.create()`.
     */
    private fun preloadTestViewModel(
        activity: OnboardingActivity,
        loginAction: suspend (String, String, String) -> Unit,
    ): OnboardingViewModel {
        val testVm = OnboardingViewModel(
            activity.application,
            (activity.application as DeckChatApplication).secureStorage,
            ioDispatcher = Dispatchers.Unconfined,
            loginAction = loginAction,
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = testVm as T
        }
        ViewModelProvider(activity, factory).get(OnboardingViewModel::class.java)
        return testVm
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
    fun `rotation on STEP_VOICE preserves currentStep`() {
        val state = Bundle().apply { putInt(keyCurrentStep, 1) }
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create(state).start().resume()
        controller.recreate()
        val activity = controller.get()
        val stepVoice = activity.findViewById<View>(R.id.step_voice)
        val stepLogin = activity.findViewById<View>(R.id.step_login)
        assertEquals(View.VISIBLE, stepVoice.visibility)
        assertEquals(View.GONE, stepLogin.visibility)
    }

    @Test
    fun `rotation on STEP_PERMISSIONS preserves currentStep`() {
        val state = Bundle().apply { putInt(keyCurrentStep, 2) }
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create(state).start().resume()
        controller.recreate()
        val activity = controller.get()
        val stepPermissions = activity.findViewById<View>(R.id.step_permissions)
        val stepVoice = activity.findViewById<View>(R.id.step_voice)
        assertEquals(View.VISIBLE, stepPermissions.visibility)
        assertEquals(View.GONE, stepVoice.visibility)
    }

    @Test
    fun `rotation on STEP_BLUETOOTH preserves currentStep`() {
        val state = Bundle().apply { putInt(keyCurrentStep, 3) }
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create(state).start().resume()
        controller.recreate()
        val activity = controller.get()
        val stepBluetooth = activity.findViewById<View>(R.id.step_bluetooth)
        val stepPermissions = activity.findViewById<View>(R.id.step_permissions)
        assertEquals(View.VISIBLE, stepBluetooth.visibility)
        assertEquals(View.GONE, stepPermissions.visibility)
    }

    @Test
    fun `back press on first step finishes activity`() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java)
            .create().start().resume().get()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    // --- #203 in-flight + error rotation ---

    @Test
    fun `rotation during InProgress preserves InProgress UI without re-firing login`() {
        val loginProceed = CompletableDeferred<Unit>()
        val loginCallCount = AtomicInteger(0)

        val controller = Robolectric.buildActivity(OnboardingActivity::class.java)
        val testVm = preloadTestViewModel(controller.get()) { _, _, _ ->
            loginCallCount.incrementAndGet()
            loginProceed.await()
        }
        controller.create().start().resume()

        testVm.validateAndLogin("https://matrix.example.com", "user", "pass", "!room:example.com")
        assertEquals(OnboardingViewModel.LoginState.InProgress, testVm.loginState.value)

        controller.recreate()
        val recreated = controller.get()

        // VM survives recreate via NonConfigurationInstances; state is still InProgress.
        assertEquals(OnboardingViewModel.LoginState.InProgress, testVm.loginState.value)

        // The recreated Activity's new collector picks up the retained InProgress and
        // renders the spinner; the error view stays hidden.
        val progress = recreated.findViewById<View>(R.id.login_progress)
        val error = recreated.findViewById<View>(R.id.login_error)
        assertEquals(View.VISIBLE, progress.visibility)
        assertEquals(View.GONE, error.visibility)

        // Login was fired exactly once across the whole rotation.
        assertEquals(1, loginCallCount.get())

        loginProceed.complete(Unit)
    }

    @Test
    fun `rotation during Error preserves Error UI`() {
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java)
        val testVm = preloadTestViewModel(controller.get()) { _, _, _ ->
            throw RuntimeException("bad credentials")
        }
        controller.create().start().resume()

        testVm.validateAndLogin("https://matrix.example.com", "user", "wrong", "!room:example.com")
        val before = testVm.loginState.value
        assertTrue("Expected Error before rotation, got $before", before is OnboardingViewModel.LoginState.Error)

        controller.recreate()
        val recreated = controller.get()

        // VM + state survive recreate.
        val after = testVm.loginState.value
        assertTrue(after is OnboardingViewModel.LoginState.Error)
        assertEquals("bad credentials", (after as OnboardingViewModel.LoginState.Error).message)

        // The recreated Activity's collector replays Error and renders the error text.
        val errorView = recreated.findViewById<TextView>(R.id.login_error)
        val progress = recreated.findViewById<View>(R.id.login_progress)
        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals("bad credentials", errorView.text.toString())
        assertEquals(View.GONE, progress.visibility)
        assertFalse(recreated.isFinishing)
    }

    // --- OnboardingViewModel.Factory ---

    @Test
    fun `Factory creates ViewModel using TestDeckChatApplication secureStorage`() {
        // TestDeckChatApplication overrides secureStorage with PlaintextTokenEncryptor,
        // so Factory.create() must succeed without Android Keystore.
        val vm = OnboardingViewModel.Factory(RuntimeEnvironment.getApplication())
            .create(OnboardingViewModel::class.java)
        assertEquals(OnboardingViewModel.LoginState.Idle, vm.loginState.value)
    }

    @Test
    fun `Factory throws for unrelated ViewModel class`() {
        val factory = OnboardingViewModel.Factory(RuntimeEnvironment.getApplication())
        try {
            factory.create(MainViewModel::class.java)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unknown ViewModel class"))
        }
    }
}
