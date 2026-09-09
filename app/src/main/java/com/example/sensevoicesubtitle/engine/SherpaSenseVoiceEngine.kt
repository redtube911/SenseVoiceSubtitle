package com.example.sensevoicesubtitle.engine

import android.content.Context
import com.example.sensevoicesubtitle.data.AppSettings
import com.example.sensevoicesubtitle.data.SubtitleCue
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.io.File
import java.io.RandomAccessFile

/** Uses the official sherpa-onnx Kotlin API and its SenseVoice model conversion. */
class SherpaSenseVoiceEngine(context: Context) : SenseVoiceEngine {
    private val recognizerCache = HashMap<Pair<String, Int>, OfflineRecognizer>()
    private val appContext = context.applicationContext

    private fun recognizer(settings: AppSettings): OfflineRecognizer {
        val key = settings.language.code to settings.threads
        return recognizerCache[key] ?: run {
            val modelDir = "sensevoice"
            val model = "$modelDir/model.int8.onnx"
            val tokens = "$modelDir/tokens.txt"
            val cfg = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = model,
                        language = settings.language.code,
                        useInverseTextNormalization = true,
                    ),
                    tokens = tokens,
                    numThreads = settings.threads,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            )
            OfflineRecognizer(appContext.assets, cfg).also { recognizerCache[key] = it }
        }
    }

    override fun transcribe(wav: File, offsetMs: Long, settings: AppSettings): List<SubtitleCue> {
        val rec = recognizer(settings)
        val cues = mutableListOf<SubtitleCue>()
        WavReader(wav).use { reader ->
            val windowMs = 25_000L
            var localMs = 0L
            while (localMs < reader.durationMs) {
                val wanted = minOf(windowMs, reader.durationMs - localMs)
                val samples = reader.readSamples((wanted * reader.sampleRate / 1000L).toInt())
                if (samples.isEmpty()) break
                val stream = rec.createStream()
                stream.acceptWaveform(samples, sampleRate = reader.sampleRate)
                rec.decode(stream)
                val r = rec.getResult(stream)
                val text = r.text.trim()
                if (text.isNotBlank()) {
                    cues += groupText(
                        text,
                        r.timestamps.map { offsetMs + localMs + (it * 1000).toLong() },
                        offsetMs + localMs,
                        samples.size * 1000L / reader.sampleRate
                    ).map { it.copy(
                        emotion = if (settings.showEmotion) r.emotion else "",
                        event = if (settings.showEvent) r.event else ""
                    ) }
                }
                stream.release()
                localMs += wanted
            }
        }
        return cues
    }

    private class WavReader(private val file: File) : AutoCloseable {
        private val raf = RandomAccessFile(file, "r")
        val sampleRate: Int
        val durationMs: Long
        private var remainingBytes: Long
        init {
            raf.seek(24); sampleRate = readIntLE(raf); raf.seek(40)
            val dataBytes = readIntLE(raf).toLong()
            remainingBytes = dataBytes
            durationMs = dataBytes * 1000L / (sampleRate * 2L)
            raf.seek(44)
        }
        fun readSamples(count: Int): FloatArray {
            val bytesToRead = minOf(remainingBytes, count.toLong() * 2L).toInt()
            if (bytesToRead <= 0) return FloatArray(0)
            val bytes = ByteArray(bytesToRead); raf.readFully(bytes); remainingBytes -= bytesToRead
            val out = FloatArray(bytesToRead / 2); var p = 0
            for (i in out.indices) { val lo = bytes[p++].toInt() and 255; val hi = bytes[p++].toInt(); out[i] = (((hi shl 8) or lo).toShort().toInt() / 32768f) }
            return out
        }
        private fun readIntLE(r: RandomAccessFile): Int = r.read() or (r.read() shl 8) or (r.read() shl 16) or (r.read() shl 24)
        override fun close() { raf.close() }
    }

    /** Subtitle-friendly segmentation using SenseVoice token timestamps. */
    private fun groupText(
        text: String,
        tokenStarts: List<Long>,
        offsetMs: Long,
        durationMs: Long
    ): List<SubtitleCue> = SubtitleSegmenter.split(text, tokenStarts, offsetMs, durationMs)

    private fun readWavPcm16(file: File): Pair<FloatArray, Int> {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(24); val rate = readIntLE(raf); raf.seek(44)
            val bytes = ByteArray((raf.length() - 44).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()); raf.readFully(bytes)
            val samples = FloatArray(bytes.size / 2); var p = 0
            for (i in samples.indices) { val lo=bytes[p++].toInt() and 255; val hi=bytes[p++].toInt(); samples[i]=(((hi shl 8) or lo).toShort().toInt()/32768f) }
            return samples to rate
        }
    }
    private fun readIntLE(raf: RandomAccessFile): Int = raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)

    override fun close() { recognizerCache.values.forEach { runCatching { it.release() } }; recognizerCache.clear() }
}
