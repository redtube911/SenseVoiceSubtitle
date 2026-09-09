# SenseVoiceSubtitle

Android 本地离线视频字幕生成器：SenseVoice Small + sherpa-onnx。

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

GitHub Actions 会自动下载 sherpa-onnx Android AAR 和 SenseVoice Small INT8 模型，因此模型和 AAR 不需要上传到仓库。
