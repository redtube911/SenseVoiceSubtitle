# SenseVoiceSubtitle

Android 本地离线视频字幕生成器：SenseVoice Small FP32 ONNX + sherpa-onnx。

## 手机 GitHub 网页上传

不要把 ZIP 文件本身上传到 GitHub。先解压，然后把本项目的内容上传到仓库根目录。

推荐顺序：
1. 根目录：`.gitignore`、`build.gradle.kts`、`gradle.properties`、`settings.gradle.kts`、`README.md`
2. `app/`：进入 GitHub 的 `app` 文件夹后上传 `build.gradle.kts`、`libs/README.txt`，以及 `src/` 下的全部文件
3. `.github/workflows/`：上传 `build-apk.yml`

最终根目录必须直接看到：
- `.github/`
- `app/`
- `.gitignore`
- `build.gradle.kts`
- `gradle.properties`
- `settings.gradle.kts`
- `README.md`

不要出现 `SenseVoiceSubtitle/SenseVoiceSubtitle/app` 这种多套一层目录的情况。

## 自动编译 APK

上传完成后：GitHub → Actions → Build Android APK → Run workflow。

构建成功后，在本次运行页面的 Artifacts 中下载 `SenseVoiceSubtitle-debug`。

GitHub Actions 会自动下载 sherpa-onnx Android AAR 和 SenseVoice Small FP32 ONNX 模型，因此模型和 AAR 不需要上传到仓库。

## 字幕分割优化

新版加入电影字幕友好的智能分割器：
- 中文/日文/韩文默认每条最多约 22 个字符；
- 英文默认每条最多约 42 个字符，并尽量不从单词中间切断；
- 优先在 `。！？；` 等句末标点切分，其次在逗号、顿号等自然停顿处切分；
- 单条字幕目标不超过约 5 秒；
- SRT 显示层进一步自动换成两行，中文约 11+11 字、英文约 21+21 字；
- 使用 SenseVoice 时间戳估算每个片段的时间范围。

如果你觉得字幕仍然偏长，可以继续把中文上限从 22 调到 18。


## FP32 ONNX 模型

GitHub Actions 会直接下载官方 sherpa-onnx SenseVoice Small FP32 ONNX，最终 APK 使用 `model.onnx`。不进行 FP16 转换，也不进行 INT8 量化。模型不需要提交到 GitHub 仓库。

## 字幕固定在视频底部

应用现在同时生成 `.srt` 和 `.ass`。SRT 标准本身没有统一的字幕坐标字段，因此无法保证所有播放器都把 SRT 固定在底部。推荐使用 `.ass`：它使用 ASS 的 `Alignment=2`（底部居中）和固定 `MarginV`，字幕锚点始终固定在视频下方。
