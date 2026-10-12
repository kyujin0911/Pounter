# Pounter — 파이프 계수 Android 체험 앱

사진 검수 흐름은 기존 코드 재사용·Jetpack Navigation 화면 분리로 구성되어 있다. 최신 요구사항은 [PRD](PRD.md), 채택된 비AI 검출은 [자동 계수 설계](docs/auto_counting.md), 화면은 [디자인 명세](docs/design_spec.md)를 따른다. 현재 수동 표시 흐름을 세 목적지로 분리했다. 자동 계수 MVP는 아직 완료하지 않았다. 추가 구현은 실제 요청 범위를 따른다.

Kotlin / Jetpack Compose / Room / 화면 단위 MVI로 만든 오프라인 사진 검수 앱입니다.

**기존 코드 상태: 수동 표시·보정·기록 앱 존재. 최신 OpenCV 복합 자동 계수는 미구현·미검증.**

제공된 현장 사진 3장에서 초기 원형 검출이 바닥·반사를 과검출했습니다. 사용자 결정에 따라 정면 촬영 조건을 유지하며 추가 사진으로 검증한 뒤 자동 검출을 적용합니다. 앱은 `ValidationRequiredDetector`를 사용합니다. `HoughPipeDetector`는 androidTest에만 둔 독립 검증용 프로토타입이며 앱의 자동 계수기로 활성화하지 않았습니다. OpenCV는 테스트 APK에만 포함됩니다. 합성 원 테스트를 현장 정확도 근거로 사용하지 않습니다.

## 실행 안내

Android Studio에서 이 폴더를 열고 Gradle Sync 후 `app`을 실행합니다. Android 8.0(API 26) 이상이 필요합니다. 최초 빌드는 Google Maven/Maven Central 접근이 필요하며, 앱 실행에는 인터넷·로그인·서버가 필요하지 않습니다.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

## 기존 코드 사용 흐름 — 최신 목표 흐름은 PRD 참조

1. 사진 선택 또는 시스템 카메라 촬영.
2. 필요하면 계수 영역을 조절하고 `수동 표시 시작`.
3. 단면 중심을 눌러 추가. 삭제 모드 또는 표시 목록으로 삭제하고 되돌리기로 마지막 보정 복구.
4. 확대·이동하며 각 단면과 표시를 대조한 뒤 최종 개수를 확정.
5. 기록 목록에서 다시 열거나 기록별 삭제.

사진과 보정은 앱 내부에 저장됩니다. 새 사진은 새 작업으로 저장하며 이전 기록을 덮어쓰지 않습니다. 앱 제거·앱 데이터 삭제 시 기록이 사라집니다. 클라우드 백업과 기기 전송 백업을 제외합니다. 원본 갤러리 사진은 수정하지 않습니다.

## 기존 코드 구조·문서

- 화면 이동: 타입 안전 HomeRoute / WorkRoute(taskId) / RecordRoute(taskId), Navigation Compose, entry별 ViewModel.
- UI: `CountAction → CountViewModel → StateFlow<CountUiState> → Compose`. NavController는 UI 계층에서만 사용.
- 데이터: `CountRepository → Room + PhotoStore + PipeDetector`.
- 사진 좌표는 정규화해 확대·이동과 무관하게 저장합니다. 확정 기록은 읽기 전용이며 변경은 revision 검사로 보호합니다.
- [제품 요구사항](PRD.md), [작업 지침](AGENTS.md), [결정 기록](docs/decision_log.md), [환경](docs/environment.md), [검증](docs/verification.md).

로컬 현장 사진·분석 오버레이는 Git에 포함하지 않습니다. 원격 push·PR·병합·배포는 해당 사용자 요청 범위에서 수행합니다.

앱/Gradle 프로젝트 이름: `Pounter`. namespace/applicationId/기본 패키지: `io.github.kyujin0911.pounter`. 앱 ID 변경 전 설치 앱의 기록은 자동 이전되지 않으며 기존 설치·데이터를 삭제하지 않는다.

## 개발 협업·Git

[Git 운영 규칙](docs/git_workflow.md)에서 브랜치·커밋·PR·병합 흐름을 확인합니다. [PR 템플릿](.github/pull_request_template.md)과 [커밋 템플릿](.gitmessage)을 사용하며, commit.template 로컬 적용은 운영 문서의 선택 명령을 따릅니다.

작업/버그 Issue는 [작업 제안·계획](.github/ISSUE_TEMPLATE/01-work.md), [버그 보고](.github/ISSUE_TEMPLATE/02-bug.md) 템플릿을 사용합니다. 작성·종료 기준은 [Git 운영 규칙](docs/git_workflow.md#issue-규칙과-템플릿)을 따릅니다.
