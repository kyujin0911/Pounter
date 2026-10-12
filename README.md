# 파이프 계수 — Android 체험 앱

Kotlin / Jetpack Compose / Room / 화면 단위 MVI로 만든 오프라인 사진 검수 앱입니다.

**현재 상태: 수동 표시·보정·기록 앱 구현. 자동 계수 MVP는 미완료(검출 검증 대기).**

제공된 현장 사진 3장에서 초기 원형 검출이 바닥·반사를 과검출했습니다. 사용자 결정에 따라 정면 촬영 조건을 유지하며 추가 사진으로 검증한 뒤 자동 검출을 적용합니다. 앱은 `ValidationRequiredDetector`를 사용합니다. `HoughPipeDetector`는 androidTest에만 둔 독립 검증용 프로토타입이며 앱의 자동 계수기로 활성화하지 않았습니다. OpenCV는 테스트 APK에만 포함됩니다. 합성 원 테스트를 현장 정확도 근거로 사용하지 않습니다.

## 실행

Android Studio에서 이 폴더를 열고 Gradle Sync 후 `app`을 실행합니다. Android 8.0(API 26) 이상이 필요합니다. 최초 빌드는 Google Maven/Maven Central 접근이 필요하며, 앱 실행에는 인터넷·로그인·서버가 필요하지 않습니다.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

## 사용 흐름

1. 사진 선택 또는 시스템 카메라 촬영.
2. 필요하면 계수 영역을 조절하고 `수동 표시 시작`.
3. 단면 중심을 눌러 추가. 삭제 모드 또는 표시 목록으로 삭제하고 되돌리기로 마지막 보정 복구.
4. 확대·이동하며 각 단면과 표시를 대조한 뒤 최종 개수를 확정.
5. 기록 목록에서 다시 열거나 기록별 삭제.

사진과 보정은 앱 내부에 저장됩니다. 새 사진은 새 작업으로 저장하며 이전 기록을 덮어쓰지 않습니다. 앱 제거·앱 데이터 삭제 시 기록이 사라집니다. 클라우드 백업과 기기 전송 백업을 제외합니다. 원본 갤러리 사진은 수정하지 않습니다.

## 구조·문서

- UI: `CountAction → CountViewModel → StateFlow<CountUiState> → Compose`.
- 데이터: `CountRepository → Room + PhotoStore + PipeDetector`.
- 사진 좌표는 정규화해 확대·이동과 무관하게 저장합니다. 확정 기록은 읽기 전용이며 변경은 revision 검사로 보호합니다.
- [제품 요구사항](PRD.md), [작업 지침](AGENTS.md), [결정 기록](docs/decision_log.md), [환경](docs/environment.md), [검증](docs/verification.md).

로컬 현장 사진·분석 오버레이는 Git에 포함하지 않습니다. 원격 push·PR·병합·배포는 수행하지 않습니다.
