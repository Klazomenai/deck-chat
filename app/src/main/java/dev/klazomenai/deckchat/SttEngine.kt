package dev.klazomenai.deckchat

import java.io.File

interface SttEngine {
    suspend fun transcribe(audioFile: File): String
    fun close()
}
