package com.example.sensevoicesubtitle.engine

import android.content.Context

object ModelValidator {
    fun validate(context: Context) {
        require(assetSize(context, "sensevoice/model.int8.onnx") > 10_000_000L) { "SenseVoice 模型文件不完整" }
        require(assetSize(context, "sensevoice/tokens.txt") > 100L) { "SenseVoice tokens.txt 文件缺失或不完整" }
    }
    private fun assetSize(context: Context, path: String): Long = context.assets.open(path).use { it.available().toLong() }
}
