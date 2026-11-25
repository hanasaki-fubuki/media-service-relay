# Media Service Relay

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · 한국어 · [Deutsch](README.de.md)

**QQ Music HD와 호환 Android 플레이어가 백그라운드에서도 계속 재생되도록 합니다.** Relay는 재생 서비스 연결을 유지해 앱을 전환하거나 화면을 잠근 뒤에도 재생이 이어지게 합니다. Xiaomi Pad 7의 AxionOS에서 QQ Music HD를 백그라운드로 보내면 진행 막대만 움직이고 소리가 끊기는 문제에서 출발했습니다.

[서명된 APK 다운로드](https://github.com/hanasaki-fubuki/media-service-relay/releases/latest)

## 시작하기

1. **Android 14 이상**에서 APK를 설치하세요. 최초 테스트는 AxionOS(Android 16)가 설치된 Xiaomi Pad 7에서 진행했습니다.
2. ‘연결’에서 내장 QQ Music HD 프로필을 연결하세요. 다른 플레이어는 ‘앱 추가’에서 호환되는 재생 서비스를 선택하세요.
3. ‘바로가기 만들기’를 사용하면 한 번에 연결하고 플레이어를 열 수 있습니다. ‘연결 해제’로 연결을 끊을 수 있으며, QQ Music HD에는 별도의 종료 버튼도 있습니다.

호환 여부는 플레이어가 제공하는 서비스와 미디어 명령에 따라 달라집니다. 연결 로그를 검색할 수 있고, 재생 조작과 스펙트럼을 각각 켜고 끌 수 있습니다. 미국 영어, 영국 영어, 중국어 간체와 번체, 일본어, 한국어, 독일어를 지원합니다.

## 선택 권한

- **알림 접근**은 재생 상태를 읽고 미디어 명령을 보내는 데 사용합니다. 스펙트럼이나 재생 조작을 켤 때 권한을 요청합니다.
- **실시간 스펙트럼**은 Android의 녹음 권한과 화면 공유 동의를 사용해 선택한 앱의 재생 오디오를 메모리에서 분석합니다. 연결 화면을 벗어나면 캡처를 중지합니다. 일반 애니메이션 막대는 재생 상태에 따라 표시됩니다.

## 빌드 및 릴리스

JDK 17 이상, Android SDK Platform 36, Build Tools 36.0.0이 필요합니다. `ANDROID_HOME`을 설정하거나 `local.properties`에 `sdk.dir`을 지정하세요.

```sh
./gradlew assembleDebug lintDebug
```

Windows에서는 `gradlew.bat assembleDebug lintDebug`를 실행하세요. GitHub Actions는 main 브랜치에 푸시된 변경 사항과 풀 리퀘스트를 검사합니다. 앱 버전과 일치하는 태그(예: `v1.9`)를 푸시하면 서명된 APK와 SHA-256 체크섬을 릴리스에 올립니다.

## 라이선스와 크레딧

Relay 자체 소스는 [GPL-3.0-only](../LICENSE)로 배포합니다. 포함된 AxionOS 테마에는 Apache 2.0이 적용됩니다. [서드 파티 고지](../THIRD_PARTY_NOTICES.md)를 참고하세요.

Relay 자체 코드는 OpenAI Codex가 작성했습니다. はなさき　フブキ는 프로젝트 방향을 잡고, 태블릿에서 재생 문제를 재현하며, 기능 설계와 실제 기기 테스트를 이끌었습니다.
