# Pounter 코드 컨벤션

갱신일: 2026-10-12. 이번 문서는 변수 네이밍 규칙과 다음 구조·컨벤션 정리 작업의 적용 기준을 확정한다. 실제 소스 변수명 변경·파일/패키지 분리·포맷터·Koin 적용은 별도 구조/DI 작업에서 수행한다.

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
