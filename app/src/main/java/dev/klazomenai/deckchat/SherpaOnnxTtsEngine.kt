package dev.klazomenai.deckchat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
 * routing is handled separately by the audio routing layer (issue #5).
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
        val espeakDataDir = File(destDir, "espeak-ng-data")

        if (destDir.exists()
            && modelFile.exists() && modelFile.length() > 0
            && tokensFile.exists() && tokensFile.length() > 0
            && espeakDataDir.exists() && espeakDataDir.isDirectory
            && (espeakDataDir.listFiles()?.isNotEmpty() == true)) {
            return destDir
        }

        destDir.mkdirs()

        val assetPath = "tts/$voiceDir"
        val assetFiles = context.assets.list(assetPath)
        val expectedModel = "${voiceDir.removePrefix("vits-piper-")}.onnx"
        require(!assetFiles.isNullOrEmpty()) {
            "TTS voice assets not found in $assetPath — run ./gradlew downloadTtsModels first"
        }
        require(assetFiles.contains(expectedModel)) {
            "TTS model $expectedModel not found in $assetPath — run ./gradlew downloadTtsModels first"
        }
        require(assetFiles.contains("tokens.txt")) {
            "TTS tokens.txt not found in $assetPath — run ./gradlew downloadTtsModels first"
        }
        require(assetFiles.contains("espeak-ng-data")) {
            "TTS espeak-ng-data not found in $assetPath — run ./gradlew downloadTtsModels first"
        }
        val espeakAssets = context.assets.list("$assetPath/espeak-ng-data")
        require(!espeakAssets.isNullOrEmpty()) {
            "TTS espeak-ng-data is empty in $assetPath — run ./gradlew downloadTtsModels first"
        }

        copyAssetsRecursive(assetPath, destDir)
        return destDir
    }

    private fun copyAssetsRecursive(assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath) ?: return
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

    private fun getOrCreateTts(crewName: String): OfflineTts {
        val crew = CrewRegistry.lookup(crewName)
        return synchronized(ttsInstances) {
            ttsInstances.getOrPut(crew.name) {
                val ttsDir = copyVoiceToDisk(crew.voiceDir)
                val modelName = crew.voiceDir.removePrefix("vits-piper-")

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
    }

    override suspend fun speak(crewName: String, text: String) = withContext(Dispatchers.IO) {
        val crew = CrewRegistry.lookup(crewName)
        val tts = getOrCreateTts(crewName)
        val announcement = "${crew.displayName}: $text"
        val audio = tts.generate(text = announcement, sid = 0, speed = 1.0f)

        if (audio.samples.isEmpty()) return@withContext

        val bufSize = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        require(bufSize > 0) {
            "AudioTrack.getMinBufferSize returned $bufSize for sampleRate=${audio.sampleRate}"
        }
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
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException(
                "AudioTrack init failed (state=${track.state}) for sampleRate=${audio.sampleRate}"
            )
        }
        try {
            track.play()
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
        } finally {
            track.stop()
            track.release()
        }
    }

    override fun close() {
        synchronized(ttsInstances) {
            ttsInstances.values.forEach { it.release() }
            ttsInstances.clear()
        }
    }

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
