package dev.klazomenai.deckchat

import android.app.Application
import android.util.Log
import android.os.Process
import kotlinx.coroutines.runBlocking

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
open class DeckChatApplication : Application() {

    /**
     * App-wide singleton SecureStorage. Lazy so the Tink keyset load + Keystore round-trip
     * happens once and is shared across all callers. Call-site refactor (rewriting the five
     * dispersed construction sites to use this property) lands in the sibling PR for #226 —
     * this PR adds the property only.
     */
    open val secureStorage: SecureStorage by lazy { SecureStorage(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        // One-time migration from EncryptedSharedPreferences to TinkAeadPrefs.
        // Must complete before any SecureStorage access — runBlocking is intentional here
        // (one-time, before any Activity starts, before any other coroutine context exists).
        // Failure is caught and logged per B3: flaky or unavailable Keystore results in
        // the user re-authenticating rather than a hard crash. Robolectric unit tests also
        // hit this path; they have no AndroidKeyStore provider and throw here.
        try {
            runBlocking { MigrationGate.migrateIfNeeded(this@DeckChatApplication) }
        } catch (e: Exception) {
            // Only TinkAeadPrefs construction failure reaches here (internal MigrationGate errors
            // are caught and handled inside migrateIfNeeded). If Tink key material is unavailable,
            // every subsequent SecureStorage access will also fail — the user will be prompted to
            // re-authenticate when the first credential read throws rather than at a hard crash.
            // The broad catch is also required for Robolectric: the JVM has no AndroidKeyStore
            // provider so TinkAeadPrefs construction always throws in unit tests.
            Log.e("DeckChat.Migration", "Tink key material unavailable — credential access will fail on first use", e)
        }
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
                Process.killProcess(Process.myPid())
            }
        }
    }

    companion object {
        const val TAG = "DeckChat.CRASH"
    }
}
