package dev.klazomenai.deckchat

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Routes audio to/from Bluetooth SCO headset.
 *
 * API 31+: uses [AudioManager.setCommunicationDevice] with
 * [AudioDeviceInfo.TYPE_BLUETOOTH_SCO].
 *
 * API ≤30: uses legacy [AudioManager.startBluetoothSco] path.
 *
 * Call [routeToBluetooth] before starting audio capture/playback,
 * and [clearRoute] when finished.
 *
 * Requires BLUETOOTH_CONNECT permission (API 31+) to be granted at
 * runtime before calling [routeToBluetooth] — returns false if no
 * Bluetooth SCO device is available.
 */
class DeckChatAudioManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Attempts to route audio to a Bluetooth SCO headset.
     * Returns true if routing was requested. On API 31+ this confirms a
     * device was found; on API ≤30 the legacy SCO path is best-effort
     * and always returns true (actual connection is asynchronous).
     */
    fun routeToBluetooth(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val btDevice = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                if (btDevice != null) {
                    audioManager.setCommunicationDevice(btDevice)
                } else {
                    false
                }
            } catch (_: SecurityException) {
                // BLUETOOTH_CONNECT not granted
                false
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            true
        }
    }

    /**
     * Clears Bluetooth audio routing, returning to default output.
     */
    fun clearRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
        }
    }
}
