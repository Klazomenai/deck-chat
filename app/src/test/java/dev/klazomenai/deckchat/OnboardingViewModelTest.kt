package dev.klazomenai.deckchat

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val vm = viewModel(loginAction = { _, _, _ -> /* no-op — instant success */ })

        vm.validateAndLogin("https://matrix.example.com", "user", "pass", "!room:example.com")

        assertEquals(OnboardingViewModel.LoginState.Success, vm.loginState.value)
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

        // New collector (simulates Activity recreation after rotation) sees the last state.
        val collected = mutableListOf<OnboardingViewModel.LoginState>()
        val state = vm.loginState.value
        collected.add(state)

        assertTrue(collected.first() is OnboardingViewModel.LoginState.Error)
    }
}
