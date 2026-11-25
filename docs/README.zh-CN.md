# Media Service Relay

[English](../README.md) · 简体中文 · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Deutsch](README.de.md)

**让 QQ 音乐 HD 和兼容的 Android 播放器在后台继续播放。** Relay 会维持播放服务连接，以便在切换应用或锁屏时继续播放。项目起于小米平板 7 的 AxionOS 上一个问题：QQ 音乐 HD 切到后台后声音断了，进度条却还在走。

[下载已签名的 APK](https://github.com/hanasaki-fubuki/media-service-relay/releases/latest)

## 开始使用

1. 在 **Android 14 或更高版本**上安装 APK。项目最早在搭载 AxionOS（Android 16）的小米平板 7 上实测。
2. 打开「连接」，使用内置的 QQ 音乐 HD 配置。其他播放器可通过「添加应用」选择兼容的播放服务。
3. 用「创建快捷方式」一键建立连接并打开播放器。「断开连接」解除连接，QQ 音乐 HD 还有单独的退出操作。

应用兼容性取决于播放器提供的服务和媒体控制。Relay 支持搜索连接日志，也可以分别开关播放控制和频谱。界面支持美国英语、英国英语、简体中文、繁体中文、日语、韩语和德语。

## 可选权限

- **通知使用权**用于读取播放状态和发送播放指令，启用播放控制或频谱时会提示授权。
- **实时频谱**使用 Android 的录音权限和共享屏幕授权，在内存中分析所选应用的播放音频。离开连接页即停止捕获。普通动条由播放状态驱动。

## 构建与发布

使用 JDK 17 或更高版本、Android SDK Platform 36 和 Build Tools 36.0.0。设置 `ANDROID_HOME`，或在 `local.properties` 里配置 `sdk.dir`。

```sh
./gradlew assembleDebug lintDebug
```

Windows 上执行 `gradlew.bat assembleDebug lintDebug`。GitHub Actions 会检查主分支提交和拉取请求。推送与应用版本一致的标签（如 `v1.9`），会自动构建并发布签名 APK 和 SHA-256 校验文件。

## 许可与署名

Relay 自有源码采用 [GPL-3.0-only](../LICENSE)。引用的 AxionOS 主题沿用 Apache 2.0 许可，详见[第三方声明](../THIRD_PARTY_NOTICES.md)。

Relay 自有代码由 OpenAI Codex 编写。はなさき　フブキ负责项目方向、在平板上复现播放问题，并指导功能设计和真机测试。
