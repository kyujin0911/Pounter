# Pounter 코드 컨벤션

갱신일: 2026-10-12. 이번 문서는 변수 네이밍 규칙과 다음 구조·컨벤션 정리 작업의 적용 기준을 확정한다. 실제 소스 변수명 변경·파일/패키지 분리·포맷터·Koin 적용은 별도 구조/DI 작업에서 수행한다.

## 재사용 UI의 패키지 관리 — 2026-10-13 추가

상태: 규칙 작성 / 디자인 시스템 구현 시 적용 대기. 기존 변수 네이밍 내용은 보존한다. 책임·패키지 기준은 저장된 구조 변경 64f4700의 같은 문서에서 확인했다. 이 checkout은 이전 구조이며 다음 기준을 이미 코드에 적용했다고 보고하지 않는다. 구조 변경 결합 시 기존 책임/포맷/수명 규칙과 본 절을 함께 보존한다.

컴포넌트 API·상태 소유·Modifier·슬롯은 [Compose 컴포넌트 규칙](compose_components.md), 시각 기준은 [디자인 시스템](design_system.md), 적용 작업은 [구현 계획](superpowers/plans/2026-10-13-design-system-implementation.md)에 둔다. 패키지 위치·의존 방향은 이 절이 단일 기준이다.

### 배치: 사용 범위와 업무 의존성

