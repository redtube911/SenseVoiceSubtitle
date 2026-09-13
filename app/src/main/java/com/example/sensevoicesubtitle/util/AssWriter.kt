package com.example.sensevoicesubtitle.util

import com.example.sensevoicesubtitle.data.SubtitleCue
import java.util.Locale

/**
 * Advanced SubStation Alpha writer.
 * ASS allows us to explicitly pin subtitles to the bottom-center of the video.
 * SRT itself has no standard, portable subtitle-position field.
 */
object AssWriter {
    private const val PLAY_RES_X = 1920
    private const val PLAY_RES_Y = 1080
    private const val MARGIN_V = 48

    fun format(cues: List<SubtitleCue>, showEmotion: Boolean, showEvent: Boolean): String = buildString {
        appendLine("[Script Info]")
        appendLine("ScriptType: v4.00+")
        appendLine("PlayResX: $PLAY_RES_X")
        appendLine("PlayResY: $PLAY_RES_Y")
        appendLine("ScaledBorderAndShadow: yes")
        appendLine("WrapStyle: 2")
        appendLine("YCbCr Matrix: None")
        appendLine()
        appendLine("[V4+ Styles]")
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        // Alignment 2 = bottom-center. MarginV is fixed, so the subtitle anchor does not drift.
        appendLine("Style: Movie,Arial,52,&H00FFFFFF,&H00FFFFFF,&H00101010,&H00000000,0,0,0,0,100,100,0,0,1,3,0,2,80,80,$MARGIN_V,1")
        appendLine()
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

        cues.forEach { cue ->
            val tags = mutableListOf<String>()
            if (showEmotion && cue.emotion.isNotBlank()) tags += "情绪:${cue.emotion}"
            if (showEvent && cue.event.isNotBlank()) tags += "事件:${cue.event}"

            val main = wrapForAss(cue.text.trim())
            val text = if (tags.isEmpty()) main else "$main\\N{${tags.joinToString(" | ")}}"
            appendLine("Dialogue: 0,${ts(cue.startMs)},${ts(cue.endMs)},Movie,,0,0,0,,${escape(text)}")
        }
    }

    private fun wrapForAss(text: String): String {
        if (text.isBlank()) return text
        val compact = text.replace(Regex("\\s+"), " ").trim()
        if (compact.contains("\\N")) return compact

        val cjk = compact.count { it in '\u3400'..'\u9FFF' || it in '\u3040'..'\u30FF' || it in '\uAC00'..'\uD7AF' }
        val latin = compact.count { it.isLetter() && it.code < 128 }
        val limit = if (cjk >= latin) 22 else 42
        if (compact.length <= limit) return compact

        val first = if (cjk >= latin) {
            limit
        } else {
            (limit downTo 1).firstOrNull { compact[it - 1].isWhitespace() } ?: limit
        }
        return compact.substring(0, first).trim() + "\\N" + compact.substring(first).trim()
    }

    private fun escape(text: String): String = text.replace("{", "\\{").replace("}", "\\}")

    private fun ts(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1_000) % 60
        val cs = (ms % 1_000) / 10
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }
}
