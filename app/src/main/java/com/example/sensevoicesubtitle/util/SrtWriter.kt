package com.example.sensevoicesubtitle.util

import com.example.sensevoicesubtitle.data.SubtitleCue
import java.util.Locale

object SrtWriter {
    fun format(cues: List<SubtitleCue>, showEmotion: Boolean, showEvent: Boolean): String = buildString {
        cues.forEachIndexed { i, cue ->
            append(i + 1).append('\n')
            append(ts(cue.startMs)).append(" --> ").append(ts(cue.endMs)).append('\n')
            var text = cue.text.trim()
            val tags = mutableListOf<String>()
            if (showEmotion && cue.emotion.isNotBlank()) tags += "情绪:${cue.emotion}"
            if (showEvent && cue.event.isNotBlank()) tags += "事件:${cue.event}"
            if (tags.isNotEmpty()) text += "\n[${tags.joinToString(" | ")}]"
            append(text).append("\n\n")
        }
    }

    private fun ts(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms / 60_000) % 60; val s = (ms / 1_000) % 60; val x = ms % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, x)
    }
}
