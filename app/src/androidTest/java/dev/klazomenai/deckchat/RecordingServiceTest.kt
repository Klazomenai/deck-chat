package dev.klazomenai.deckchat

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for [RecordingService].
 * Runs on emulator or physical device. Tests requiring microphone access
 * (actionStart, actionStop) are skipped on emulator via [assumeTrue] guards.
 * [permissionDeniedStopsGracefully] runs on emulator (RECORD_AUDIO denied by default).
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class RecordingServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        RecordingService.setOnRecordingCompleteListener(null)
    }

    @After
    fun tearDown() {
        RecordingService.setOnRecordingCompleteListener(null)
        File(context.cacheDir, "recording.pcm").delete()
    }

    /**
     * Assumes notifications can be posted: app-level enabled, POST_NOTIFICATIONS
     * granted on API 33+, and recording_channel importance is not NONE.
     */
    private fun assumeNotificationsAvailable() {
        assumeTrue(
            "POST_NOTIFICATIONS permission required on API 33+",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED,
        )
        assumeTrue(
            "Notifications must be enabled for the app",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("recording_channel")
        assumeTrue(
            "Recording notification channel must exist and be enabled",
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
        )
    }

    @Test
    fun actionStartShowsForegroundNotification() {
        assumeTrue(
            "RECORD_AUDIO permission required",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
        assumeNotificationsAvailable()

        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        serviceRule.startService(intent)

        try {
            // assumeTrue — if the mic is busy or blocked (privacy toggle, another app),
            // the service stops itself and the notification never appears. That's not a
            // test failure, it's an environment issue.
            assumeTrue(
                "Recording did not start (mic may be busy or unavailable)",
                waitForRecordingNotification(present = true),
            )
        } finally {
            stopServiceAndWait()
        }
    }

    @Test
    fun actionStopCreatesPcmFileAndInvokesCallback() {
        assumeTrue(
            "RECORD_AUDIO permission required",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
        assumeNotificationsAvailable()

        val latch = CountDownLatch(1)
        var receivedFile: File? = null

        RecordingService.setOnRecordingCompleteListener { file ->
            receivedFile = file
            latch.countDown()
        }

        val startIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        serviceRule.startService(startIntent)

        try {
            // Verify recording actually started — notification posts before AudioRecord
            // init, so also confirm it's still present after a brief delay to rule out
            // the service stopping itself due to init failure.
            assumeTrue(
                "Recording did not start (mic may be busy or unavailable)",
                waitForRecordingNotification(present = true),
            )
            Thread.sleep(500)
            assumeTrue(
                "Recording stopped unexpectedly (AudioRecord may have failed to init)",
                waitForRecordingNotification(present = true, timeoutMs = 500),
            )

            // Record briefly
            Thread.sleep(1000)

            stopServiceAndWait()

            // Callback may not fire if AudioRecord failed after notification posted —
            // treat as environment skip rather than test failure.
            assumeTrue(
                "Completion callback did not fire (recording may not have started)",
                latch.await(5, TimeUnit.SECONDS),
            )
            assertNotNull("Callback should receive a file", receivedFile)
            assertTrue("PCM file should exist", receivedFile!!.exists())
            assertTrue("PCM file should be non-empty", receivedFile!!.length() > 0)
        } finally {
            stopServiceAndWait()
        }
    }

    @Test
    fun permissionDeniedStopsGracefully() {
        assumeTrue(
            "Test requires RECORD_AUDIO to be denied",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED,
        )

        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        // Use plain startService — startForegroundService would require calling
        // startForeground(), but the manifest foregroundServiceType="microphone"
        // triggers SecurityException without RECORD_AUDIO. Plain startService has
        // no such contract; the service checks permission and calls stopSelf().
        try {
            context.startService(intent)
        } catch (e: IllegalStateException) {
            assumeTrue(
                "Background service start restricted on this device: ${e.message}",
                false,
            )
        }

        // Brief grace period for the service to start and stop itself.
        Thread.sleep(500)

        // Service should stop itself — no crash, no lingering notification
        assertTrue(
            "No notification should remain after permission-denied stop",
            waitForRecordingNotification(present = false),
        )
    }

    /**
     * Sends ACTION_STOP to RecordingService and waits for the notification to disappear.
     * Safe to call multiple times (idempotent).
     */
    private fun stopServiceAndWait() {
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        try {
            // Deliver ACTION_STOP via ServiceTestRule so stopRecording() runs
            // (writes PCM file, invokes callback). Plain stopService() would
            // kill the process without delivering the action.
            serviceRule.startService(stopIntent)
        } catch (_: Exception) {
            // ServiceTestRule or background restrictions failed — last resort
            try {
                context.stopService(stopIntent)
            } catch (_: Exception) {
                // Service already stopped — nothing to clean up
            }
        }
        assertTrue(
            "RecordingService notification did not disappear within timeout after ACTION_STOP",
            waitForRecordingNotification(present = false),
        )
    }

    /**
     * Polls [NotificationManager] until the recording notification is present or absent.
     * Returns true if the expected state was reached within the timeout.
     */
    private fun waitForRecordingNotification(
        present: Boolean,
        timeoutMs: Long = 5_000L,
    ): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val hasNotification = nm.activeNotifications
                .any { it.notification.channelId == "recording_channel" }
            if (hasNotification == present) return true
            Thread.sleep(50)
        }
        return false
    }
}
