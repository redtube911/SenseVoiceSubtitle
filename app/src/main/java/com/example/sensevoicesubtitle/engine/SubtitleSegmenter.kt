package com.example.sensevoicesubtitle.engine

import com.example.sensevoicesubtitle.data.SubtitleCue
import kotlin.math.max
import kotlin.math.min

/**
 * Splits long SenseVoice results into readable movie-style subtitles.
 * The input timestamps are token timestamps from SenseVoice. Since token count
 * and Kotlin text character count are not always identical, character positions
 * are mapped to the available timestamps proportionally.
 */
object SubtitleSegmenter {
    private const val MIN_DURATION_MS = 650L
    private const val MAX_DURATION_MS = 5_000L
    private const val MIN_CHARS = 4
    private const val ZH_MAX_CHARS = 22
    private const val LATIN_MAX_CHARS = 42

    private val hardPunctuation = setOf('。', '！', '？', '!', '?', ';', '；', '\n')
    private val softPunctuation = setOf('，', ',', '、', '：', ':', '—', '-', '…')

    fun split(
        text: String,
        tokenStarts: List<Long>,
        fallbackStartMs: Long,
        fallbackDurationMs: Long,
    ): List<SubtitleCue> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return emptyList()

        val start = tokenStarts.firstOrNull() ?: fallbackStartMs
        val estimatedEnd = tokenStarts.lastOrNull()?.plus(850L) ?: (fallbackStartMs + fallbackDurationMs)
        val end = max(start + MIN_DURATION_MS, min(estimatedEnd, fallbackStartMs + fallbackDurationMs + 900L))
        val maxChars = if (looksLikeCjk(normalized)) ZH_MAX_CHARS else LATIN_MAX_CHARS

        if (normalized.length <= maxChars && end - start <= MAX_DURATION_MS) {
            return listOf(SubtitleCue(start, max(start + MIN_DURATION_MS, end), normalized))
        }

        val chunks = mutableListOf<Chunk>()
        var cursor = 0
        while (cursor < normalized.length) {
            val remaining = normalized.length - cursor
            if (remaining <= maxChars && !tooLongByDuration(cursor, normalized.length, start, end)) {
                chunks += Chunk(cursor, normalized.length)
                break
            }

            val durationLimitedChars = if (end > start) {
                ((normalized.length.toLong() * MAX_DURATION_MS) / (end - start)).toInt().coerceAtLeast(MIN_CHARS)
            } else {
                maxChars
            }
            val maxEnd = min(normalized.length, cursor + min(maxChars, durationLimitedChars))
            val cut = chooseCut(normalized, cursor, maxEnd)
            val safeCut = if (cut <= cursor) min(normalized.length, cursor + maxChars) else cut
            chunks += Chunk(cursor, safeCut)
            cursor = safeCut
            while (cursor < normalized.length && normalized[cursor].isWhitespace()) cursor++
        }

        return chunks.mapIndexed { index, chunk ->
            val chunkStart = mapPosition(chunk.start, normalized.length, start, end)
            var chunkEnd = mapPosition(chunk.end, normalized.length, start, end)
            if (index == chunks.lastIndex) chunkEnd = max(chunkEnd, end)
            chunkEnd = max(chunkStart + MIN_DURATION_MS, chunkEnd)
            SubtitleCue(chunkStart, chunkEnd.coerceAtMost(end + 500L), normalized.substring(chunk.start, chunk.end).trim())
        }.filter { it.text.isNotBlank() }
    }

    private fun chooseCut(text: String, start: Int, maxEnd: Int): Int {
        // Prefer the latest sentence-ending punctuation that still leaves a useful fragment.
        for (i in maxEnd downTo start + MIN_CHARS) {
            if (text[i - 1] in hardPunctuation) return i
        }
        // Then use a natural pause.
        for (i in maxEnd downTo start + MIN_CHARS) {
            if (text[i - 1] in softPunctuation) return i
        }

        // For English, never split in the middle of a word if a nearby space exists.
        if (!looksLikeCjk(text.substring(start, maxEnd))) {
            for (i in maxEnd downTo start + MIN_CHARS) {
                if (text[i - 1].isWhitespace()) return i
            }
        }
        return maxEnd
    }

    private fun tooLongByDuration(pos: Int, total: Int, start: Long, end: Long): Boolean {
        val projected = start + ((end - start) * pos.toLong() / total.coerceAtLeast(1))
        return projected - start > MAX_DURATION_MS
    }

    private fun mapPosition(pos: Int, length: Int, start: Long, end: Long): Long {
        if (length <= 0) return start
        return start + ((end - start) * pos.toLong() / length.toLong())
    }

    private fun looksLikeCjk(text: String): Boolean {
        val cjk = text.count { it in '\u3400'..'\u9FFF' || it in '\u3040'..'\u30FF' || it in '\uAC00'..'\uD7AF' }
        val latin = text.count { it.isLetter() && it.code < 128 }
        return cjk >= latin
    }

    private data class Chunk(val start: Int, val end: Int)
}
