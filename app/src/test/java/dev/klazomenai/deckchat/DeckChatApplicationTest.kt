package dev.klazomenai.deckchat

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeckChatApplicationTest {

    @Test
    fun `application is DeckChatApplication instance`() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(app is DeckChatApplication)
    }

    @Test
    fun `uncaught exception handler is CrashLoggingHandler after onCreate`() {
        // Ensure application has been created (triggers onCreate → installCrashHandler)
        RuntimeEnvironment.getApplication()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue(
            "Expected CrashLoggingHandler, got ${handler?.javaClass?.name}",
            handler is DeckChatApplication.CrashLoggingHandler,
        )
    }

    @Test
    fun `installCrashHandler is idempotent`() {
        RuntimeEnvironment.getApplication()
        val handlerBefore = Thread.getDefaultUncaughtExceptionHandler()
        // Calling onCreate again should not wrap the handler a second time
        (RuntimeEnvironment.getApplication() as DeckChatApplication).onCreate()
        val handlerAfter = Thread.getDefaultUncaughtExceptionHandler()
        assertSame(
            "Handler should not be re-wrapped on second onCreate",
            handlerBefore,
            handlerAfter,
        )
    }
}
