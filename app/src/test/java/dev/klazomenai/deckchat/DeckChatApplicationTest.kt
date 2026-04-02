package dev.klazomenai.deckchat

import org.junit.Assert.assertNotNull
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
    fun `uncaught exception handler is installed after onCreate`() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)
        // The handler is our wrapper, not the raw system handler.
        assertTrue(
            handler!!.javaClass.name != "com.android.internal.os.RuntimeInit\$KillApplicationHandler",
        )
    }
}
