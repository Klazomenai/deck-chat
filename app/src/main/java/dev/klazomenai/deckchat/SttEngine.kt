package dev.klazomenai.deckchat

import java.io.File

/**
 * Speech-to-text engine abstraction.
 *
 * Takes a [File] containing raw 16-bit little-endian PCM audio at 16 kHz mono.
 * File-based API chosen over ByteArray (deviation from issue #3 spec) because
 * RecordingService writes to disk and Sherpa-ONNX JNI operates on file paths.
 */
interface SttEngine {
    suspend fun transcribe(audioFile: File): String
    fun close()
}
