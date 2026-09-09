package com.example.sensevoicesubtitle.ui

import android.app.Application
import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sensevoicesubtitle.data.*
import com.example.sensevoicesubtitle.engine.SenseVoiceEngine
import com.example.sensevoicesubtitle.engine.ModelValidator
import com.example.sensevoicesubtitle.engine.SherpaSenseVoiceEngine
import com.example.sensevoicesubtitle.util.AudioChunker
import com.example.sensevoicesubtitle.util.SrtWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _progress = MutableStateFlow(ProgressState()); val progress = _progress.asStateFlow()
    private val _settings = MutableStateFlow(AppSettings()); val settings = _settings.asStateFlow()
    val videoName = MutableStateFlow(""); val running = MutableStateFlow(false); val error = MutableStateFlow<String?>(null); val output = MutableStateFlow<File?>(null)
    private var uri: Uri? = null

    fun setVideo(u: Uri) { uri = u; videoName.value = u.lastPathSegment ?: "视频"; error.value = null; output.value = null }
    fun setLanguage(v: RecognizeLanguage) { _settings.value = _settings.value.copy(language = v) }
    fun setShowEmotion(v: Boolean) { _settings.value = _settings.value.copy(showEmotion = v) }
    fun setShowEvent(v: Boolean) { _settings.value = _settings.value.copy(showEvent = v) }
    fun setThreads(v: Int) { _settings.value = _settings.value.copy(threads = v.coerceIn(1, 8)) }

    fun start() {
        val input = uri ?: run { error.value = "请先选择视频"; return }
        if (running.value) return
        running.value = true; error.value = null; output.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val work = File(getApplication<Application>().cacheDir, "subtitle_work_${System.currentTimeMillis()}")
            try {
                requireFreeSpace(600L * 1024 * 1024)
                ModelValidator.validate(getApplication())
                _progress.value = ProgressState("提取音频", 0, 0, "正在按 5 分钟一段提取音频…")
                val chunks = AudioChunker(getApplication()).extract(input, work) { p -> _progress.value = _progress.value.copy(audioPercent = p, phase = "提取音频", message = "音频处理中 ${p}%") }
                if (chunks.isEmpty()) error("没有检测到可识别的音频")

                val cues = mutableListOf<SubtitleCue>()
                SenseVoiceEngineContext(getApplication()).use { engine ->
                    chunks.forEachIndexed { i, chunk ->
                        _progress.value = _progress.value.copy(phase = "识别", recognitionPercent = (i * 100 / chunks.size), message = "正在识别第 ${i + 1}/${chunks.size} 段")
                        cues += engine.transcribe(chunk.file, chunk.startMs, settings.value)
                        chunk.file.delete()
                        _progress.value = _progress.value.copy(recognitionPercent = ((i + 1) * 100 / chunks.size))
                    }
                }
                val out = File(getApplication<Application>().getExternalFilesDir(null), "${safeName(videoName.value)}.srt")
                out.parentFile?.mkdirs(); out.writeText(SrtWriter.format(cues, settings.value.showEmotion, settings.value.showEvent), Charsets.UTF_8)
                output.value = out; _progress.value = ProgressState("完成", 100, 100, "字幕生成完成，共 ${cues.size} 条")
            } catch (e: AudioChunker.UnsupportedVideoException) { error.value = e.message }
            catch (e: AudioChunker.UnsupportedAudioException) { error.value = e.message }
            catch (e: SecurityException) { error.value = "无法读取该视频，请在系统文件选择器中重新授权访问。" }
            catch (e: OutOfMemoryError) { error.value = "内存不足：已自动采用 5 分钟分段；请关闭后台应用后重试。" }
            catch (e: Exception) { error.value = e.message ?: "模型加载或识别失败，请确认模型文件完整。" }
            finally { work.deleteRecursively(); running.value = false }
        }
    }

    private fun requireFreeSpace(bytes: Long) { val fs = StatFs(getApplication<Application>().cacheDir.absolutePath); if (fs.availableBytes < bytes) throw IllegalStateException("存储空间不足，至少需要 ${bytes / 1024 / 1024} MB 可用空间") }
    private fun safeName(s: String): String = s.substringBeforeLast('.').replace(Regex("[^0-9A-Za-z\\u4e00-\\u9fa5._-]"), "_").ifBlank { "subtitle" }

    override fun onCleared() { super.onCleared() }
}

private class SenseVoiceEngineContext(app: Application) : AutoCloseable {
    private val engine: SenseVoiceEngine = SherpaSenseVoiceEngine(app)
    fun transcribe(file: File, offsetMs: Long, settings: AppSettings) = engine.transcribe(file, offsetMs, settings)
    override fun close() { engine.close() }
}
