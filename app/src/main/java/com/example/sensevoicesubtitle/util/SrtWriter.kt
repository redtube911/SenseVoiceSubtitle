package com.example.sensevoicesubtitle.util

import com.example.sensevoicesubtitle.data.SubtitleCue
import java.util.Locale

object SrtWriter {
    fun format(cues: List<SubtitleCue>, showEmotion: Boolean, showEvent: Boolean): String = buildString {
        cues.forEachIndexed { i, cue ->
            append(i + 1).append('\n')
            append(ts(cue.startMs)).append(" --> ").append(ts(cue.endMs)).append('\n')
            var text = wrapForSrt(cue.text.trim())
            val tags = mutableListOf<String>()
            if (showEmotion && cue.emotion.isNotBlank()) tags += "情绪:${cue.emotion}"
            if (showEvent && cue.event.isNotBlank()) tags += "事件:${cue.event}"
            if (tags.isNotEmpty()) text += "\n[${tags.joinToString(" | ")}]"
            append(text).append("\n\n")
        }
    }

    private fun wrapForSrt(text: String): String {
        if (text.isBlank()) return text
        val compact = text.replace(Regex("\\s+"), " ").trim()
        val cjk = compact.count { it in '\u3400'..'\u9FFF' || it in '\u3040'..'\u30FF' || it in '\uAC00'..'\uD7AF' }
        val latin = compact.count { it.isLetter() && it.code < 128 }
        val limit = if (cjk >= latin) 11 else 21
        if (compact.length <= limit) return compact

        val cut = if (cjk >= latin) {
            limit
        } else {
            (limit downTo 1).firstOrNull { compact[it - 1].isWhitespace() } ?: limit
        }
        return compact.substring(0, cut).trim() + "\n" + compact.substring(cut).trim()
    }

    private fun ts(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms / 60_000) % 60; val s = (ms / 1_000) % 60; val x = ms % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, x)
    }
}
