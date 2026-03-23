package dev.klazomenai.deckchat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service that captures 16kHz mono 16-bit PCM audio.
 *
 * Started by HeadsetButtonReceiver on ACTION_DOWN, stopped on ACTION_UP.
 * On stop, writes raw PCM to cacheDir/recording.pcm and notifies the
 * registered [OnRecordingCompleteListener].
 *
 * Audio is raw 16-bit little-endian PCM at 16 kHz mono — the format
 * expected by SherpaOnnxSttEngine.
 */
class RecordingService : Service() {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    private val audioBuffer = mutableListOf<ByteArray>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isRecording) return

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize <= 0) {
            stopSelf()
            return
        }

        // RECORD_AUDIO permission is requested by the Activity at runtime.
        // This service is only started from HeadsetButtonReceiver after the
        // user has granted the permission.
        @SuppressLint("MissingPermission")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            stopSelf()
            return
        }

        isRecording = true
        audioBuffer.clear()
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    audioBuffer.add(buffer.copyOf(read))
                }
            }
        }.also { it.start() }
    }

    private fun stopRecording() {
        if (!isRecording) {
            stopSelf()
            return
        }

        isRecording = false
        // Stop AudioRecord first to unblock the read() call in the recording thread,
        // then join the thread. Joining before stop would deadlock until timeout.
        audioRecord?.stop()
        recordingThread?.join(THREAD_JOIN_TIMEOUT_MS)
        recordingThread = null
        audioRecord?.release()
        audioRecord = null

        val outputFile = writeBufferToFile()
        listener?.onRecordingComplete(outputFile)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun writeBufferToFile(): File {
        val outputFile = File(cacheDir, RECORDING_FILENAME)
        FileOutputStream(outputFile).use { out ->
            for (chunk in audioBuffer) {
                out.write(chunk)
            }
        }
        audioBuffer.clear()
        return outputFile
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Audio recording in progress"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DeckChat")
            .setContentText("Recording...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (isRecording) {
            isRecording = false
            recordingThread?.join(THREAD_JOIN_TIMEOUT_MS)
            audioRecord?.stop()
            audioRecord?.release()
        }
        super.onDestroy()
    }

    /**
     * Callback for when recording completes and the PCM file is ready.
     * Set via [setOnRecordingCompleteListener] from the hosting Activity.
     */
    fun interface OnRecordingCompleteListener {
        fun onRecordingComplete(audioFile: File)
    }

    companion object {
        const val ACTION_START = "dev.klazomenai.deckchat.ACTION_START_RECORDING"
        const val ACTION_STOP = "dev.klazomenai.deckchat.ACTION_STOP_RECORDING"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "recording_channel"
        private const val SAMPLE_RATE = 16000
        private const val RECORDING_FILENAME = "recording.pcm"
        private const val THREAD_JOIN_TIMEOUT_MS = 2000L

        /**
         * App-wide listener. Set from Activity/Application before service starts.
         * This is a simple MVP pattern — see issue #17 for MediaSession spike.
         */
        @Volatile
        var listener: OnRecordingCompleteListener? = null

        fun setOnRecordingCompleteListener(l: OnRecordingCompleteListener?) {
            listener = l
        }
    }
}
