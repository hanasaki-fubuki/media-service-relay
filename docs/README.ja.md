# Media Service Relay

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · 日本語 · [한국어](README.ko.md) · [Deutsch](README.de.md)

**QQ Music HD（QQ 音楽 HD）や対応する Android プレーヤーのバックグラウンド再生を維持します。** Relay はプレーヤーの再生サービスとの接続を保ち、アプリの切り替えや画面のロック後も再生が続くようにします。きっかけは Xiaomi Pad 7 の AxionOS で、QQ Music HD をバックグラウンドに移すと進捗バーだけが動き、音が途切れる問題でした。

[署名済み APK をダウンロード](https://github.com/hanasaki-fubuki/media-service-relay/releases/latest)

## 使い方

1. **Android 14 以上**の端末に APK をインストールします。最初に動作を確認したのは AxionOS（Android 16）搭載の Xiaomi Pad 7 です。
2. 「接続」で組み込みの QQ Music HD プロファイルを選択します。ほかのプレーヤーでは「アプリを追加」から対応する再生サービスを選びます。
3. 「ショートカットを作成」を使うと、ワンタップで接続してプレーヤーを開けます。「接続を解除」で接続を切れます。QQ Music HD には「終了」もあります。

対応状況は各プレーヤーが公開するサービスやメディア操作に依存します。接続ログを検索でき、再生コントロールとスペクトラムは個別に切り替えられます。画面表示はアメリカ英語、イギリス英語、簡体字中国語、繁体字中国語、日本語、韓国語、ドイツ語に対応しています。

## 任意の権限

- **通知へのアクセス**は再生状態の取得と再生操作に使います。スペクトラムまたは再生コントロールを有効にすると許可を求めます。
- **ライブスペクトラム**は Android の録音権限と画面共有の許可を使い、選んだアプリの再生音声をメモリ内で解析します。接続ページを離れるとキャプチャは停止します。通常のアニメーション表示は再生状態に連動します。

## ビルドとリリース

JDK 17 以上、Android SDK Platform 36、Build Tools 36.0.0 が必要です。`ANDROID_HOME` を設定するか、`local.properties` に `sdk.dir` を指定します。

```sh
./gradlew assembleDebug lintDebug
```

Windows では `gradlew.bat assembleDebug lintDebug` を実行します。GitHub Actions は main ブランチへの push とプルリクエストを検証します。アプリのバージョンと一致するタグ（`v1.9` など）を push すると、署名済み APK と SHA-256 チェックサムを公開します。

## ライセンスとクレジット

Relay 独自のソースは [GPL-3.0-only](../LICENSE) です。収録した AxionOS テーマは Apache 2.0 のままです。[サードパーティーに関する表示](../THIRD_PARTY_NOTICES.md)を参照してください。

Relay 独自のコードは OpenAI Codex が実装しました。はなさき　フブキは開発方針を示し、タブレット上で再生の問題を再現して、機能設計と実機テストを進めました。
