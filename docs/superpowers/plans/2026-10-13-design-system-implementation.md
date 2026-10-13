# Pounter Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 아이콘 기반 Material 3 테마와 사진 검수 컴포넌트를 앱에 적용하면서 기존 보정·저장·복원·읽기 전용 동작을 보존한다.

**Architecture:** 기존 MaterialTheme와 화면별 UI 책임을 유지한다. 테마 → 공통 업무 표현 → 홈 → 사진/보정 → 완료 기록 순서로 적용하며 Repository·ViewModel·Navigation 계약은 변경하지 않는다. 기본 Material 컴포넌트를 직접 사용하고 반복되는 업무 의미만 추출한다.

**Tech Stack:** Kotlin 2.1.21, Compose UI/Foundation 1.8.3, Material 3 1.3.2, Navigation Compose 2.9.8, JDK 17, 기존 JUnit4/Compose UI Test, 기존 Spotless/ktfmt.

**Spec:** [코드 컨벤션의 패키지 관리](../../code_conventions.md#재사용-ui의-패키지-관리--2026-10-13-추가), [Compose 컴포넌트 규칙](../../compose_components.md), [디자인 시스템 0.2](../../design_system.md), [비교 시안](../../design_system_board.html), [화면 명세](../../design_spec.md), [PRD](../../../PRD.md). HTML은 레이아웃 참고이며 dp/sp·상태 로직·실제 사진 검증 기준은 명세를 따른다.

## Global Constraints

- '동적 색상은 이번 브랜드 기준의 기본값으로 사용하지 않는다.'
- '주요 버튼 높이 최소 56dp, 기타 조작 터치 영역 최소 48×48dp.'
- '간격 척도: 4 / 8 / 12 / 16 / 24 / 32dp.' 화면 16dp, 카드 16dp, 시작 영역 20dp, 그룹 16dp, 섹션 24dp.
- '중요한 상태·오류·보존 안내를 12sp 이하로 축소하지 않는다.' 시스템 글꼴, 한국어 fallback. 명세 4절의 크기/행간/굵기를 그대로 적용한다.
- '중요한 완료·오류 정보는 Snackbar만으로 전달하지 않는다.'
- '로고의 두꺼운 흰색 링을 검출 마커에 그대로 복제하지 않는다.' 마커의 자동/수동 구별·목록·이중 테두리 유지.
- '작은 높이·글꼴 200%에서는 고정 하단바가 사진/도구를 압박하면 스크롤 가능한 배치로 전환한다.'
- 명세 3절 라이트/다크 HEX와 전경/배경 쌍 전체를 사용한다. 사진 배경 #111827, active 글자 대비 내부 목표 4.5:1, primary #164EE2/#B7C8FF.
- _uiState/uiState·역할/단위 이름 규칙 유지. 직렬화 key·Room·Navigation 인자·앱 ID·사진 보존 형식 변경 금지.
- 새 라이브러리/모듈/DI/UseCase/전역 상태·새 탭·스와이프 삭제·검출기 구현·런처 아이콘 변경은 제외한다. 사진·외부 로고 원본·개인 경로·캡처 이미지는 커밋하지 않는다.
- 문안 충돌은 컴포넌트 계약/시안/현재 확정 대화상자에 맞춰 'N개 확인하고 확정'으로 통일한다. 실제 저장 성공 후에만 '사용자 확인 완료'를 표시한다.
- UI 위치/승격/의존 방향/가시성은 코드 컨벤션의 재사용 UI 패키지 관리 절을 따른다. 일반 디자인 UI는 core/ui/components, 업무 공유 UI는 feature/shared, 기능 전용 UI는 해당 feature. 패키지 이름만으로 모듈 경계가 강제된다고 보고하지 않는다.
- 현재/후속 UI는 Compose 컴포넌트 규칙의 추출 기준·Modifier 루트 1회·값/이벤트·슬롯 수명을 따른다. 새 상태/슬롯 API·파일 배치는 그 문서 4절이 기준이며 컴포넌트 안에서 VM/Repository/NavController를 사용하지 않는다.

## Review Focus

- 분석 전과 분석 결과 0개: 미분석은 '— / 분석 전', 0개는 '0개 / 확인 전'; 자동 완료 처리 금지. Task 2/4 테스트.
- 저장 실패 중 연속 입력·확정·복귀: 보정 개수·선택 모드를 보존하고 재시도만 허용되는 기존 보호 조건 유지. Task 4 및 기존 NavigationUiTest.
- 작은 높이·글꼴 200%·300개 근처: 단위/상태 미잘림, 확정·재시도·보정 도구 도달, 사진 영역이 사라지지 않음. Task 4/6 테스트.
- 확대 중 보정/화면 크기 변경: 같은 작업의 확대 위치를 보정마다 리셋하지 않고 표시 좌표 변환·겹친 표시 대체 선택 유지. Task 4 및 기존 ModelTest/AppUiTest.
- 완료 기록의 사진 읽기 실패: 개수·확인 시각 유지, 편집/표시 삭제 진입 금지, 기록 삭제는 확인 후 기존 경로로 실행. Task 5 테스트.

---

## 기준 코드와 실행 준비

계획 작성 브랜치: codex/brand-design-rules, 디자인 문서 기준 969ff2b1f3ab364ebef9b5d5d4b78caa17866864. 작업 트리에는 사용자 미추적 파일이 있으므로 자동 stash/reset/clean하지 않는다.

현재 checkout은 ui/PipeApp.kt로 화면이 합쳐져 있지만, 저장된 origin/main의 64f47000626ad7f1a35bc48363e05b2bc3655b0d에는 feature/home·count·record·shared와 core/ui/theme으로 책임 분리가 끝나 있다. **아래 파일 경로·인터페이스는 이 구조 변경과 디자인 문서를 결합한 상태 기준이다.** 오늘 원격 서버 조회·fetch·merge·코드 실행은 하지 않았다.

실행 승인 후 using-git-worktrees로 이 채팅의 적합한 활성 worktree를 확인/재사용하거나 문서 브랜치 기준의 격리 작업 공간을 만든다. 그 공간에서 저장된 구조 변경 SHA를 일반 merge하여 문서와 결합한다. 문서 충돌은 구조 변경의 실제 코드/컨벤션·Navigation 책임과 본 디자인 문서 연결을 모두 보존해 해결하며 일괄 ours/theirs로 덮지 않는다. 현재 사용자 checkout을 임의 전환하지 않는다. 최신 main에 이미 두 변경이 있으면 새 기능 브랜치에서 시작하며 중복 merge하지 않는다. 기준 파일/서명 차이가 생기면 계획을 먼저 조정한다. push/PR/병합/삭제는 별도 실제 요청 범위다.

## 파일 책임 지도

경로 축약: `M = app/src/main/java/io/github/kyujin0911/pounter`, `U = app/src/test/java/io/github/kyujin0911/pounter`, `I = app/src/androidTest/java/io/github/kyujin0911/pounter`. 아래 목록의 모든 경로는 이 접두사에 연결한다.

| 작업 | 만들거나 변경할 파일 | 책임 |
|---|---|---|
| 1 | M/core/ui/theme/{PipeTheme,PipeColors,PipeTypography,PipeShapes,PipeDimensions}.kt | 기존 테마 확장. 역할 색·문자·형태·간격 값 |
| 2 | M/feature/shared/{CountSummary,TaskHeader,ValidationNotice}.kt; 새 StatusLabel.kt, RecoveryNotice.kt; M/core/ui/components/{NoticeCard,MessageCard,ProgressCard,ConfirmDialog}.kt | 공통 업무 상태·복구 안내. 동작은 콜백으로만 전달 |
| 3 | M/feature/home/HomeScreen.kt; 새 PhotoInputCard.kt, RecordCard.kt | 입력과 기록 목록 표현 |
| 4 | M/feature/count/CountWorkScreen.kt; M/feature/shared/PhotoPanel.kt; 새 M/feature/count/CorrectionToolbar.kt, ConfirmAction.kt; 새 M/feature/shared/PhotoLayout.kt | 보정 조작·사진 가용 크기·확정 접근 |
| 5 | M/feature/record/RecordScreen.kt; M/feature/shared/DetailPhotoActions.kt; M/core/ui/components/ConfirmDialog.kt | 읽기 전용 기록과 분리된 삭제 |
| 6 | I/{AppUiTest,NavigationUiTest,LargeFontUiTest}.kt; app/src/debug/java/io/github/kyujin0911/pounter/preview/DesignPreviews.kt; docs/{verification,design_spec,design_system,compose_components,code_conventions}.md | 통합 회귀·Preview·실제 화면 확인과 적용 상태 기록 |

설계의 PhotoViewport/PhotoDisplayTools는 별도 래퍼를 만들지 않고 기존 PhotoPanel의 사진·보기 도구로 구현한다. OperationNotice는 ProgressCard, DestructiveDialog는 ConfirmDialog를 재사용한다. 테마 token 접근을 위해 새 CompositionLocal 프레임워크를 만들지 않는다.

일반 UI의 실제 이동 목록은 MessageCard/ProgressCard/ConfirmDialog → core/ui/components이다. 기능 소비자와 테스트는 새 import를 사용하고 옛 파일/forwarding wrapper를 남기지 않는다. 신규 테스트/Preview 경로와 runner FQCN도 아래 파일 지도/각 Task의 경로를 사용한다.

NoticeCard는 실제 네 소비자(진행·검증 대기·오류·복구)의 공통 표면/본문/조작 슬롯만 맡는다. 기능 내부 CorrectionToolbar/ConfirmAction은 feature/count에 둔다. PhotoLayout은 계수·완료 두 화면이 사용하는 helper라 shared에 둔다. Task2의 ComponentContractUiTest가 재사용 API의 루트·슬롯 계약을 검증한다. Task6의 Preview는 app/src/debug/java/io/github/kyujin0911/pounter/preview/DesignPreviews.kt에만 작성한다.

### Task 1: 브랜드 테마 적용

**Files:** Create M/core/ui/theme/PipeColors.kt, PipeTypography.kt, PipeShapes.kt, PipeDimensions.kt; Modify M/core/ui/theme/PipeTheme.kt; Test Create U/core/ui/theme/DesignTokensTest.kt.

**Interfaces:** Consumes 기존 `PipeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`. Produces 동일 서명, `internal val PipeLightColors: ColorScheme`, `PipeDarkColors: ColorScheme`, `PipeTypography: Typography`, `PipeShapes: Shapes`, `object PipeDimensions`의 `screenInset=16.dp`, `cardInset=16.dp`, `inputCardInset=20.dp`, `controlGap=8.dp`, `groupGap=16.dp`, `sectionGap=24.dp`, `primaryActionMinHeight=56.dp`, `touchTargetMinSize=48.dp`, `readingMaxWidth=600.dp`.

- [x] **Step 1: 실행 기반 결합·기존 검증** — 위 격리/merge 절차 후 `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck` 실행. 기존 실패가 있으면 디자인 변경 전 원인·범위를 기록하고 기대값을 약화하지 않는다.
- [x] **Step 2: 실패 테스트 작성** — DesignTokensTest의 `activeTextPairsMeetContrastInBothThemes`에서 명세 8절의 54쌍을 표로 순회하고 sRGB 대비를 계산한다. `assertTrue("$theme $foreground/$background: $ratio", ratio >= 4.5)`로 검사한다. 기본색을 그대로 남기면 primary를 바꿔도 컨테이너 쌍 누락을 검출하도록 양 모드의 surfaceContainer 계열을 포함한다. 대비 helper `private fun contrast(foreground: Color, background: Color): Double`은 테스트 파일에만 둔다.
- [x] **Step 3: RED 확인** — `./gradlew testDebugUnitTest --tests '*DesignTokensTest'`. 새 테마 식별자 미정의로 컴파일 실패 또는 실제 대비 실패를 확인한다. 도구/네트워크 실패를 RED로 쓰지 않는다.
- [x] **Step 4: 테마 구현** — 명세 3절 모든 역할·surfaceTint·scrim과 4절 7개 Typography/5개 Shapes를 위 파일에 선언하고 PipeTheme에 전달. Material 3 1.3.2에서 제공되지 않는 API를 새 버전으로 올려 해결하지 않는다. 특별 사진 색은 PhotoPanel에 의미 있는 이름으로 유지한다.
- [x] **Step 5: GREEN 확인** — Step 3 명령과 `./gradlew assembleDebug lintDebug spotlessCheck`. 새 파일만 필요한 포맷 적용. 기본 버튼의 포커스/비활성 표현은 Material 구현을 유지한다.
- [x] **Step 6: diff 리뷰·커밋** — 테마 파일과 DesignTokensTest만 명시적으로 stage; `git diff --cached --check`; `git commit -m 'feat(design): 브랜드 색상과 글꼴·형태 토큰 적용'`.

### Task 2: 개수·상태·복구 컴포넌트

**Files:** Move M/feature/shared/MessageCard.kt, ProgressCard.kt, ConfirmDialog.kt → M/core/ui/components/의 동명 파일; Modify M/feature/shared/CountSummary.kt, TaskHeader.kt, ValidationNotice.kt; Modify M/feature/home/HomeScreen.kt, feature/count/CountWorkScreen.kt, feature/record/RecordScreen.kt의 이동 심벌 import; Create M/feature/shared/StatusLabel.kt, RecoveryNotice.kt; Create M/core/ui/components/NoticeCard.kt; Test Create I/feature/shared/DesignStatusUiTest.kt, I/core/ui/components/ComponentContractUiTest.kt, I/feature/shared/DesignFixtures.kt.

**Interfaces:** Consumes Task 1 테마와 core.model.CountTask. Produces `CountSummary(task: CountTask, modifier: Modifier = Modifier)`, `StatusLabel(status: TaskStatus, modifier: Modifier = Modifier)`와 `internal fun taskStatus(task: CountTask): TaskStatus`, `RecoveryNotice(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier)`. TaskStatus/NoticeTone/NoticeCard 및 TaskHeader/ProgressCard/MessageCard/ValidationNotice 서명은 Compose 컴포넌트 규칙 4절 그대로 적용한다. 기존 필수 인자는 유지하고 첫 선택 인자로 modifier를 제공한다. 모든 변경 호출부는 named arguments로 정리한다. Fixture `internal fun designTask(markerCount: Int = 0, analyzed: Boolean = false, confirmed: Boolean = false): CountTask`, `internal fun designBitmap(): Bitmap`.

- [x] **Step 1: 일반 UI 패키지 이동** — 구조 변경 결합 상태에서 MessageCard/ProgressCard/ConfirmDialog를 core/ui/components로 이동, package 선언·Home/Count/Record import·테스트의 해당 import를 갱신한다. 아직 문안/서명/상태 동작은 바꾸지 않는다. NoticeCard 새 파일은 계약 구현 단계에 만든다. private helper 유지, 모듈 내 UI는 internal을 기본으로 하고 타입/함수 가시성 일치 확인.
- [x] **Step 2: 이동만의 회귀·커밋** — `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck`, 기존 AppUiTest/NavigationUiTest 실행. `rg -n 'import io\.github\.kyujin0911\.pounter\.feature\.shared\.(MessageCard|ProgressCard|ConfirmDialog)' app/src` 결과 0건, core/ui/components에서 feature/data/navigation/core.model import 0건, theme→components import 0건을 확인한다. 패키지 구분은 import/diff 리뷰이고 별도 강제 도구는 추가하지 않는다. 이동 파일·제거된 원본·소비자 import·테스트 import만 stage하고 `git commit -m 'refactor(ui): 일반 공유 UI를 디자인 컴포넌트 패키지로 이동'`.
- [x] **Step 3: 실패 UI 테스트 작성** — createComposeRule+PipeTheme 사용. Fixture는 id='design-fixture', photoName='synthetic.png', width=900, height=650, createdAt=0L, confirmedAt은 confirmed이면 1000L; markerCount개 고유 manual Marker를 20열 격자에 .05~.95 정규화 좌표·radius=.01f로 생성. bitmap은 900×650 중립 합성 그림; 파일·DB 접근 없음. `unanalyzedIsNotZero`는 analyzed=false에서 '분석 전'·'—' 존재/‘0개’ 없음, `analyzedZeroIsNotConfirmed`는 analyzed=true·0에서 '0개'·'확인 전'/완료 없음, `confirmedSummaryKeepsTimestampAndHasNoFalseAutomaticClaim`은 12개 완료·시각·수동 출처를 검사. RecoveryNotice 테스트는 '저장되지 않은 변경' 지속/재시도 클릭 1회/onRetry 1회.

`unanalyzedIsNotZero`의 핵심 assertions(각 UI 테스트 class에서 `rule = createComposeRule()`):
```kotlin
rule.setContent { PipeTheme { CountSummary(designTask()) } }
rule.onNodeWithText("분석 전").assertExists()
rule.onNodeWithText("—").assertExists()
rule.onNodeWithText("0개").assertDoesNotExist()
rule.onNodeWithText("사용자 확인 완료").assertDoesNotExist()
```

ComponentContractUiTest에 `modifierIsAppliedExactlyOnceToNoticeRoot`, `toneAndActionsChangesKeepContentRememberState`, `noticeSlotsAreOptionalAndSingle`을 작성한다. 아래 첫 테스트에서 clicks는 mutable Int 카운터다.
```kotlin
rule.setContent {
    PipeTheme {
        NoticeCard(
            tone = NoticeTone.INFORMATION,
            modifier = Modifier.testTag("notice-root").width(240.dp).clickable { clicks++ },
        ) { Text("본문") }
    }
}
rule.onAllNodesWithTag("notice-root").assertCountEquals(1)
rule.onNodeWithTag("notice-root").assertWidthIsEqualTo(240.dp).performClick()
assertEquals(1, clicks)
```
두 번째 테스트는 Notice 본문 안 remember 카운터를 버튼으로 1 증가시킨 뒤, 외부 tone을 ERROR로 전환하고 actions 슬롯을 추가/제거한다. 매번 '본문 횟수 1' 노드는 정확히 하나여야 한다. 세 번째는 title/actions=null에서 본문만 존재하고, actions에 실제 TextButton을 제공한 경우 표시·클릭 callback 1회 및 본문 위치의 안정성을 검사한다. remember 테스트는 색 변경 때 content를 두 분기에 배치하는 잘못된 구현을 검출한다.
- [x] **Step 4: RED 확인** — `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.feature.shared.DesignStatusUiTest,io.github.kyujin0911.pounter.core.ui.components.ComponentContractUiTest'`. 기존 '0'/‘확정됨’ 문안과 새 계약 불일치 또는 새 함수 미정의가 실패 원인이어야 한다.
- [x] **Step 5: 공통 UI 구현** — CountSummary는 number/status/단위가 줄바꿈 가능하게 구성하고 StatusLabel은 클릭 없는 텍스트+보조 아이콘. 미분석/0/완료 규칙을 따른다. count-value는 분석 후 'N개', 미분석 '—'; 설명은 완료면 '확인한 개수', 아니면 '현재 개수'다. 기존 CountTask 계산을 재구현하지 않는다. ProgressCard는 중립 색·접근성 상태; ValidationNotice는 중립 안내·현재 수동 기능 유지; RecoveryNotice는 errorContainer·명확한 재시도. MessageCard는 실제 오류 전용으로 유지하며 임의 문자열로 오류/안내를 판정하지 않는다. 주요 상태 bodyMedium 이상, heading/stateDescription/liveRegion은 의미가 맞는 요소에만 설정한다.
- [x] **Step 6: NoticeCard 계약 구현·소비자 연결** — Compose 컴포넌트 규칙 4절의 NoticeTone/NoticeCard 선언을 구현하고 ProgressCard·ValidationNotice·MessageCard·RecoveryNotice에서 사용한다. modifier는 Surface 루트에 1회, content는 안정된 Column 위치에 1회, title/actions null이면 해당 영역/간격을 만들지 않는다. 각 업무 함수는 문구·callback만 구성하고 Action 변환은 화면에 둔다. TaskHeader도 전달 modifier를 루트 Row에 1회 적용한다.
- [x] **Step 7: GREEN 확인** — Step 4 명령 재실행. 라이트·다크 300개 summary 캡처에서 개수/단위/상태 미잘림을 관찰하되 캡처를 Git에 넣지 않는다.
- [x] **Step 8: 커밋** — 위 core/shared 파일과 해당 소비자 import, 세 테스트 파일만 stage·diff 검사; `git commit -m 'feat(design): 상태와 슬롯 기반 안내 컴포넌트 적용'`.

### Task 3: 홈의 입력·기록 패턴

**Files:** Modify M/feature/home/HomeScreen.kt; Create M/feature/home/PhotoInputCard.kt, RecordCard.kt; Test Create I/feature/home/HomeDesignUiTest.kt; Modify I/LargeFontUiTest.kt.

**Interfaces:** Consumes Task 1/2. Produces `PhotoInputCard(canStart: Boolean, onCamera: () -> Unit, onPick: () -> Unit, modifier: Modifier = Modifier)`, `RecordCard(task: CountTask, canOpen: Boolean, onOpen: (CountTask) -> Unit, modifier: Modifier = Modifier)`. HomeScreen 기존 서명 유지.

- [x] **Step 1: 실패 테스트 작성** — `inputActionsDispatchOnceAndDisableDuringImport`는 촬영·선택 콜백 각 1회, PhotoImportUiState(isImporting=true)에서 버튼 비활성/기록 열기 콜백 0회. `recordsSeparateUnanalyzedZeroAndConfirmed`는 fixture 3개를 고유 ID로 copy하여 '분석 전', '0개/확인 전', '사용자 확인 완료'를 각 record-id 태그 내부에서 검사. 기존 darkHomeAtTwoHundredPercentKeepsActionsReachable의 촬영·선택·검증 대기 검사 유지.

가져오기 중 변형의 assertions:
```kotlin
rule.onNodeWithText("사진 촬영").assertIsNotEnabled()
rule.onNodeWithText("사진 선택").assertIsNotEnabled()
assertEquals(0, openedRecords.size)
```
`openedRecords`는 테스트에서 정의한 `mutableListOf<CountTask>()`이며 HomeScreen.onOpen은 목록에 추가하는 콜백이다. 첫 정상 변형과 가져오기 변형은 하나의 `mutableStateOf(PhotoImportUiState())`로 변경하고 setContent를 중복 호출하지 않는다.
- [x] **Step 2: RED 확인** — `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.feature.home.HomeDesignUiTest,io.github.kyujin0911.pounter.LargeFontUiTest'`.
- [x] **Step 3: 홈 구현** — 중립 시작 카드·파란 촬영/Outlined 선택, 안내 '한 묶음의 모든 단면이 보이도록 정면에서 촬영하세요.'와 검증 대기/보존 설명. RecordCard는 전체 열기 하나·surfaceContainerLow·날짜·수동/자동 출처·상태. 기존 정렬/Repository 관찰·onOpen 조건 유지. 홈 제목은 기존 문자열 Pounter 유지하며 새 원본 로고 에셋 추가는 하지 않는다. content testTag와 기존 탐색 문맥 보존.
- [x] **Step 4: GREEN 확인** — Step 2 명령. 360dp 폭·fontScale 2.0에서 버튼/기록/안내에 스크롤 도달. 단위/날짜 말줄임 금지, 중요 설명 14sp 이상.
- [x] **Step 5: 커밋** — 홈 파일과 관련 테스트만 stage·diff 검사; `git commit -m 'feat(design): 촬영 입력과 기록 카드의 우선순위 정리'`.

### Task 4: 사진·보정·확정 접근성

**Files:** Modify M/feature/count/CountWorkScreen.kt, M/feature/shared/PhotoPanel.kt; Create M/feature/count/CorrectionToolbar.kt, ConfirmAction.kt; Create M/feature/shared/PhotoLayout.kt; Test Create U/feature/shared/PhotoLayoutTest.kt, I/feature/count/CountDesignUiTest.kt, I/feature/shared/PhotoPanelDesignUiTest.kt; Modify I/AppUiTest.kt, I/NavigationUiTest.kt의 조작 위치 helper만 필요 시 갱신.

**Interfaces:** Consumes Task 1/2와 기존 CountUiState/CountAction/EditMode. Produces `CorrectionToolbar(mode: EditMode, canEdit: Boolean, canUndo: Boolean, onModeChange: (EditMode) -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier)`, `ConfirmAction(count: Int, canConfirm: Boolean, isSaving: Boolean, onConfirm: () -> Unit, modifier: Modifier = Modifier)`.

PhotoPanel은 Compose 컴포넌트 규칙 4절 서명을 적용한다. 필수 인자 다음 첫 선택 인자는 modifier, 그 뒤 viewportHeight와 optional callbacks다. 두 상세 화면은 계산된 height를 전달하고 기존 named callback을 보존한다. PhotoLayout에는 `internal fun photoViewportHeightDp(contentHeightDp: Float, contentWidthDp: Float, photoAspectRatio: Float): Float`, `internal fun shouldPinConfirmAction(contentHeightDp: Float, fontScale: Float): Boolean`만 둔다. inputs는 inset 적용 후 양의 크기/정상 사진 비율이다.

- [x] **Step 1: 실패 레이아웃 테스트 작성** — height 정책은 `min(width/aspect, height*0.45, 480)`을 `min(180,height)` 이상으로 제한한다. 800/360/1.5 → 240f, 400/360/1.5 → 180f, 120/360/1.5 → 120f를 .01f 허용으로 검사. pin 조건은 높이 >=720dp이고 fontScale <1.5f. 800/1→true, 719/1→false, 800/2→false. 이는 명세의 작은 화면 대응을 구현하기 위한 초기 선택이며 실화면 검증 후 조정 시 테스트·명세 함께 갱신한다.

PhotoLayoutTest의 `shortHeightAndLargeFontUseScrollableConfirm` assertions:
```kotlin
assertEquals(180f, photoViewportHeightDp(400f, 360f, 1.5f), .01f)
assertEquals(120f, photoViewportHeightDp(120f, 360f, 1.5f), .01f)
assertFalse(shouldPinConfirmAction(719f, 1f))
assertFalse(shouldPinConfirmAction(800f, 2f))
assertTrue(shouldPinConfirmAction(800f, 1f))
```
- [x] **Step 2: 실패 상태 UI 테스트 작성** — `failedSaveKeepsCountAndRequiresRetry`는 12개·unsavedChanges=true에서 개수 유지/재시도 클릭→CountAction.RetrySave 1회/확정 비활성/보정 비활성 검사. `savingNeverShowsConfirmedLabel`은 Operation.SAVING·confirmedAt=null에서 완료 없음. `zeroReviewStillRequiresConfirmation`은 analyzed=true·0에서 완료 없음과 명시적 confirm dialog 필요 검사. `largeFontReviewKeepsConfirmRetryAndUndoReachable`는 fontScale2.0에서 실제 표시·터치 크기/텍스트 경계가 부모 안인 것을 검사한다.

실패 상태에서 count-value(Task 2의 CountSummary 숫자에 부여)와 confirm-action(ConfirmAction Button에 부여) 태그를 사용한다:
```kotlin
rule.onNodeWithTag("count-value").assertTextEquals("12개")
rule.onNodeWithTag("confirm-action").assertIsNotEnabled()
rule.onNodeWithText("저장 재시도").performClick()
assertEquals(listOf(CountAction.RetrySave), dispatched)
```
`dispatched`는 테스트가 정의한 `mutableListOf<CountAction>()`, 화면 dispatch는 `dispatched::add`다. retry가 작은 화면 밖이면 먼저 content로 해당 문구를 스크롤한다. enabled/위치 검사 전에 scroll로 실제 조작 도달을 확인한다.

CountDesignUiTest의 `modeRemainsCallerControlled`은 toolbar의 mode=VIEW와 onModeChange 수신 목록을 고정해 놓고 '추가'를 클릭한다. 요청은 ADD 1회, 외부 값 변경 전에는 '이동' selected=true와 '추가' selected=false를 검사한다. 이후 호출자가 mode=ADD로 변경하면 선택이 반영된다. canEdit=false에서는 추가 요청이 없어야 한다. 이는 내부에서 부모 상태를 복제해 고치는 구현을 검출한다.
- [x] **Step 3: 실패 사진 UI 테스트 작성** — `correctionKeepsZoomForSameTask`는 합성 bitmap에서 확대 후 같은 id의 task를 보정하여 recomposition하고 확대 상태가 그대로인지 semantics로 검사. PhotoPanel 사진 Canvas에 `photo-viewport` 태그와 stateDescription '확대 N배'를 제공한다. `resizedViewportAddsAtCorrectNormalizedPoint`는 240/180dp viewport에서 동일 단면 위치 탭 콜백의 x/y가 .01 이내로 같은지 검사. `overlapSelectionAndAlternativePositionControlsRemainAvailable`는 겹친 marker 선택 대화상자와 기존 위치 조절/이 위치에 추가 경로를 검사한다. 색·사진만 보고 자동/수동을 추정하지 않는다.
- [x] **Step 4: RED 확인** — `./gradlew testDebugUnitTest --tests '*PhotoLayoutTest'`; `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.feature.count.CountDesignUiTest,io.github.kyujin0911.pounter.feature.shared.PhotoPanelDesignUiTest'`.
- [x] **Step 5: 사진 레이아웃·도구 구현** — BoxWithConstraints/LocalDensity로 inset 후 크기와 fontScale을 읽는다. summary/사진/보기 도구/보정/확정 순서. pin true면 Scaffold.bottomBar에 ConfirmAction, false면 content의 scroll item에 배치하며 동시에 두 번 표시하지 않는다. PhotoPanel modifier는 전체 Column 루트에 1회, Canvas만 전달 높이로 변경하고 geometry/pointerInput/marker ID·집계·pan 유지·대체 입력을 보존한다. 사진 배경 중립색, 기존 마커색/원형·사각형·이중 테두리는 유지한다. 하늘색 단독 표시로 교체하지 않는다. CorrectionToolbar/ConfirmAction도 modifier를 각각 루트에 적용하며 task/UI 상태를 내부 캐시로 복제하지 않는다.
- [x] **Step 6: 상태·동작 연결** — canEdit 기존 `!isBusy && !unsavedChanges`와 task의 confirmed/analyzed 제한을 유지한다. ConfirmAction의 enabled도 기존 규칙, 클릭은 기존 confirm dialog만 연다. 복구 시 RecoveryNotice로 RetrySave 전달; 저장 중은 기존 상태의 의미까지만 문구화한다. 연산 종류를 문자열이나 확인 대화상자 닫힘으로 추정하지 않는다. 번호·영역·사진 교체·미제공 자동 상태·미저장 뒤로 가기 보호를 유지한다.
- [x] **Step 7: GREEN·기존 회귀** — Step 4 명령과 `./gradlew testDebugUnitTest --tests '*CountViewModelTest' --tests '*ModelTest'`; AppUiTest/NavigationUiTest 실행. 고정 CTA 도입 시 scroll helper는 대상이 이미 표시되면 클릭, 아니면 content로 scroll하도록 조정하고 기존 저장/좌표/assertRoute 기대를 유지한다. 문안 변경에만 기대 텍스트 갱신.
- [x] **Step 8: 커밋** — 위 UI/레이아웃/테스트 파일만 stage·diff 검사; `git commit -m 'feat(design): 사진 보정 도구와 확정 동작의 접근성 개선'`.

### Task 5: 읽기 전용 완료 기록

**Files:** Modify M/feature/record/RecordScreen.kt, M/feature/shared/DetailPhotoActions.kt, M/core/ui/components/ConfirmDialog.kt; Modify M/feature/count/CountWorkScreen.kt의 ConfirmDialog 명명 인자 호출만; Test Create I/feature/record/RecordDesignUiTest.kt; Modify I/AppUiTest.kt의 완료 문안 기대만 필요한 범위.

**Interfaces:** Consumes Task 2/4와 기존 RecordUiState/RecordAction. RecordScreen 기존 공개 서명 유지. DetailPhotoActions/ConfirmDialog는 Compose 컴포넌트 규칙 4절 modifier 포함 서명으로 갱신하며 onDelete/onConfirm을 모든 소비자에서 명명 인자로 전달한다. 사진 높이는 Task 4 helper를 사용하고 canEdit=false 유지.

- [x] **Step 1: 실패 UI 테스트 작성** — `confirmedRecordIsReadOnlyEvenWhenPhotoFails`는 confirmed fixture·preview=null에서 N개/확인 시각/사진 오류 유지, 추가/제외/확정 없음, 표시 목록의 편집 없음. `deleteRequiresDialogAndCancelDoesNotDispatch`는 기록 삭제 클릭 후 원본 유지 문구·취소/삭제 표시, 취소 후 RecordAction.Delete 0회. 다시 열어 삭제 확인 후 1회. `busyRecordDisablesDeleteAndSeparateInput`는 isDeleting=true에서 삭제/촬영/선택 비활성 검사.

읽기 전용 변형의 assertions:
```kotlin
rule.onNodeWithText("사용자 확인 완료").assertExists()
rule.onNodeWithTag("count-value").assertTextEquals("12개")
rule.onNodeWithTag("confirm-action").assertDoesNotExist()
rule.onNodeWithText("수동 표시 시작").assertDoesNotExist()
rule.onNodeWithText("추가", substring = false).assertDoesNotExist()
```
- [x] **Step 2: RED 확인** — `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.feature.record.RecordDesignUiTest'`.
- [x] **Step 3: 기록 UI 구현** — 사용자 확인 완료 라벨·개수/시각·읽기 전용 사진·보기 도구를 먼저 배치. DetailPhotoActions의 새 입력은 별도 작업 보존 설명을 유지하고 삭제를 별도 행·오류색으로 분리. 삭제 dialog 기존 영향/취소/성공 후 이동 보존. 사진 실패에 가짜 bitmap·0개로 대체하지 않는다.
- [x] **Step 4: GREEN·회귀** — Step 2 명령과 AppUiTest/NavigationUiTest. 앱 데이터 보존 결과를 기존 repository assertions로 검증하고 스냅샷만으로 대체하지 않는다.
- [x] **Step 5: 커밋** — 관련 기록/shared/test 파일만 stage·diff 검사; `git commit -m 'feat(design): 확인 완료 기록과 삭제 조작의 표현 정리'`.

### Task 6: 통합·접근성 수락과 문서 동기화

**Files:** Modify I/LargeFontUiTest.kt, AppUiTest.kt, NavigationUiTest.kt; Create app/src/debug/java/io/github/kyujin0911/pounter/preview/DesignPreviews.kt; Modify docs/verification.md, design_spec.md, design_system.md, compose_components.md, code_conventions.md; 필요하면 docs/design_system_board.html의 동일 문안/배치만 갱신.

**Interfaces:** Consumes Task 1~5의 최종 UI와 기존 end-to-end 테스트. Produces 앱 적용 범위/현재 실행 결과/미검증 항목이 구분된 기록. 제품 API·DB 변경 없음.

- [x] **Step 1: 누락된 실패 통합 테스트 추가** — `lightAndDarkWorkAtTwoHundredPercentKeepActionsWithinBounds`를 LargeFontUiTest에 추가: 두 테마·fontScale2.0·fixture300개에서 현재 개수/단위/확정/되돌리기·저장 실패 재시도에 scroll 도달, bounds 미잘림, interactive target>=48dp, CTA>=56dp. Activity.recreate 후 AppUiTest의 표시와 개수/완료 상태 유지 assertions를 보존한다. 고정/스크롤 CTA 각각 실제 클릭 후 확인 dialog를 검증한다.

단위 잘림 확인과 확인 dialog 동작의 assertions:
```kotlin
rule.onNodeWithTag("count-value").assertTextEquals("300개")
rule.onNodeWithTag("confirm-action").assertIsDisplayed().performClick()
rule.onNodeWithText("확정 저장").assertIsDisplayed()
```
확정 테스트에서는 task.analyzed=true, confirmedAt=null, operation=NONE, unsavedChanges=false를 사용한다. 실패 변형은 별도 mutable state 업데이트로 전환하며 dialog를 닫은 후 검사한다. 화면 밖 CTA는 content로 해당 tag를 스크롤한 후 검사한다.
- [x] **Step 2: 해당 추가 테스트 RED 확인** — 기존 코드/Task 5 결과에서 실패를 확인한다. 이미 기대를 만족하면 이를 기록하고 억지 실패를 만들지 않는다. 불필요한 제품 로직을 추가하지 않는다.
- [x] **Step 3: 실패한 UI만 최소 수정** — 단위/label wrap, 부모 constraints, contentPadding/insets, semantics를 소유 파일에서 조정. 테스트 기대를 줄이거나 도구를 제거하지 않는다.
- [x] **Step 4: 최종 자동 검증** — `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck`; `./gradlew connectedDebugAndroidTest`. 결과는 exit0/BUILD SUCCESSFUL/실제 테스트 수·기기/API로 기록한다. 전용 기기 선택 필요 시 ANDROID_SERIAL을 실제 조회값으로 지정한다. 빌드와 검출 성능을 구분한다.
- [ ] **Step 5: 실제 화면 관찰** — 360dp 세로·가로·라이트/다크·fontScale100/200%에서 홈/사진 확인/보정300개/저장 실패/완료/사진 실패 관찰. 밝은/어두운 합성 사진에서 marker 가림·이중테두리/자동·수동 구별 평가. TalkBack 개수/선택 모드/오류/대체 입력/읽기 전용 조작 확인. S22+ 밝은 환경·실사진·OS 프로세스 종료·강제 종료 복원은 수행 가능할 때 별도 확인; 못 하면 이유/최소 다음 절차로 남긴다. 재생성을 프로세스 종료 검증으로 보고하지 않는다.
- [x] **Step 6: 재사용 가능한 UI Preview 작성** — DesignPreviews.kt에 라이트/다크·fontScale2·widthDp360 변형과 미분석/0/300/확인 완료/저장 실패 fixture를 만든다. AndroidTest의 DesignFixtures를 import하지 않고 debug 파일의 private fixture 함수로 구성한다. NoticeCard 슬롯/CountSummary/홈/보정/완료를 VM·Room·실제 파일·네트워크 없이 Preview한다. 전체 화면 Preview는 상태와 no-op callback만 받는다. `./gradlew assembleDebug`로 debug source 포함 컴파일을 확인하고 Preview 관찰과 실행 검증을 구분한다.
- [x] **Step 7: 문서 동기화·커밋** — 실제 토큰/컴포넌트/문안/threshold 변경만 명세·보드·컴포넌트 계약에 반영. 상태를 '앱 적용/확인한 범위'와 '현장·미실행'으로 구분한다. 관련 테스트·문서·실제 수정 UI와 Preview만 stage하고 diff 검사 후 `git commit -m 'test(design): 재사용 계약과 접근성·저장 회귀 검증 기록'`.

## 패키지 적용 확인

Task2의 이동 검증에 더해 Task6 최종 diff에서 feature 간 Screen/Action/UiState/ViewModel 직접 import, core UI의 CountTask 의존, 공유 UI의 VM/Repository/NavController 조회, 옛 경로 복제/alias를 검사한다. 단위·컴포넌트 테스트는 대상 패키지와 일치, 기존 통합 테스트는 루트 유지, Preview는 debug/preview, fixture는 androidTest/feature/shared. 재사용처가 한 기능뿐인 신규 요소는 core/shared에 승격하지 않는다. code_conventions.md의 규칙과 실제 배치를 동시에 기록한다.

## 계획 자체 검토

명세 1~4절→Task1, 5절/10~11절→Task2~5, 6절/12절→Task3~5, 7~8절/13절→Task6에 연결했다. 사진 마커의 신규 밀도 설계는 실사진 없이 확정하지 않고 현행 구별·보정 경로를 보존한다. 런처 아이콘/Figma/자동 검출 구현·새 UI 기능은 제외 범위다. Review Focus 다섯 조건은 소유 Task의 검사와 기존 보존 테스트에 연결했다.

계획의 경로·기존 함수/상태는 저장된 구조 변경 SHA에서 확인했다. 새 함수 서명과 파일 책임은 계획에서 정의했다. 초기 작성 시 모든 실행 단계는 미실행이었다. 2026-10-13 구현 단계 체크박스를 실제 결과에 맞춰 갱신했다. Task6 Step5는 합성 캡처·일부 가로 화면까지 확인했으나 TalkBack·실기기·실사진 검증이 남아 전체 수락은 미완료로 유지한다. 결과와 실행 판단은 [검증 이력](../../verification.md#2026-10-13-디자인-시스템-구현)을 따른다.

2026-10-13 추가: 공식 Compose 컴포넌트 가이드에서 재사용 기준을 도출해 compose_components.md에 정리했다. Task2에 값 중심 StatusLabel·NoticeCard 슬롯·외부 Modifier/슬롯 수명 테스트를 추가하고, Task4에 부모 소유 모드 테스트와 기능 내부 파일 배치를 반영했다. Task5의 modifier/trailing-lambda 호출부 변경, Task6의 독립 Preview까지 연결했다. 이 추가 규칙은 기존 6개 작업에 통합했으며 별도의 범용 UI 프레임워크 작업을 만들지 않았다.

## 실행 인계

권장: **Native**. 6개 작업이 공통 테마·사진·상태 표현에 순차 의존하므로 한 구현자가 연결을 유지하고 마지막에 독립 리뷰를 받는 방식이 적합하다. Native는 executing-plans로 본 세션에서 구현하고 마지막에 새 리뷰어 한 명이 전체 변경을 검토한다. Subagent-driven은 작업마다 새 구현자/새 리뷰어로 진행해 검토는 더 촘촘하지만 호출 비용이 커진다.

사용자가 계획을 검토하고 실행 방식을 선택한 뒤 구현한다. 실행 시 필요한 리뷰 위임은 선택한 skill 지침에 따라 수행하며 현재 계획 단계에서는 에이전트를 만들지 않는다. 원격 push·PR·병합은 이 실행 선택으로 자동 허용되지 않는다.
