package dev.klazomenai.deckchat

import android.app.Application
import android.util.Log
import kotlin.system.exitProcess

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
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is CrashLoggingHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashLoggingHandler(current))
    }

    /**
     * Named handler class so [installCrashHandler] can detect double-installation
     * via an `instanceof` check. Delegates to [delegate] after logging; if [delegate]
     * is null (rare — test/embedded runtimes), terminates the process to avoid leaving
     * it in an undefined state.
     */
    internal class CrashLoggingHandler(
        private val delegate: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            if (delegate != null) {
                delegate.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }
    }

    companion object {
        const val TAG = "DeckChat.CRASH"
    }
}
