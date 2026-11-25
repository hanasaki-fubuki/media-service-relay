# Media Service Relay

[English](../README.md) · [简体中文](README.zh-CN.md) · 繁體中文 · [日本語](README.ja.md) · [한국어](README.ko.md) · [Deutsch](README.de.md)

**讓 QQ 音樂 HD 與相容的 Android 播放器在背景繼續播放。** Relay 會維持與播放服務的連線，讓切換應用程式或鎖定螢幕後仍可播放。這個專案源於小米平板 7 上的 AxionOS 播放問題。QQ 音樂 HD 切到背景後聲音中斷，進度列卻繼續走。

[下載已簽署的 APK](https://github.com/hanasaki-fubuki/media-service-relay/releases/latest)

## 開始使用

1. 在 **Android 14 以上**的裝置安裝 APK。最初使用搭載 AxionOS（Android 16）的小米平板 7 測試。
2. 開啟「連線」，連接內建的 QQ 音樂 HD 設定檔。其他播放器可透過「新增應用程式」選擇相容的播放服務。
3. 選「建立捷徑」即可一鍵建立連線並開啟播放器。「中斷連線」會解除連線，QQ 音樂 HD 另有獨立的結束動作。

相容性取決於各播放器提供的服務與媒體控制。Relay 提供可搜尋的連線記錄，以及可分別開關的播放控制和頻譜。介面支援美國英語、英國英語、簡體中文、繁體中文、日文、韓文和德文。

## 選用權限

- **通知存取權**用於讀取播放狀態及傳送播放指令。啟用頻譜或播放控制時會要求授權。
- **即時頻譜**透過 Android 的錄音權限與螢幕分享授權，在記憶體中分析所選應用程式的播放音訊。離開連線頁面即停止擷取。一般動態長條依播放狀態顯示。

## 建置與發佈

使用 JDK 17 以上、Android SDK Platform 36 及 Build Tools 36.0.0。設定 `ANDROID_HOME`，或在 `local.properties` 中設定 `sdk.dir`。

```sh
./gradlew assembleDebug lintDebug
```

在 Windows 執行 `gradlew.bat assembleDebug lintDebug`。GitHub Actions 會檢查主分支的提交與提取請求。推送與應用程式版本相符的標籤（例如 `v1.9`），即會自動發佈簽署的 APK 與 SHA-256 校驗檔。

## 授權與署名

Relay 自有原始碼採 [GPL-3.0-only](../LICENSE) 授權。內含的 AxionOS 主題沿用 Apache 2.0，請參閱[第三方聲明](../THIRD_PARTY_NOTICES.md)。

Relay 自有程式碼由 OpenAI Codex 撰寫。はなさき　フブキ負責專案方向、在平板上重現播放問題，並指導功能設計和實機測試。
