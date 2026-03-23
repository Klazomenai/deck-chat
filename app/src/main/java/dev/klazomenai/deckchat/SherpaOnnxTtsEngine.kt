package dev.klazomenai.deckchat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * On-device TTS engine backed by Sherpa-ONNX + Piper VITS voice models.
 *
 * Each crew member maps to a voice model directory under assets/tts/.
 * Models are gitignored and downloaded via the downloadTtsModels Gradle
 * task or scripts/download-tts-models.sh.
 *
 * Audio plays through the default system output. Bluetooth headset
 * routing is handled separately by DeckChatAudioManager (issue #5).
 *
 * JNI library is loaded via the companion object; this class must NOT be
 * instantiated in JVM unit tests. Use MockTtsEngine instead.
 */
class SherpaOnnxTtsEngine(private val context: Context) : TtsEngine {

    private val ttsInstances = mutableMapOf<String, OfflineTts>()

    private fun copyVoiceToDisk(voiceDir: String): File {
        val destDir = File(context.filesDir, "tts/$voiceDir")
        val modelFile = File(destDir, "${voiceDir.removePrefix("vits-piper-")}.onnx")
        val tokensFile = File(destDir, "tokens.txt")

        if (destDir.exists() && modelFile.exists() && modelFile.length() > 0
            && tokensFile.exists() && tokensFile.length() > 0) {
            return destDir
        }

        destDir.mkdirs()

        val assetPath = "tts/$voiceDir"
        val assetFiles = context.assets.list(assetPath)
        require(!assetFiles.isNullOrEmpty()) {
            "TTS voice assets not found in $assetPath — run ./gradlew downloadTtsModels first"
        }

        copyAssetsRecursive(assetPath, destDir)
        return destDir
    }

    private fun copyAssetsRecursive(assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            // Leaf file — copy it
            val dest = File(destDir, "")
            context.assets.open(assetPath).use { src ->
                FileOutputStream(dest).use { out -> src.copyTo(out) }
            }
        } else {
            // Directory — recurse
            for (child in children) {
                val childAssetPath = "$assetPath/$child"
                val childDest = File(destDir, child)
                val grandchildren = context.assets.list(childAssetPath)
                if (grandchildren != null && grandchildren.isNotEmpty()) {
                    childDest.mkdirs()
                    copyAssetsRecursive(childAssetPath, childDest)
                } else {
                    context.assets.open(childAssetPath).use { src ->
                        FileOutputStream(childDest).use { out -> src.copyTo(out) }
                    }
                }
            }
        }
    }

    private fun getOrCreateTts(crewName: String): OfflineTts {
        return ttsInstances.getOrPut(crewName) {
            val voiceDir = CREW_VOICES[crewName]
                ?: throw IllegalArgumentException("Unknown crew member: $crewName. Known: ${CREW_VOICES.keys}")
            val ttsDir = copyVoiceToDisk(voiceDir)
            val modelName = voiceDir.removePrefix("vits-piper-")

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "${ttsDir.absolutePath}/$modelName.onnx",
                        tokens = "${ttsDir.absolutePath}/tokens.txt",
                        dataDir = "${ttsDir.absolutePath}/espeak-ng-data",
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
            )
            OfflineTts(config = config)
        }
    }

    override suspend fun speak(crewName: String, text: String) = withContext(Dispatchers.IO) {
        val tts = getOrCreateTts(crewName)
        val announcement = "${CREW_ANNOUNCEMENTS[crewName] ?: crewName}: $text"
        val audio = tts.generate(text = announcement, sid = 0, speed = 1.0f)

        if (audio.samples.isEmpty()) return@withContext

        val bufSize = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(audio.sampleRate)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        try {
            track.play()
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
        } finally {
            track.stop()
            track.release()
        }
    }

    override fun close() {
        ttsInstances.values.forEach { it.release() }
        ttsInstances.clear()
    }

    companion object {
        // Maps crew name → voice model directory under assets/tts/
        private val CREW_VOICES = mapOf(
            "maren" to "vits-piper-en_GB-cori-high",
            "crest" to "vits-piper-en_US-lessac-high",
        )

        // Maps crew name → spoken announcement prefix
        private val CREW_ANNOUNCEMENTS = mapOf(
            "maren" to "Maren",
            "crest" to "Crest",
        )

        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
