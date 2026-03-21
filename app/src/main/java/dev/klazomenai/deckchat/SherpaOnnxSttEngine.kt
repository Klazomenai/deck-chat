package dev.klazomenai.deckchat

import android.content.Context
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
        val sttDir = copyAssetsToDisk().absolutePath
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
        return OfflineRecognizer(config = config)
    }

    /**
     * Transcribes raw 16-bit little-endian PCM audio at 16 kHz mono.
     *
     * The caller is responsible for writing audio in this
     * format. WAV headers or other container formats are not handled — passing
     * non-PCM data will produce garbage output.
     */
    override suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        val stream = recognizer.createStream()
        try {
            val bytes = audioFile.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val samples = FloatArray(bytes.size / 2) { buf.short / 32768f }
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            stream.release()
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

        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
