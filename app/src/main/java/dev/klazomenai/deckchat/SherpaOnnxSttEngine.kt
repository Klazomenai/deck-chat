package dev.klazomenai.deckchat

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device STT engine backed by Sherpa-ONNX + Whisper Tiny EN (int8 ONNX).
 *
 * Model files are loaded from assets (app/src/main/assets/stt/) which are
 * copied to filesDir on first use — the assets API is read-only, but the JNI
 * layer requires file-system paths. Models are gitignored and downloaded via
 * the downloadSttModels Gradle task or scripts/download-stt-models.sh.
 *
 * JNI library is loaded via the companion object; this class must NOT be
 * instantiated in JVM unit tests. Use MockSttEngine instead.
 */
class SherpaOnnxSttEngine(private val context: Context) : SttEngine {

    private var recognizerInstance: OfflineRecognizer? = null

    private val recognizer: OfflineRecognizer
        get() {
            return recognizerInstance ?: createRecognizer().also { recognizerInstance = it }
        }

    private fun copyAssetsToDisk(): File {
        val destDir = File(context.filesDir, "stt")
        val encoderFile = File(destDir, ENCODER_FILE)
        val decoderFile = File(destDir, DECODER_FILE)

        if (destDir.exists()) {
            val encoderOk = encoderFile.exists() && encoderFile.length() > 0
            val decoderOk = decoderFile.exists() && decoderFile.length() > 0
            if (encoderOk && decoderOk) return destDir
        }

        destDir.mkdirs()

        val assetFiles = context.assets.list(ASSET_DIR)
        require(!assetFiles.isNullOrEmpty()) {
            "STT model assets not found in $ASSET_DIR — run ./gradlew downloadSttModels first"
        }
        require(assetFiles.contains(ENCODER_FILE) && assetFiles.contains(DECODER_FILE)) {
            "Expected $ENCODER_FILE and $DECODER_FILE in assets/$ASSET_DIR, found: ${assetFiles.toList()}"
        }

        assetFiles.forEach { name ->
            val dest = File(destDir, name)
            context.assets.open("$ASSET_DIR/$name").use { src ->
                FileOutputStream(dest).use { out -> src.copyTo(out) }
            }
        }
        return destDir
    }

    private fun createRecognizer(): OfflineRecognizer {
        Log.d(TAG, "Copying model assets to disk")
        val sttDir = copyAssetsToDisk().absolutePath
        Log.d(TAG, "Model dir: $sttDir")
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "$sttDir/$ENCODER_FILE",
                    decoder = "$sttDir/$DECODER_FILE",
                    language = "en",
                    task = "transcribe",
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        Log.d(TAG, "Creating OfflineRecognizer from config")
        val rec = OfflineRecognizer(config = config)
        Log.d(TAG, "OfflineRecognizer created: $rec")
        return rec
    }

    /**
     * Transcribes raw 16-bit little-endian PCM audio at 16 kHz mono.
     *
     * The caller is responsible for writing audio in this
     * format. WAV headers or other container formats are not handled — passing
     * non-PCM data will produce garbage output.
     */
    override suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "transcribe() called, file=${audioFile.name}, size=${audioFile.length()}")
        val rec = recognizer
        Log.d(TAG, "Recognizer ready, creating stream")
        val stream = rec.createStream()
        Log.d(TAG, "Stream created: $stream")
        try {
            val bytes = audioFile.readBytes()
            require(bytes.isNotEmpty()) {
                "Audio file ${audioFile.absolutePath} is empty; expected 16-bit PCM data."
            }
            require(bytes.size % 2 == 0) {
                "Audio file ${audioFile.absolutePath} has ${bytes.size} bytes; expected even byte count for 16-bit PCM."
            }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val samples = FloatArray(bytes.size / 2) { buf.short / 32768f }
            Log.d(TAG, "Audio loaded: ${samples.size} samples (${samples.size / SAMPLE_RATE.toFloat()}s)")
            Log.d(TAG, "Calling acceptWaveform")
            stream.acceptWaveform(samples, SAMPLE_RATE)
            Log.d(TAG, "acceptWaveform complete, calling decode")
            rec.decode(stream)
            Log.d(TAG, "decode complete, getting result")
            val text = rec.getResult(stream).text
            Log.d(TAG, "Transcription result: ${text.take(50)}")
            text
        } finally {
            stream.release()
            Log.d(TAG, "Stream released")
        }
    }

    override fun close() {
        recognizerInstance?.release()
        recognizerInstance = null
    }

    companion object {
        private const val ASSET_DIR = "stt"
        private const val ENCODER_FILE = "tiny.en-encoder.int8.onnx"
        private const val DECODER_FILE = "tiny.en-decoder.int8.onnx"
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80

        private const val TAG = "DeckChat.SttEngine"

        init {
            try {
                System.loadLibrary("sherpa-onnx-jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load sherpa-onnx-jni native library", e)
                throw e
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to load sherpa-onnx-jni native library", e)
                throw e
            }
        }
    }
}
