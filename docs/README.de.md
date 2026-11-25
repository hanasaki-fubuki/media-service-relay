# Media Service Relay

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · Deutsch

**QQ Music HD und kompatible Android-Player spielen im Hintergrund weiter.** Relay hält die Verbindung zum Wiedergabedienst beim App-Wechsel und beim Sperren des Bildschirms offen. Das Projekt entstand auf einem Xiaomi Pad 7 mit AxionOS, weil QQ Music HD im Hintergrund stumm wurde, obwohl der Fortschrittsbalken weiterlief.

[Signierte APK herunterladen](https://github.com/hanasaki-fubuki/media-service-relay/releases/latest)

## Erste Schritte

1. Installiere die APK unter **Android 14 oder neuer**. Die erste Testumgebung war ein Xiaomi Pad 7 mit AxionOS (Android 16).
2. Verbinde unter „Verbindungen“ das integrierte Profil für QQ Music HD. Wähle für andere Player unter „App hinzufügen“ einen kompatiblen Wiedergabedienst.
3. Mit „Verknüpfung erstellen“ kannst du die Verbindung herstellen und den Player öffnen. „Trennen“ löst die Verbindung. Für QQ Music HD gibt es außerdem eine eigene Aktion zum Beenden.

Die Kompatibilität hängt von den Diensten und Medienbefehlen des Players ab. Das Verbindungsprotokoll ist durchsuchbar. Wiedergabesteuerung und Spektrum lassen sich getrennt einschalten. Die Oberfläche unterstützt US-Englisch, britisches Englisch, vereinfachtes und traditionelles Chinesisch, Japanisch, Koreanisch und Deutsch.

## Optionale Berechtigungen

- **Benachrichtigungszugriff** wird beim Aktivieren des Spektrums oder der Wiedergabesteuerung angefragt. Relay liest damit den Wiedergabestatus und sendet Medienbefehle.
- **Live-Spektrum** nutzt die Android-Audioberechtigung und die Zustimmung zur Bildschirmfreigabe, um das Wiedergabeaudio der ausgewählten App im Arbeitsspeicher zu analysieren. Beim Verlassen der Verbindungsseite endet die Erfassung. Die gewöhnlichen animierten Balken folgen dem Wiedergabestatus.

## Build und Veröffentlichung

Benötigt werden JDK 17 oder neuer, Android SDK Platform 36 und Build Tools 36.0.0. Setze `ANDROID_HOME` oder trage `sdk.dir` in `local.properties` ein.

```sh
./gradlew assembleDebug lintDebug
```

Unter Windows funktioniert `gradlew.bat assembleDebug lintDebug`. GitHub Actions prüft Pushes auf main und Pull Requests. Ein zur App-Version passender Tag wie `v1.9` veröffentlicht eine signierte APK mit SHA-256-Prüfsumme.

## Lizenz und Mitwirkende

Der eigene Quellcode von Relay steht unter [GPL-3.0-only](../LICENSE). Für das eingebundene AxionOS-Design gilt Apache 2.0. Siehe [Hinweise zu Drittanbieterkomponenten](../THIRD_PARTY_NOTICES.md).

Der eigene Code von Relay wurde von OpenAI Codex geschrieben. はなさき　フブキ gab die Projektrichtung vor, reproduzierte das Wiedergabeproblem auf dem Tablet und begleitete den Funktionsentwurf und die Tests auf dem Gerät.
