package com.example.sensevoicesubtitle.engine

import com.example.sensevoicesubtitle.data.RecognizeLanguage
import com.example.sensevoicesubtitle.data.SubtitleCue
import com.example.sensevoicesubtitle.data.AppSettings
import java.io.File

interface SenseVoiceEngine : AutoCloseable {
    fun transcribe(wav: File, offsetMs: Long, settings: AppSettings): List<SubtitleCue>
    override fun close()
}
