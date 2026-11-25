# Media Service Relay

[English](README.md) · [简体中文](docs/README.zh-CN.md) · [繁體中文](docs/README.zh-TW.md) · [日本語](docs/README.ja.md) · [한국어](docs/README.ko.md) · [Deutsch](docs/README.de.md)

[![Android CI](https://github.com/hanasaki-fubuki/media-service-relay/actions/workflows/ci.yml/badge.svg)](https://github.com/hanasaki-fubuki/media-service-relay/actions/workflows/ci.yml)

**Keep QQ Music HD (QQ 音乐 HD) and compatible Android players playing in the background.** Relay maintains a connection to the player's playback service while you switch apps or lock the screen. It started as a workaround for an AxionOS issue on the Xiaomi Pad 7 where QQ Music HD went silent in the background while the progress bar kept moving.

[Download the signed APK](https://github.com/hanasaki-fubuki/media-service-relay/releases/latest)

## Get started

1. Install the APK on **Android 14 or later**. The original setup was tested on a Xiaomi Pad 7 running AxionOS (Android 16).
2. Open **Connections** and connect the built-in QQ Music HD profile. For another player, use **Add app** and select its compatible playback service.
3. Use **Create shortcut** to launch a player with its relay already connected. **Disconnect** releases that connection. QQ Music HD also has a separate **Exit** action.

Compatibility depends on the services and media controls each player exposes. Relay includes searchable connection logs and individually configurable playback controls and spectrum displays. The interface supports US English, UK English, Simplified Chinese, Traditional Chinese, Japanese, Korean and German.

## Optional access

- **Notification access** lets Relay read playback status and send media commands. It is requested when you enable the spectrum or playback controls.
- **Live spectrum** uses Android's audio permission and screen-sharing consent to analyse the selected app's playback audio in memory. Capture stops when you leave the Connections page. The ordinary animated bars use playback state.

## Build and release

Use JDK 17 or later, Android SDK Platform 36 and Build Tools 36.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`.

```sh
./gradlew assembleDebug lintDebug
```

On Windows, run `gradlew.bat assembleDebug lintDebug`. GitHub Actions checks pushes to `main` and pull requests. A tag matching the app version, such as `v1.9`, builds and publishes a signed APK and its SHA-256 checksum to Releases.

## License and credits

Relay's own source is licensed under [GPL-3.0-only](LICENSE). The vendored AxionOS theme retains its Apache 2.0 license. See [third-party notices](THIRD_PARTY_NOTICES.md).

Relay's original code was written by OpenAI Codex. はなさき　フブキ provided project direction, reproduced the playback problem on the tablet and guided feature design and device testing.
