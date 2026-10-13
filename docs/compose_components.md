# Pounter Compose 컴포넌트 규칙과 설계

작성일: 2026-10-13. 상태: 컴포넌트·패키지·독립 Preview 코드 적용 / 실행 결과는 검증 이력 참조.

[디자인 시스템](design_system.md)의 UI 재사용 기준을 구체화한다. [구현 계획](superpowers/plans/2026-10-13-design-system-implementation.md)의 컴포넌트 API는 본 문서와 함께 따른다. 패키지 위치·의존 방향·승격 절차는 [코드 컨벤션](code_conventions.md#재사용-ui의-패키지-관리--2026-10-13-추가)을 따른다. 제품 범위·보존·이동은 PRD와 화면 명세가 우선한다. 아래 Kotlin은 적용한 API 계약의 요약이다. 서명 예제의 가시성은 생략했으며 신규 컴포넌트는 internal, debug Preview/fixture는 private로 둔다. 기존 화면/PhotoPanel의 공개 API는 유지한다.

## 1. 공식 근거와 적용 범위

2026-10-13 다음 원문을 조회했다. Android 공식 API 안내가 연결하는 AndroidX 가이드도 포함했다. 프레임워크/라이브러리 개발자와 앱 개발자의 엄격도는 다르므로 모든 MUST를 앱 전체에 기계적으로 강제하지 않는다.

| 근거 | 참고한 내용 | Pounter 규칙 |
|---|---|---|
| [Compose API 안내](https://developer.android.com/develop/ui/compose/api-guidelines) | 앱·라이브러리·프레임워크 대상과 지침 구분 | 자체 규칙과 공식 권고를 구분 |
| [AndroidX 컴포넌트 API 지침](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md) | 단일 목적, 생성 필요성, 파라미터/Modifier, 슬롯 수명 | 필수 값→Modifier→선택 값→주 content 슬롯. Modifier는 루트에 한 번. 슬롯은 명확한 변형이 있을 때 |
| [상태 호이스팅](https://developer.android.com/develop/ui/compose/state-hoisting) | 상태를 사용하는 가장 가까운 공통 소유자, 불변 값과 이벤트 | 업무 상태는 화면 계층에서 받으며 자식이 직접 변경하지 않음. 지역 UI 상태까지 전부 ViewModel로 옮기지 않음 |
| [Modifier](https://developer.android.com/develop/ui/compose/modifiers) | 루트 전달, 체인 순서, 레이아웃 scope | 외부 크기·간격·semantics는 호출자, 내부 모양/간격은 컴포넌트 |
| [레이아웃 기본/슬롯](https://developer.android.com/develop/ui/compose/layouts/basics) | 상위 레이아웃과 content 구성, Material 슬롯 | 필요한 범위만 ColumnScope/RowScope로 제공 |
| [Preview](https://developer.android.com/develop/ui/compose/tooling/previews) | preview 제약과 상태별 시각 확인 | 실데이터/VM 없는 합성 상태로 Preview. 실행·TalkBack·복원 검증과 구분 |

## 2. 프로젝트 규칙

1. **추출 이유를 먼저 기록한다.** 두 개 이상 실제 사용처에서 같은 의미·상태·배치가 반복되거나, 한 화면에서도 독립된 복잡한 조작을 소유할 때 추출한다. 업무와 무관한 디자인 UI의 core 승격은 코드 컨벤션 기준을 따른다. '미래에 쓸 수도 있음'만으로 공통 패키지에 넣지 않는다. 두 사용처 조건은 Pounter 판단이며 공식 숫자 기준이 아니다.
2. **Material에서 시작한다.** 단순 Button/Text/Surface 래퍼를 모든 화면에 강제하지 않는다. 색·글꼴·형태는 테마로 공유한다. 외형만 비슷하고 행동이 다르면 별도 업무 컴포넌트를 유지한다.
3. **시각 요소는 의미를 드러내는 PascalCase 이름을 사용한다.** CountSummary·CorrectionToolbar처럼 표시/역할 이름을 사용하고 RenderUi·HandleState 같은 모호한 이름을 피한다.
4. **Modifier 기본값은 Modifier, 첫 선택 파라미터다.** UI를 내보내는 재사용 컴포넌트에 하나만 두고 가장 바깥 레이아웃 체인의 앞에서 한 번 적용한다. childModifier·textPadding 같은 외부 배치 옵션을 늘리지 않는다. 슬롯이 필요한 하위 요소는 해당 슬롯 호출자가 제어한다.
5. **필요한 값과 이벤트를 받는다.** MutableState·ViewModel·Repository·NavController·Activity를 받지 않는다. 새 기본 UI는 값 중심, 기존 CountTask를 받는 앱 전용 컴포넌트는 내부 읽기 전용 어댑터를 허용한다. StateFlow 수집과 Action 변환은 화면에 둔다.
6. **조작 가능 여부는 호출자가 결정한다.** 저장 실패/저장 중 보호 조건을 UI 내부에서 임의 해석하거나 enabled=true로 복제하지 않는다. 이름은 canEdit/canConfirm 등 실제 가능 조건을 드러낸다.
7. **지역 상태는 필요한 곳에 둔다.** PhotoPanel의 확대·이동·겹친 표시 선택처럼 내부에서만 쓰는 상태는 유지한다. 같은 task.id의 보정 때 초기화하지 않는다. 다른 UI와 공유할 실제 필요가 생긴 경우에만 가장 가까운 소유자로 올린다.
8. **슬롯은 내용 구성이 변하는 곳에만 쓴다.** 주 content는 마지막, 선택적 action은 nullable 슬롯. 단순 label/count를 불필요하게 람다로 감싸지 않는다. Boolean이 여러 개 조합되는 만능 variant, 스타일 객체, 독자 DSL을 만들지 않는다.
9. **슬롯의 위치·수명을 안정적으로 유지한다.** 색/상태가 바뀔 때 동일 content를 if 양쪽에서 새로 구성하지 않는다. content는 한 자리에서 한 번 호출한다. action이 없어질 때 action 수명은 끝날 수 있지만 본문 상태는 초기화하지 않는다.
10. **접근성과 수명은 계약의 일부다.** 터치 영역·상태·선택·오류 semantics를 정의한다. 전체 카드 클릭과 내부 중복 클릭을 겹치지 않게 한다. 내부 testTag로 호출자의 루트 testTag를 덮지 않는다. Slot의 텍스트/버튼 의미도 임의로 clearAndSetSemantics하지 않는다.
11. **의존성은 드러내고 값은 복제하지 않는다.** 테마는 암묵 의존성을 허용한다. 파일/DB/Navigation·영속 저장은 UI 컴포넌트 책임이 아니다. 화면 액션은 콜백에서 변환한다. CountTask.count를 별도 캐시로 재계산하지 않는다.
12. **재사용 계약을 동작으로 검증한다.** Modifier 전달·크기/클릭 1회·상태 제어·슬롯 수명·큰 글꼴·읽기 전용을 검사한다. Preview를 작성했다고 실행 검증을 통과 처리하지 않는다. @Stable/@Immutable은 사실인 계약이 확인될 때만 사용한다.

## 3. 현재 UI 재사용 조사와 배치

조사는 이전 ui/PipeApp.kt/PhotoPanel.kt와 저장된 구조 변경 SHA 64f4700을 기준으로 시작했고, 현재 아래 위치에 적용했다. 초기 조사와 후속 실행 검증은 [검증 이력](verification.md#2026-10-13-디자인-시스템-구현)으로 구분한다.

| 대상 | 실제/계획 사용처 | 설계 결정 | 위치 |
|---|---|---|---|
| TaskHeader | 계수·완료 화면 | 기존 확장, modifier 제공, 제목/복귀 책임 유지 | feature/shared/TaskHeader.kt |
| CountSummary | 계수·완료, 향후 같은 상세 표시 | 기존 확장. 도메인 CountTask를 읽는 업무 컴포넌트 유지 | feature/shared/CountSummary.kt |
| StatusLabel | 계수·완료·홈 RecordCard | 새 값 중심 상태 라벨 | feature/shared/StatusLabel.kt |
| NoticeCard | ProgressCard·ValidationNotice·MessageCard·RecoveryNotice | 공통 표면/본문/조작 슬롯. 업무별 문구·권한은 각각 유지 | 새 core/ui/components/NoticeCard.kt |
| PhotoPanel | 계수·완료 | 기존 재사용, modifier/높이 추가. 내부 사진과 보기 도구 분리는 보류 | feature/shared/PhotoPanel.kt |
| ConfirmDialog / DetailPhotoActions | 계수·완료 | 기존 재사용, modifier 전달/콜백·파괴 동작 조건 보존 | ConfirmDialog: core/ui/components; DetailPhotoActions: feature/shared |
| RecordCard / PhotoInputCard | 홈 기록 항목/새 입력 | 기능 내부 컴포넌트. 전역 공통 승격하지 않음 | feature/home |
| CorrectionToolbar / ConfirmAction | 계수 화면 | 복잡한 조작/고정·스크롤 양쪽 배치 대응. 기능 내부 위치 | feature/count |

같은 배경이라는 이유로 기록 카드와 입력 카드를 하나의 다목적 Card로 합치지 않는다. NoticeCard의 공통점은 제목/본문/조작을 가진 안내 구조이며, 정보/오류라는 두 의미만 받는다. 로딩 indicator와 취소, 오류 재시도는 슬롯 호출자의 명시적 UI다.

## 4. 적용한 API 계약

### StatusLabel: 업무별 도메인과 UI 표현 분리

```kotlin
enum class TaskStatus { UNANALYZED, REVIEW_REQUIRED, CONFIRMED }
internal fun taskStatus(task: CountTask): TaskStatus
@Composable
fun StatusLabel(status: TaskStatus, modifier: Modifier = Modifier)
```

같은 StatusLabel.kt에 선언한다. taskStatus는 confirmed 우선, 다음 analyzed, 나머지 미분석으로 읽기 전용 매핑한다. 상태를 저장하거나 새 SSOT로 만들지 않는다. 문구는 '분석 전'/'확인 전'/'사용자 확인 완료'. 홈의 '진행 중'은 RecordCard의 문맥 문구로 둔다. confirmed는 secondaryContainer, 다른 상태는 중립/primaryContainer와 문구로 표현한다. 버튼 역할·클릭 없음.

CountSummary(task, modifier)는 이 매핑을 사용하며 날짜·수동/자동 출처는 별도 본문이다. RecordCard도 taskStatus를 사용한다. 큰 도메인 UI 모델·변환 계층을 새로 만들지 않는다.

### NoticeCard: 한 컨테이너, 필요한 슬롯만

NoticeCard와 일반 MessageCard/ProgressCard/ConfirmDialog는 core/ui/components에 두고 업무 문구를 고정한 ValidationNotice/RecoveryNotice는 feature/shared에 둔다. 기존 일반 컴포넌트는 복제하지 않고 이동한다.

```kotlin
enum class NoticeTone { INFORMATION, ERROR }
@Composable
fun NoticeCard(
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

동일 파일에 enum을 둔다. tone은 두 의미만 지정하는 앱 자체 계약이며 범용 스타일 시스템이 아니다. INFORMATION은 surfaceContainer/onSurface, ERROR는 errorContainer/onErrorContainer. title 없으면 제목 영역과 그 간격 없음, actions 없으면 버튼 영역과 그 간격 없음. 내부 content를 같은 Column 위치에서 한 번 호출한다. 외부 modifier는 Surface 루트에 한 번, 내부 카드 여백16dp·그룹 간격8dp. content에 의미 있는 텍스트/진행 표시, actions에 기본 Material 버튼을 넣는다. 배치가 부족하면 action RowScope 내 호출자가 FlowRow로 줄바꿈한다.

기존 업무 함수의 API:
```kotlin
@Composable
fun ProgressCard(label: String, modifier: Modifier = Modifier, onCancel: (() -> Unit)? = null)
@Composable
fun MessageCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier)
@Composable
fun ValidationNotice(modifier: Modifier = Modifier)
@Composable
fun RecoveryNotice(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier)
@Composable
fun TaskHeader(task: CountTask?, canGoBack: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier)
```

위 안내 함수는 NoticeCard를 활용하되 RecoveryNotice의 '저장 재시도'와 MessageCard의 '닫기'를 서로 대체하지 않는다. 저장 실패/분석 취소 가능 여부는 화면이 전달한다. ProgressCard는 임의 %를 계산하지 않는다.

### 사진·대화상자·기능 내부 API

```kotlin
@Composable
fun PhotoPanel(
    task: CountTask, bitmap: Bitmap?, mode: EditMode,
    showNumbers: Boolean, canEdit: Boolean, isLoading: Boolean,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = 340.dp,
    onAddMarker: (Float, Float) -> Unit = { _, _ -> },
    onRemoveMarkers: (Set<String>) -> Unit = {},
)
@Composable
fun ConfirmDialog(
    title: String, body: String, confirm: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
)
@Composable
fun DetailPhotoActions(
    canAct: Boolean, onPick: () -> Unit, onCamera: () -> Unit,
    onDelete: () -> Unit, modifier: Modifier = Modifier,
)
```

PhotoPanel의 modifier는 전체 Column, viewportHeight는 Canvas에만 적용한다. 대화상자의 modifier는 AlertDialog에 적용한다. DetailPhotoActions의 여러 UI 루트는 하나의 Column으로 묶어 modifier를 정확히 한 번 적용한다. 기존 ConfirmDialog/DetailPhotoActions의 trailing lambda 호출은 onConfirm/onDelete 명명 인자로 바꿔 마지막 Modifier 인자 추가와 충돌하지 않게 한다. 앱 내부 계약은 코드 컨벤션에 따라 internal을 기본으로 하며 오래된 중복 overload를 남기지 않고 호출부·테스트를 함께 갱신한다.

PhotoInputCard/RecordCard는 계획의 값·이벤트·modifier 계약 유지. CorrectionToolbar/ConfirmAction은 feature/count에 둔다. PhotoLayout은 계수·완료 양쪽이 사진 높이 계산을 사용하므로 feature/shared에 둔다. 같은 기능에서만 쓰는 UI를 shared로 승격하지 않는다. PhotoPanel은 높이를 받으므로 계수·완료 화면이 필요한 배치를 계산해 전달한다.

## 5. 미래 UI에 적용하는 판단 절차

새 UI마다 기존 컴포넌트의 의미/상태/이벤트와 일치하는지 먼저 확인한다. 같으면 값·슬롯·Modifier로 조합한다. 실제 차이가 슬롯 본문이면 슬롯을 추가하고, 동작 자체가 다르면 기능 내부 컴포넌트를 만든다. 재사용자가 실제로 생겼을 때만 shared로 이동하고 모든 호출부·상태 검증을 같이 갱신한다.

미채택 후보: 앱 전체 ScreenScaffold, 모든 Button/Text 래퍼, PhotoViewport/PhotoDisplayTools 독립 분리, 범용 component style/DSL. 승인된 화면이 추가되거나 서로 다른 소비자가 필요한 조작/내용을 요구할 때 재검토한다. 새 화면 기능을 이 후보 설계로 승인받은 것으로 해석하지 않는다.

## 6. 수락·검증 계약

- ComponentContractUiTest: 외부 testTag/width/click Modifier가 루트에서 동작하고 callback 1회. modifier를 두 하위 노드에 재적용하면 실패해야 한다.
- NoticeCard: title/actions null에서 빈 행 없음, content를 한 번만 구성, tone 변경 시 본문의 remember 상태 유지, action 추가/제거 때 본문 유지, action 버튼 클릭 1회.
- StatusLabel: 세 값의 문구·비조작 의미. count.summary/홈 card가 같은 상태 매핑을 사용하며 task의 저장/확정 값은 변경하지 않음.
- State/value 계약: CorrectionToolbar 클릭이 콜백을 보내도 호출자가 mode를 갱신하기 전에는 selected가 바뀌지 않음. disabled일 때 콜백 없음.
- 합성 fixture만으로 라이트/다크·미분석/0/300·저장 실패·읽기 전용 Preview. VM·Room·실제 파일·네트워크 없이 구성. Preview는 실행 테스트와 구분.
- 기존 저장/복원/겹친 표시·대체 입력 회귀와 큰 글꼴/터치 검증은 디자인 구현 계획에 통합한다. 인터페이스 중복·외부 modifier 무시·도메인 객체 변경 여부는 diff 리뷰.

현재 계약 테스트는 대상 컴포넌트 패키지, 통합 테스트는 루트, 합성 fixture는 androidTest/feature/shared, Preview는 src/debug/.../preview에 둔다. NoticeCard 슬롯 수명·외부 Modifier·부모 소유 모드·상태 구분과 기존 저장/복원 회귀를 검증한다. 실제 실행 수와 미실행 항목은 검증 이력을 따른다.
