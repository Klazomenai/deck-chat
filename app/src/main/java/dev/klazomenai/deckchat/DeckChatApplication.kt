package dev.klazomenai.deckchat

import android.app.Application
import android.util.Log

/**
 * Application subclass that installs a global [Thread.UncaughtExceptionHandler].
 *
 * The handler logs the crash via [Log.e] with tag [TAG] then delegates to the
 * system default handler so the standard crash dialog still appears.
 *
 * This is intentionally minimal — it exists solely to make previously-silent
 * crashes visible in logcat. File-based persistence and richer crash reporting
 * are future enhancements.
 */
class DeckChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val TAG = "DeckChat.CRASH"
    }
}
