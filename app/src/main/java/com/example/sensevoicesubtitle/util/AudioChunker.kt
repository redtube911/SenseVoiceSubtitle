package com.example.sensevoicesubtitle.util

import android.media.*
import android.net.Uri
import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Decode the video's first audio track to mono PCM16/16kHz, writing one WAV per chunk.
 * Chunks are written to disk immediately; the app never holds a whole long-video waveform in RAM.
 */
class AudioChunker(private val context: Context) {
    data class Chunk(val file: File, val startMs: Long, val durationMs: Long)

    fun extract(uri: Uri, outDir: File, chunkMs: Long = 5 * 60 * 1000L, onProgress: (Int) -> Unit): List<Chunk> {
        if (!outDir.exists() && !outDir.mkdirs()) throw IllegalStateException("无法创建临时目录")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) throw UnsupportedAudioException()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw UnsupportedAudioException()
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val resampler = PcmResampler(srcRate, 16_000, srcChannels)
            val chunks = mutableListOf<Chunk>()
            var chunkStartMs = 0L
            var currentWav: WavWriter? = null
            var currentFile: File? = null
            var outputSamplesMs = 0L
            var sawInputEos = false; var sawOutputEos = false
            val bufferInfo = MediaCodec.BufferInfo()

            fun openChunk() {
                currentFile = File(outDir, "chunk_${chunks.size.toString().padStart(4, '0')}.wav")
                currentWav = WavWriter(currentFile!!, 16_000, 1)
                currentWav!!.writeHeaderPlaceholder()
                outputSamplesMs = 0L
            }
            fun closeChunk() {
                val wav = currentWav ?: return
                wav.finish()
                val file = currentFile ?: return
                if (wav.dataBytes > 0) chunks += Chunk(file, chunkStartMs, outputSamplesMs)
                wav.close(); currentWav = null
            }
            openChunk()

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val input = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val out = decoder.getOutputBuffer(outIndex)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                            val bytes = ByteArray(bufferInfo.size)
                            out.position(bufferInfo.offset); out.limit(bufferInfo.offset + bufferInfo.size); out.get(bytes)
                            val samples = resampler.pcm16ToFloat(bytes)
                            var pos = 0
                            while (pos < samples.size) {
                                val canTake = min(samples.size - pos, ((chunkMs - outputSamplesMs).coerceAtLeast(1) * 16L).toInt())
                                val part = samples.copyOfRange(pos, pos + canTake)
                                currentWav!!.writeFloatMono(part)
                                val addedMs = (canTake * 1000L) / 16_000L
                                outputSamplesMs += addedMs; pos += canTake
                                if (outputSamplesMs >= chunkMs && pos < samples.size) {
                                    closeChunk(); chunkStartMs += outputSamplesMs; openChunk()
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEos = true
                        onProgress(((extractor.sampleTime.coerceAtLeast(0) * 100L) / durationUs.coerceAtLeast(1)).toInt().coerceIn(0, 100))
                    }
                }
            }
            closeChunk(); decoder.stop(); decoder.release(); onProgress(100); return chunks
        } catch (e: SecurityException) { throw UnsupportedVideoException() }
        finally { extractor.release() }
    }

    class UnsupportedVideoException : Exception("视频没有可解码的音频轨道或格式不受支持")
    class UnsupportedAudioException : Exception("找不到可解码的音频轨道")

    private class PcmResampler(private val srcRate: Int, private val dstRate: Int, private val channels: Int) {
        fun pcm16ToFloat(bytes: ByteArray): FloatArray {
            val totalFrames = bytes.size / 2 / channels
            val mono = FloatArray(totalFrames)
            var p = 0
            for (i in 0 until totalFrames) {
                var sum = 0f
                repeat(channels) { ch ->
                    val lo = bytes[p++].toInt() and 0xFF; val hi = bytes[p++].toInt(); sum += (((hi shl 8) or lo).toShort().toInt() / 32768f)
                }
                mono[i] = sum / channels
            }
            if (srcRate == dstRate) return mono
            val outN = (mono.size.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
            val out = FloatArray(outN)
            val ratio = srcRate.toDouble() / dstRate
            for (i in out.indices) { val x = i * ratio; val a = x.toInt().coerceIn(0, mono.lastIndex); val b = (a + 1).coerceAtMost(mono.lastIndex); val t = (x - a).toFloat(); out[i] = mono[a] * (1 - t) + mono[b] * t }
            return out
        }
    }

    private class WavWriter(private val file: File, private val rate: Int, private val channels: Int) : AutoCloseable {
        private val raf = RandomAccessFile(file, "rw"); var dataBytes = 0L; private set
        fun writeHeaderPlaceholder() { repeat(44) { raf.write(0) } }
        fun writeFloatMono(samples: FloatArray) { val buf = ByteArray(samples.size * 2); var p=0; for (s in samples) { val v=(s.coerceIn(-1f,1f)*32767).toInt(); buf[p++]=(v and 0xFF).toByte(); buf[p++]=(v shr 8).toByte() }; raf.write(buf); dataBytes += buf.size }
        fun finish() { raf.seek(0); writeAscii("RIFF"); writeIntLE((36 + dataBytes).toInt()); writeAscii("WAVEfmt "); writeIntLE(16); writeShortLE(1); writeShortLE(channels); writeIntLE(rate); writeIntLE(rate*channels*2); writeShortLE((channels*2)); writeShortLE(16); writeAscii("data"); writeIntLE(dataBytes.toInt()); raf.seek(44 + dataBytes) }
        private fun writeAscii(s:String){ s.forEach { raf.write(it.code) } }; private fun writeIntLE(v:Int){ raf.write(v and 255); raf.write((v shr 8) and 255); raf.write((v shr 16) and 255); raf.write((v shr 24) and 255) }; private fun writeShortLE(v:Int){ raf.write(v and 255); raf.write((v shr 8) and 255) }
        override fun close(){ raf.close() }
    }
}
