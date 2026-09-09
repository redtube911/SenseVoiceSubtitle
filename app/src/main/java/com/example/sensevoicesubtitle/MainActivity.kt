package com.example.sensevoicesubtitle

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sensevoicesubtitle.ui.MainViewModel
import com.example.sensevoicesubtitle.data.RecognizeLanguage

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::setVideo) }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by vm.progress.collectAsState(); val settings by vm.settings.collectAsState()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("SenseVoice 字幕助手", style = MaterialTheme.typography.headlineMedium)
                    Text(vm.videoName.value.ifBlank { "未选择视频" })
                    Button(onClick = { pickVideo.launch(arrayOf("video/*")) }) { Text("选择视频") }
                    Button(enabled = !vm.running.value, onClick = vm::start) { Text(if (vm.running.value) "处理中…" else "开始生成字幕") }

                    LinearProgressIndicator(progress = { state.audioPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("音频提取：${state.audioPercent}%")
                    LinearProgressIndicator(progress = { state.recognitionPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("识别：${state.recognitionPercent}%")
                    Text(state.message)

                    Text("识别语言")
                    var expanded = false
                    var current = settings.language
                    // Compact language buttons are used instead of a heavy dropdown so the screen remains usable on phones.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecognizeLanguage.entries.forEach { lang ->
                            FilterChip(selected = current == lang, onClick = { vm.setLanguage(lang) }, label = { Text(lang.label) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("显示情感标签"); Switch(settings.showEmotion, vm::setShowEmotion)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("显示事件标签"); Switch(settings.showEvent, vm::setShowEvent)
                    }
                    Text("识别线程数：${settings.threads}")
                    Slider(value = settings.threads.toFloat(), onValueChange = { vm.setThreads(it.toInt().coerceIn(1, 8)) }, valueRange = 1f..8f, steps = 6)

                    vm.error.value?.let { err -> Text("错误：$err", color = MaterialTheme.colorScheme.error) }
                    vm.output.value?.let { out -> Text("字幕已生成：${out.absolutePath}") }
                }
            }
        }
    }
}
