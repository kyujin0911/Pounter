# Pounter 코드 컨벤션

갱신일: 2026-10-12. 파일·책임·패키지·명명·포맷팅 규칙의 단일 기준이다. 제품 범위는 [PRD](../PRD.md), 작업 승인과 검증은 [AGENTS](../AGENTS.md), Git 단위는 [Git 운영 규칙](git_workflow.md)을 따른다. 이번 구조 작업은 [Issue #1](https://github.com/kyujin0911/Pounter/issues/1)에 연결한다.

## 책임과 패키지

단일 app 모듈을 유지한다. 기능별 UI와 데이터 책임을 나누되, 패키지 구분은 Gradle이 강제하는 모듈 경계가 아니다. 다음 배치는 실제 코드에 적용했다.

| 패키지 | 책임·배치 예시 | 제외할 책임 |
|---|---|---|
| feature/home | HomeScreen, HomeAction, HomeUiState, HomeViewModel: 기록 목록 구독·조회 오류 | 편집·분석·확정 저장 |
| feature/count | CountWorkScreen, CountAction, CountUiState, CountViewModel: 경로 작업 복원·영역·보정·분석 취소·저장 재시도·확정·진행 작업 삭제 | 전체 기록 목록 구독·확정 기록 편집 |
| feature/record | RecordScreen, RecordAction, RecordUiState, RecordViewModel: 읽기 전용 조회·사진·삭제 | 편집·검출·확정 Action |
| feature/shared | PhotoPanel, PhotoGeometry, EditMode, 실제 공유 화면 부분과 PhotoImportHandler | 특정 화면 UiState/Action 참조·전역 선택 작업 |
| data/repository | CountRepository 계약, LocalCountRepository 구현: DB/파일/검출 조합·revision·부분 완료 복구 | 화면 상태·이동 |
| data/local/database | CountDatabase, TaskDao, TaskEntity, TaskCodec: 영속 형식 | UI 모델·화면 처리 |
| data/local/photo | PhotoStore: 가져오기·정규화·미리 보기·파일 삭제 | 화면 이동·개수 판단 |
| data/detector | PipeDetector, Detection, ValidationRequiredDetector | UI 상태·수동 앱을 자동 MVP로 표시 |
| core/model | CountTask, Region, Marker: 화면과 데이터가 공유하는 불변 모델·규칙 | 특정 화면 Action/UiState·Room Entity |
| core/ui/theme | PipeTheme: 앱 공통 테마 | feature 구현 |
| navigation | 타입 안전 경로·NavHost·entry 소유권·Activity Result launcher 연결 | 영속 데이터 저장·검출 |
| di (향후) | 별도 승인된 Koin 작업에서 객체 생성·등록·수명 | 이번에는 파일·빈 패키지·의존성 추가 없음 |

실제 책임이 없는 디렉터리는 만들지 않는다. 한 파일 때문에 반복적으로 하위 패키지를 만들거나 util/common/base에 배치가 어려운 코드를 모으지 않는다. PhotoGeometry는 사진 컴포넌트 가까이 둔다. Repository 인터페이스는 기존 테스트/교체 경계로 유지하며 별도 domain/UseCase/DTO를 강제하지 않는다.

### 의존 방향

- feature → Repository 계약과 core 모델. 데이터 처리는 Repository를 통해 요청한다.
- 서로 다른 feature는 상대 Screen/ViewModel 구현을 직접 참조하지 않는다. 공유 구현은 실제 재사용될 때 feature/shared에 둔다.
- navigation → 각 feature 화면·ViewModel을 연결한다. NavController는 UI 계층에만 둔다.
- data/core → feature/navigation/di 의존을 금지한다. data에서 필요한 shared 모델은 core/model에 둔다.
- 의미 없는 BaseViewModel/BaseRepository·공통 Store·인터페이스·UseCase·DI 라이브러리·멀티 모듈은 추가하지 않는다.

## 파일과 공유 기준

주요 공개 타입은 이름에 맞는 파일을 쓴다. HomeScreen.kt / CountWorkScreen.kt / RecordScreen.kt, 각 ViewModel.kt / Action.kt / UiState.kt를 분리한다. 공개 타입 이름과 실제 패키지 경로가 일치해야 한다. 작은 내부 enum, private 함수, 같은 책임의 화면 전용 다이얼로그는 같은 파일에 둘 수 있다. 줄 수만으로 무조건 분리하지 않는다.

화면은 실제 내용을 소유한다. 분리된 파일이 동일한 거대한 renderer를 호출하는 wrapper에 그치지 않게 한다. 기존 PipeApp.kt는 제거했고 화면 연결은 navigation/PipeNavigation.kt가 담당한다. 공통 MessageCard/ProgressCard/CountSummary 등은 실제 여러 화면에서 사용하는 부분만 추출했다.

공유 사진 컴포넌트는 불변 CountTask 사진/표시 데이터, bitmap, 모드·허용 조건, 추가/삭제 callback을 받는다. 화면 UiState 전체나 Action dispatcher를 받지 않고 ViewModel/Repository/NavController를 조회하지 않는다. 기록 화면은 canEdit=false로 제공하며 RecordAction에도 편집 동작이 없다. 공유 모델을 불필요한 여러 DTO로 복제하지 않는다.

PhotoImportHandler는 사진 입력 후 Repository 가져오기·오류·이동 대기 ID를 재사용하는 작은 상태 처리 객체다. 각 entry의 ViewModel이 자신의 SavedStateHandle/viewModelScope로 별도 생성한다. 같은 클래스 재사용과 같은 인스턴스 공유는 다르다. 전역 handler/ViewModel로 현재 작업을 공유하지 않는다. 시스템 launcher·Context는 navigation의 Compose 수명에 둔다.

### 상태·수명·복원

Home/Count/Record는 독립 책임 때문에 ViewModel을 분리했다. 단순히 목적지가 세 개라서 로직을 복제하지 않는다. 목록은 Home만 구독하고 Count는 저장 대기 값과 분석 Job을 소유하며 Record는 읽기 전용 조회·삭제만 수행한다. 공개 상태는 읽기 전용 StateFlow, 화면은 collectAsStateWithLifecycle로 수집한다.

경로에는 taskId만 전달한다. ViewModel은 해당 Navigation entry에 속하며 pop 시 해제된다. 사진·영역·표시·확정은 Repository 영속 데이터, 저장 실패 대기 값/가져오기 이동 결과는 SavedStateHandle, 확대·패널·선택 모드는 UI 상태로 구분한다. 상세 이동·실패 정책은 [화면 명세](design_spec.md#navigation-목적지와-이동-규칙)를 따른다.

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

### 이번에 적용한 주요 이름

| 이전 | 적용 | 판단 |
|---|---|---|
| CountViewModel.mutable | _uiState | 공개 uiState의 mutable backing property |
| analysis | analysisJob | 실행 중 분석 coroutine |
| generation / token | analysisGeneration / resultGeneration | 현재 유효 세대와 해당 결과의 세대 |
| pendingSave / next / stored | taskPendingSave / editedTask / savedTask | 저장 대기·편집 결과·저장 성공을 구분 |
| CountUiState.busy | isBusy | 진행 상태이며 편집 허용 조건 canEdit와 구분 |
| CountAction.Add.x/y, Remove.ids | normalizedX/normalizedY, markerIds | 좌표계와 삭제 대상 |
| PhotoPanel.cursor/pan/candidates | normalizedCursor/panPx/overlappingMarkerIds | 정규화 좌표·화면 px·겹친 삭제 후보 |
| PhotoGeometry.viewportWidth/drawnWidth/panX | viewportWidthPx/drawnWidthPx/panXPx | 화면 픽셀 단위 |
| PhotoStore.total/n/sample/maxSide | totalBytes/bytesRead/sampleSize/maxSidePx | 크기·읽기 양·샘플 배율·픽셀 |
| 테스트 필드 fail/saves/delayResult | hasSaveFailure/saveCount/analysisResultGate | 대역의 실패 조건·호출 수·완료 대기 |

Repository의 revision 증가 후 DB 쓰기 전 값은 taskToPersist로, ViewModel이 Repository 저장 성공 후 받은 값은 savedTask로 구분한다.

기존 공개 모델의 manual/analyzed/confirmed, width/height, analysisMillis, createdAt 등의 이름과 저장 속성은 유지한다. TaskCodec의 photo/r/millis/confirmed 등 JSON key, Room 컬럼, SavedStateHandle의 taskId/pendingSave/importedTaskId, Navigation 인자 taskId는 이름 정리로 변경하지 않는다. Route 패키지를 옮겨도 @SerialName으로 기존 직렬화 이름을 유지하고 entry ViewModel 저장 key도 명시적으로 유지한다.

CountUiState 등 일시 화면 타입의 이름 변경과 영속 계약 변경을 구분한다. 영속 형식 변경이 필요하면 먼저 기존 형식을 유지하는 명시적 매핑을 검토하고 실제 마이그레이션은 별도 영향 판단을 한다. Room 클래스 패키지 이동은 export schema 경로만 이동했으며 v1 스키마 내용은 동일하다. 데이터 초기화·파괴적 마이그레이션은 사용하지 않는다.

## Kotlin·Compose 포맷

Kotlin 공식 스타일을 기본으로 4칸 들여쓰기·LF·UTF-8·마지막 개행과 정렬된 명시적 import를 사용한다. 긴 선언·생성자·Composable 호출은 멀티라인으로 작성하고 여러 문장/분기를 세미콜론 한 줄로 압축하지 않는다. 문자열 내부 표현식의 공백도 읽기 쉽게 작성한다. private helper/preview는 같은 화면 책임이면 함께 둘 수 있다.

포맷 실행 경로는 Spotless 7.0.4 + ktfmt 0.54 KotlinlangStyle 하나다. Gradle 8.13/Java 17 기존 환경에서 검증했고 앱 의존성 전체를 올리지 않았다. .editorconfig는 IDE의 4칸·100열 기준을 맞추며 ktfmt 자체의 Kotlin 스타일/줄바꿈 결과를 최종 검사 기준으로 쓴다. ktfmt가 .editorconfig의 모든 IDE 옵션을 읽는다고 가정하지 않는다. 문자열 등 자동 분할하지 않는 경우는 100열을 기계적으로 강제하지 않는다.

```sh
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug testDebugUnitTest lintDebug
```

대상은 app/src의 직접 관리하는 .kt와 루트/app Gradle .kts이다. build 생성 코드, Room JSON, 외부 Skill/서브모듈은 대상 밖이다. 포맷 변경은 먼저 독립 커밋으로 남기고 책임/패키지/명명 변경·해당 테스트는 후속 목적 커밋으로 묶는다. 자동 포맷을 의미·책임 리뷰 완료로 보고하지 않는다.

## 자동 검사와 리뷰

| 확인 방법 | 확인 범위 | 확인하지 못하는 것 |
|---|---|---|
| spotlessCheck | 선택한 포맷·import 정렬·공백/줄바꿈 | 이름의 의미·책임·의존 방향·데이터 호환성 |
| 빌드·Kotlin 컴파일·Android Lint | 참조·타입·Android 정적 경고 | 실제 실기기/현장 검출 성능 |
| JVM 회귀 테스트 | 저장 실패 복원·중복 완료·취소/늦은 결과·목록/기록 책임·경로 직렬화·입력 이동 대기 복원 | OS가 실제 종료한 프로세스 복원 |
| 에뮬레이터 회귀 테스트 | Navigation·보정·Activity 재생성·Room/파일·v1 기록 호환성·큰 글꼴 UI | S22+ 실기기·TalkBack 사용성·현장 정확도 |
| diff 직접 리뷰 | 파일/타입/패키지 일치·금지 의존·단위·중첩 lambda·영속 key·검증 범위 | 런타임 검증 대체 |

현재 패키지 의존과 변수 의미는 별도 자동 강제 플러그인이 없다. 리뷰에서 확인하며 네이밍 전용 플러그인·hook·추가 분석 프레임워크를 넣지 않는다. 기존 .coderabbit.yaml의 UI 리뷰 경로도 feature/navigation으로 맞추고 이 문서를 참조하도록 했다. 이는 로컬 AI 리뷰 지침이며 서버 적용·강제 검사 통과를 의미하지 않는다. 실제 실행 결과와 미검증은 [검증 이력](verification.md)에서 확인한다. 과거 테스트를 이번 결과로 재사용하지 않는다.

## 다음 Koin 작업 — 문서 규칙만 정의

별도 승인 후 di에 Application 소유 DB/PhotoStore/Repository/Detector 생성과 entry별 화면 ViewModel 등록을 옮긴다. PhotoImportHandler는 entry별 객체로 유지한다. singleton/factory/viewModel 수명과 SavedStateHandle 경로/호환 key를 확인한다. 이름만으로 수명을 입증하지 않는다. 이번에는 Koin 의존성·DI 모듈·빈 파일을 만들지 않았다.

근거: [Kotlin 공식 컨벤션](https://kotlinlang.org/docs/coding-conventions.html), [Spotless Gradle](https://github.com/diffplug/spotless/tree/main/plugin-gradle), [ktfmt](https://github.com/Kotlin/ktfmt). 역할·패키지·Boolean·단위·완료 조건은 Pounter 자체 정책이다.
