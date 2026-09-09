package com.example.sensevoicesubtitle.data

enum class RecognizeLanguage(val code: String, val label: String) {
    AUTO("auto", "自动"), ZH("zh", "中文"), EN("en", "英语"), JA("ja", "日语"), KO("ko", "韩语"), YUE("yue", "粤语")
}

data class AppSettings(val language: RecognizeLanguage = RecognizeLanguage.AUTO, val showEmotion: Boolean = true, val showEvent: Boolean = true, val threads: Int = 2)

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String, val emotion: String = "", val event: String = "")

data class ProgressState(val phase: String = "待机", val audioPercent: Int = 0, val recognitionPercent: Int = 0, val message: String = "选择视频开始")
