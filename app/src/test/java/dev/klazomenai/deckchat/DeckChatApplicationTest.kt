package dev.klazomenai.deckchat

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = DeckChatApplication::class)
class DeckChatApplicationTest {

    private var handlerBefore: Thread.UncaughtExceptionHandler? = null

    @Before
    fun saveHandler() {
        handlerBefore = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(handlerBefore)
    }

    @Test
    fun `application is DeckChatApplication instance`() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(app is DeckChatApplication)
    }

    @Test
    fun `uncaught exception handler is CrashLoggingHandler after onCreate`() {
        RuntimeEnvironment.getApplication()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue(
            "Expected CrashLoggingHandler, got ${handler?.javaClass?.name}",
            handler is DeckChatApplication.CrashLoggingHandler,
        )
    }

    @Test
    fun `installCrashHandler is idempotent`() {
        val app = RuntimeEnvironment.getApplication() as DeckChatApplication
        val handlerAfterFirstInstall = Thread.getDefaultUncaughtExceptionHandler()
        // Invoke installCrashHandler directly to test idempotency without
        // calling onCreate() a second time (which may have unrelated side effects)
        val method = DeckChatApplication::class.java.getDeclaredMethod("installCrashHandler")
        method.isAccessible = true
        method.invoke(app)
        val handlerAfterSecondInstall = Thread.getDefaultUncaughtExceptionHandler()
        assertSame(
            "Handler should not be re-wrapped on second install",
            handlerAfterFirstInstall,
            handlerAfterSecondInstall,
        )
    }
}