루트는 `io.github.kyujin0911.pounter`. 단일 app 모듈을 유지한다. 패키지는 책임 구분이며 Gradle 의존성 경계가 아니다. 공식 [모듈화 패턴](https://developer.android.com/topic/modularization/patterns)의 응집도·낮은 결합 원칙을 패키지에 적용하는 자체 판단이며, UI 공유를 이유로 새 모듈을 만들라는 뜻이 아니다. 재사용 UI에서 ViewModel을 피하는 [Android 권고](https://developer.android.com/topic/architecture/recommendations)도 따른다.

| 위치 | 판단 기준 | 예시 | 허용하지 않는 책임 |
|---|---|---|---|
| feature/home | 홈 안에서 사용하는 의미/조작 | PhotoInputCard, RecordCard | count/record 구현 직접 참조 |
| feature/count | 보정/확정 작업 안에서 사용하는 조작 | CorrectionToolbar, ConfirmAction | record ViewModel/Action·목록 관찰 |
| feature/record | 완료 기록 전용 표현 | 기록 전용 UI가 실제 생길 때 | 계수 편집·분석 |
| feature/shared | 둘 이상의 기능에서 사용하는 파이프/작업 의미 | CountSummary, StatusLabel/TaskStatus, TaskHeader, PhotoPanel, PhotoGeometry, PhotoLayout, EditMode, ValidationNotice, RecoveryNotice, DetailPhotoActions | 특정 feature의 UiState/Action/ViewModel·전역 선택 작업 |
| core/ui/components | 파이프/작업 모델 없이 사용하는 시각·상호작용 계약 | NoticeCard/NoticeTone, MessageCard, ProgressCard, ConfirmDialog | CountTask·검출·저장·feature/data/navigation 의존 |
| core/ui/theme | 브랜드 색·글꼴·형태·간격 | PipeTheme, PipeColors, PipeTypography, PipeShapes, PipeDimensions | 화면·업무 컴포넌트 구현 |
| core/model | 데이터와 여러 화면이 공유하는 업무 모델/규칙 | CountTask, Marker, Region | Compose·디자인 전용 상태 enum |

판단 순서: 기존 컴포넌트 활용 가능 여부 → 해당 기능 안의 단일 목적 추출 → 실제 다른 기능의 소비자 확인 → 업무 의미가 남으면 feature/shared, 업무와 무관하면 core/ui/components. 인자가 String/Boolean이라는 사실만으로 core에 두지 않는다. 반대로 CountTask를 불필요한 DTO로 감춰 core로 승격하지 않는다. 저장 재시도 문구/맥락을 소유하는 RecoveryNotice는 shared, 일반 오류 메시지/닫기 표현인 MessageCard는 core다.

이번 core 승격 대상은 기존 feature/shared의 MessageCard.kt·ProgressCard.kt·ConfirmDialog.kt 세 파일과 새 NoticeCard.kt다. theme은 기존 위치를 유지한다. TaskStatus와 taskStatus(CountTask)는 StatusLabel.kt에 함께 두어 검수 의미를 core에 섞지 않는다. PhotoLayout은 계수·완료 두 기능이 사용하는 사진 작업 helper이며 core의 범용 계산 util로 승격하지 않는다.

### 의존 방향과 가시성

- feature/<기능> → feature/shared, core/ui/components, core/ui/theme, core/model. 화면의 Repository 계약 사용은 기존 구조 기준 유지.
- feature/shared → core/ui/components, core/ui/theme, core/model 및 Android/Compose. shared UI 안에 Repository/VM/NavController를 넣지 않는다. 기존 PhotoImportHandler의 데이터 책임은 변경하지 않으며 UI와 다른 역할임을 유지한다.
- core/ui/components → core/ui/theme 및 Kotlin 표준 라이브러리·Android/Compose/Material만. core/ui/theme → Kotlin 표준 라이브러리·Compose/Material만. 두 패키지 모두 feature/data/navigation/di/core/model을 참조하지 않는다.
- 서로 다른 기능의 Screen/Action/UiState/ViewModel 구현은 직접 참조하지 않는다. 이벤트는 callback으로 상위 화면에서 연결한다. data가 UI 재사용 패키지를 import하지 않는다.
- 한 파일 안에서만 쓰는 helper는 private. 모듈 내 여러 파일에서 쓰는 UI/타입은 internal을 기본으로 한다. internal은 단일 모듈 전체 가시성이며 같은 패키지만 접근 가능하게 하는 장치가 아니다. 타입과 함수의 가시성을 함께 맞춘다.
- public은 외부 모듈에서 사용할 실제 계약이 생긴 경우 검토한다. 이번에 외부 라이브러리 API를 만들지 않는다. Preview/테스트 접근을 이유로 제품 API를 public으로 늘리지 않는다.

### 파일·승격·축소 규칙

파일명은 주요 컴포넌트명, package 선언은 실제 경로와 맞춘다. 작은 enum/default/어댑터는 같은 책임의 파일에 둔다. `core/ui/components/button`, `feature/count/components` 같은 하위 패키지를 파일 하나마다 만들지 않는다. 실제 독립 책임·탐색 비용이 생길 때만 분리하고 `util/common/base/widget`에 미분류 코드를 모으지 않는다.

승격에는 실제 소비자, 공유하는 의미/API, 제거할 업무 의존성, 상태 소유, 검증 항목을 적는다. 기존 파일을 이동하고 import·호출부·테스트·Preview·명세를 함께 갱신한다. 같은 구현을 옛 위치에 복제하거나 typealias/forwarding wrapper를 상시 남기지 않는다. 패키지만 바꾸는 커밋은 문안·표시·좌표·저장 동작을 함께 바꾸지 않는다.

재사용처가 사라지거나 두 소비자의 동작이 분리되면 해당 기능으로 되돌리는 것도 검토한다. 둘 중 하나에만 필요한 Boolean을 누적해 공통 컴포넌트를 만능화하지 않는다. 같은 클래스 재사용은 인스턴스·ViewModel·현재 작업 상태의 전역 공유를 뜻하지 않는다.

### 테스트·Preview와 완료 조건

새 단위/컴포넌트 테스트는 대상 소스의 패키지 구조를 test/androidTest 아래에 맞춘다. 여러 화면을 통과하는 기존 AppUiTest/NavigationUiTest/LargeFontUiTest는 현재 통합 테스트 위치를 유지한다. UI fixture는 androidTest/feature/shared, 앱 전체 시안 Preview는 debug/preview에 둔다. 실제 사진·네트워크·DB 없이 Preview하고 release에 fixture를 넣지 않는다.

적용 완료 조건은 다음과 같다.

1. core UI에 업무 모델·feature/data/navigation 의존이 없고 theme→components 역의존이 없다.
2. 일반 UI 이동 후 기존 shared import/중복 구현이 남지 않는다. 검수 UI는 shared, 한 기능 전용 UI는 그 기능에 있다.
3. 경로/package·심벌 가시성·호출부·테스트 runner FQCN·Preview import가 일치한다.
4. 구조 이동 전후 기존 빌드/단위/기기 회귀와 UI 계약을 검증한다. 패키지 정리만으로 UI 개선·성능 향상을 주장하지 않는다.
5. 문서/계획에는 실제 적용과 미실행 검증을 구분한다. 새 아키텍처 검사 라이브러리·모듈은 넣지 않고 import/diff 리뷰와 기존 도구로 확인한다.

## 변수 네이밍

기본은 영어 lowerCamelCase이다. 이름에서 값의 의미·역할·단위·현재 상태를 이해할 수 있게 한다. 이름을 길게 만드는 것이 목적은 아니며, 사용하는 범위에서 구분에 필요한 정보만 추가한다.

| 대상 | 기준 | 작성 예시 |
|---|---|---|
| 화면 상태 | private mutable backing property는 _uiState, 공개 읽기 전용 StateFlow는 uiState | _uiState / uiState |
| 기타 backing property | 공개 속성과 같은 개념일 때만 private 이름 앞에 _ 사용 | _records / records |
| Boolean | 사실은 is/has, 가능 여부는 can/should 등 의미에 맞는 긍정 조건. 이 접두사는 프로젝트 권고이며 Kotlin의 강제 문법 아님 | isAnalyzing, hasUnsavedChanges, canConfirm |
| 비동기 작업 | 수행 목적 + Job. 단순히 analysis처럼 데이터와 혼동하지 않게 함 | analysisJob |
| 결과 유효성 토큰 | 어떤 결과를 차단하기 위한 버전인지 표시 | analysisGeneration |
| 저장 단계 | 편집·대기·저장 성공을 구분. 저장 전 값을 saved로 부르지 않음 | editedTask, taskPendingSave, savedTask |
| 분석 단계 | 후보·최종 검출·유효 표시를 구분 | candidateMarkers, detectedMarkers, activeMarkers |
| ID | 여러 대상이 같은 범위에 있을 때 대상 명시 | taskId, markerId, importedTaskId |
| 컬렉션 | 원소 의미의 복수형. 타입을 반복하는 List/Array 접미사는 보통 불필요 | markers, records, markerIds |
| 수량·인덱스 | 개수와 위치 인덱스를 구분 | markerCount, markerIndex |
| 시간·크기 | 의미와 단위 명시. 시각과 소요 시간도 구분 | confirmedAtMillis, analysisDurationMillis, maxPhotoSizeBytes |
| 좌표 | 같은 범위에 서로 다른 좌표계가 있으면 화면/이미지/정규화와 단위를 구분 | tapXPx, normalizedCenterX, viewportWidthPx |
| 의존성 | 실제 책임 이름. data/service/manager처럼 광범위한 이름을 피함 | countRepository, photoStore, pipeDetector |
| callback | on + 사용자/상태 동작. 실제 수행 함수와 구분 | onConfirm, onMarkerAdded |
| 상수 | const 또는 깊게 불변인 최상위/object 값은 UPPER_SNAKE_CASE. 객체의 일반 val까지 상수처럼 명명하지 않음 | MAX_MARKER_COUNT |

### 모호한 이름과 허용 범위

클래스 필드나 긴 함수의 mutable/data/result/value/temp/flag/generation/manager는 실제 역할로 이름을 정한다. 타입 자체가 역할을 충분히 설명하는 짧은 범위의 지역 변수 task/marker/result는 사용할 수 있다. 코드를 읽는 데 도움이 되지 않는 형식적 접미사를 붙이지 않는다.

- it은 짧은 단일 lambda에서 대상이 명확할 때 사용한다. 중첩 lambda나 서로 다른 대상이 섞이면 task/marker/record 등 명시적 파라미터를 쓴다.
- i/x/y는 짧은 인덱스·수학·기하학 계산에서 의미가 명확할 때 허용한다. 여러 좌표계가 섞이면 이름으로 구분한다.
- mState/sData 같은 접두사를 사용하지 않는다. 모든 private 변수에 _를 붙이지 않는다.
- Boolean의 notSaved처럼 부정형 이름과 이중 부정을 피한다. 실제 상태와 가능 조건을 혼동하지 않는다.
- 약어는 ui/id/uri처럼 해당 문맥에서 통용되는 경우만 사용한다. 불필요하게 모든 약어를 풀지 않는다. 예: uiState, taskId, photoUri.
- 이름과 값의 의미가 일치해야 한다. count는 개수, index는 위치, radius는 반경이며 단위/좌표 기준을 확인한다.

### 현재 코드의 적용 후보 — 아직 미변경

아래는 구조 정리 작업에서 의미와 호출부를 확인한 뒤 적용할 예시다. 실행 결과가 아니다.

| 현재 CountViewModel 이름 | 후보 | 이유 |
|---|---|---|
| mutable | _uiState | 화면 상태의 mutable backing property |
| analysis | analysisJob | 분석 coroutine의 Job |
| generation | analysisGeneration | 취소/이전 분석의 늦은 결과 차단 |
| pendingSave | taskPendingSave | 저장 대기 중인 CountTask |
| CountUiState.busy | isBusy | 작업 진행 상태를 Boolean으로 표현 |

공개 모델의 manual/analyzed/confirmed 등의 이름은 자동 치환하지 않는다. 직렬화 속성명·SavedStateHandle key·Room 컬럼·TaskCodec JSON key·Navigation 인자와 연결되어 있는지 먼저 확인한다. 로컬 변수명 개선을 영속 계약 변경으로 확대하지 않는다. 기존 기록과 복원이 유지되도록 필요하면 명시적 매핑을 사용하고, 실제 저장 형식 변경은 별도 영향 판단과 마이그레이션이 필요하다.

## 구조·컨벤션 정리 작업에 포함할 항목

첫 번째 구조 정리 Issue/프롬프트의 범위와 완료 조건에 아래를 포함한다. 이번 문서 작성은 해당 구현 Issue 생성이나 코드 변경을 대신하지 않는다.

- 관련 지침과 이 문서를 읽고 파일·책임·패키지 분리를 진행한다.
- 새로 분리한 화면별 UiState/Action/ViewModel과 기존 관련 소스의 필드·파라미터·지역 변수·lambda 이름을 검토한다.
- 위 적용 후보를 실제 의미에 맞게 개선하고 public readonly uiState를 유지한다.
- 시간/용량/좌표가 경계를 넘는 곳에서 단위와 좌표계를 확인한다.
- 저장 전·저장 완료·분석 후보·최종 검출의 이름을 구분한다.
- 이름 변경과 동작 변경을 분리하고, 새 추상화나 기능을 네이밍 정리 때문에 추가하지 않는다.
- 영속 key·DB·앱 ID·사진·기록 보존, 취소·오래된 결과·저장 실패 복구를 유지한다.
- 관련 호출부·테스트·문서를 함께 정리한다. 테스트의 실제 기대 동작은 약화하지 않는다.

완료 조건:

1. _uiState/uiState 및 주요 Job·토큰·저장 대기 변수의 역할이 이름에서 식별된다.
2. 분리된 화면과 공통 컴포넌트에서 모호한 변수·중첩 it·단위 혼동을 리뷰했다.
3. 포맷 검사와 관련 빌드·테스트·Lint 결과를 보고했다. 미실행은 이유와 구분했다.
4. 기존 데이터 형식과 사용자 흐름을 보존했다.

두 번째 Koin 작업에서도 이 규칙을 따른다. DI 지역 변수·등록 함수·의존성 이름에 실제 생성 책임을 표시하고, 객체 수명을 singleton/factory/viewModel 등록으로 검증한다. 이름만으로 수명 정확성을 입증했다고 보고하지 않는다.

## 검사와 문서의 경계

camelCase·상수 표기·일부 backing property는 기존/선택한 포맷·정적 분석 도구가 지원하는 범위에서 확인한다. 역할·단위·좌표계·저장 단계의 의미는 diff와 관련 테스트로 사람이 검토한다. 이번 문서만으로 자동 검사가 설치되거나 통과한 것은 아니다. 네이밍 전용 플러그인·hook·추가 검사 프레임워크를 지금 도입하지 않는다.

공식 표기 기준: [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). 역할·Boolean·단위·완료 조건은 Pounter의 자체 규칙이다. 후속 구조 정리에서 파일·패키지·포맷 규칙을 같은 문서에 보완하고 중복 문서를 만들지 않는다.
