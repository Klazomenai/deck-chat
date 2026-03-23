package dev.klazomenai.deckchat

/**
 * Text-to-speech engine abstraction.
 *
 * Generates speech audio for a named crew member and plays it through the
 * default system audio output. Bluetooth headset routing is handled
 * separately by DeckChatAudioManager (issue #5).
 *
 * Each crew name maps to a distinct voice profile (Piper ONNX model).
 * The announcement prefix (e.g. "Maren:") is prepended before synthesis.
 */
interface TtsEngine {
    suspend fun speak(crewName: String, text: String)
    fun close()
}
