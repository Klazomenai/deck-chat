package dev.klazomenai.deckchat

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifies [MigrationGate] mutex behavior in the Robolectric environment.
 *
 * In Robolectric, [TinkAeadPrefs] construction fails because the JVM's AndroidKeyStore
 * provider is incomplete — [MigrationGate.migrateIfNeeded] therefore throws immediately.
 * These tests verify that [kotlinx.coroutines.sync.Mutex] releases the lock correctly on
 * exception, preventing deadlocks regardless of the Keystore failure mode.
 *
 * The "migration runs exactly once" invariant (AC-#127.2) is verified end-to-end
 * by [SecureStorageMigrationTest] on a real device/emulator (instrumented tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationGateTest {

    @Test
    fun `concurrent callers both complete without deadlock`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val completionCount = AtomicInteger(0)

        val d1 = async(Dispatchers.IO) {
            try { MigrationGate.migrateIfNeeded(context) } catch (_: Exception) { }
            completionCount.incrementAndGet()
        }
        val d2 = async(Dispatchers.IO) {
            try { MigrationGate.migrateIfNeeded(context) } catch (_: Exception) { }
            completionCount.incrementAndGet()
        }

        withTimeout(5_000L) {
            d1.await()
            d2.await()
        }

        assertEquals("Both callers must complete without deadlock", 2, completionCount.get())
    }

    @Test
    fun `sequential calls release mutex after each failure`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        var callCount = 0
        // Three sequential calls — if the mutex were permanently held after an exception,
        // the second call would deadlock. Test passes if all three complete.
        repeat(3) {
            try { MigrationGate.migrateIfNeeded(context) } catch (_: Exception) { }
            callCount++
        }
        assertEquals("Mutex must be released after each failed call", 3, callCount)
    }
}
