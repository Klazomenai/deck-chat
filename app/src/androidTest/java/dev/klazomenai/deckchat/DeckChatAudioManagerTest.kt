package dev.klazomenai.deckchat

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [DeckChatAudioManager].
 * Requires a physical device with Bluetooth for full coverage.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * Skipped on emulators when Bluetooth hardware or permissions are unavailable.
 */
@RunWith(AndroidJUnit4::class)
class DeckChatAudioManagerTest {

    private lateinit var context: Context
    private lateinit var manager: DeckChatAudioManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = DeckChatAudioManager(context)
    }

    @Test
    fun routeToBluetoothReturnsTrueWhenScoDevicePresent() {
        assumeTrue(
            "API 31+ required for deterministic SCO routing behavior",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
        assumeTrue(
            "BLUETOOTH_CONNECT permission required",
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED,
        )

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val hasSco = audioManager.availableCommunicationDevices
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

        assumeTrue("Bluetooth SCO device must be connected", hasSco)

        val result = manager.routeToBluetooth()
        assertTrue("routeToBluetooth should return true with SCO device", result)

        manager.clearRoute()
    }

    @Test
    fun routeToBluetoothReturnsFalseWhenNoScoDevice() {
        // Only testable on API 31+ where setCommunicationDevice returns false
        // for no device. Legacy path always returns true.
        assumeTrue(
            "API 31+ required for deterministic false return",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
        assumeTrue(
            "BLUETOOTH_CONNECT permission required",
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED,
        )

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val hasSco = audioManager.availableCommunicationDevices
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

        assumeTrue("No Bluetooth SCO device must be connected", !hasSco)

        val result = manager.routeToBluetooth()
        assertFalse("routeToBluetooth should return false without SCO device", result)
    }

    @Test
    fun clearRouteDoesNotThrow() {
        // On API 31+, clearCommunicationDevice requires BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assumeTrue(
                "BLUETOOTH_CONNECT permission required on API 31+",
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED,
            )
        }
        // clearRoute should be safe to call even if no route was established
        manager.clearRoute()
    }
}
