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

    private val recognizer: OfflineRecognizer by lazy { createRecognizer() }

    private fun copyAssetsToDisk(): File {
        val destDir = File(context.filesDir, "stt")
        if (destDir.exists() && destDir.listFiles()?.isNotEmpty() == true) return destDir
        destDir.mkdirs()
        context.assets.list(ASSET_DIR)?.forEach { name ->
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
        recognizer.release()
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
