package dev.klazomenai.deckchat

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    private lateinit var storage: SecureStorage
    private lateinit var application: Application
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        val prefs = application.getSharedPreferences("test_onboarding", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        storage = SecureStorage(prefs, SecureStorage.PlaintextTokenEncryptor())
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        loginAction: suspend (String, String, String) -> Unit = { _, _, _ -> },
    ) = OnboardingViewModel(application, storage, ioDispatcher = testDispatcher, loginAction = loginAction)

    @Test
    fun `initial loginState is Idle`() = runTest {
        val vm = viewModel()
        assertEquals(OnboardingViewModel.LoginState.Idle, vm.loginState.value)
    }

    @Test
    fun `validateAndLogin transitions to InProgress then Success`() = runTest {
        // Use StandardTestDispatcher for io so withContext() suspends for real, giving the
        // StateFlow collector a chance to process InProgress before Success is emitted.
        // With UnconfinedTestDispatcher on both sides, withContext is a no-op and StateFlow
        // conflation hides InProgress before the collector wakes up.
        val vm = OnboardingViewModel(
            application,
            storage,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            loginAction = { _, _, _ -> /* no-op — instant success */ },
        )

        val states = mutableListOf<OnboardingViewModel.LoginState>()
        val job = launch { vm.loginState.collect { states.add(it) } }

        vm.validateAndLogin("https://matrix.example.com", "user", "pass", "!room:example.com")
        advanceUntilIdle()
        job.cancel()

        val inProgressIdx = states.indexOf(OnboardingViewModel.LoginState.InProgress)
        val successIdx = states.lastIndexOf(OnboardingViewModel.LoginState.Success)
        assertTrue("InProgress must appear before Success in state sequence",
            inProgressIdx in 0 until successIdx)
        assertEquals(OnboardingViewModel.LoginState.Success, states.last())
        assertEquals("!room:example.com", storage.roomId)
    }

    @Test
    fun `validateAndLogin transitions to Error on login failure`() = runTest {
        val vm = viewModel(loginAction = { _, _, _ -> throw RuntimeException("bad credentials") })

        vm.validateAndLogin("https://matrix.example.com", "user", "wrong", "!room:example.com")

        val state = vm.loginState.value
        assertTrue(state is OnboardingViewModel.LoginState.Error)
        assertEquals("bad credentials", (state as OnboardingViewModel.LoginState.Error).message)
    }

    @Test
    fun `loginState re-emits last value to new collector (rotation semantics)`() = runTest {
        val vm = viewModel(loginAction = { _, _, _ -> throw RuntimeException("oops") })
        vm.validateAndLogin("https://matrix.example.com", "user", "pass", "!room:example.com")

        // A new collector (created after Activity recreation on rotation) must immediately
        // receive the retained Error state — StateFlow guarantees replay of last value.
        val seenByNewCollector = vm.loginState.first()

        assertTrue(seenByNewCollector is OnboardingViewModel.LoginState.Error)
    }

    @Test
    fun `second validateAndLogin call while InProgress is dropped`() = runTest {
        val loginProceed = CompletableDeferred<Unit>()
        val vm = OnboardingViewModel(
            application,
            storage,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            loginAction = { _, _, _ -> loginProceed.await() },
        )

        vm.validateAndLogin("https://matrix.example.com", "user", "pass", "!room:example.com")
        advanceUntilIdle()  // drives coroutine into loginProceed.await(); state = InProgress

        vm.validateAndLogin("https://matrix.example.com", "user2", "pass2", "!room2:example.com")
        assertEquals(OnboardingViewModel.LoginState.InProgress, vm.loginState.value)

        loginProceed.complete(Unit)
        advanceUntilIdle()

        assertEquals(OnboardingViewModel.LoginState.Success, vm.loginState.value)
        assertEquals("!room:example.com", storage.roomId)  // first call's roomId, not second
    }
}
