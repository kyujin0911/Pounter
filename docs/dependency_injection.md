# Koin 의존성 주입

갱신일: 2026-10-13. [Issue #3](https://github.com/kyujin0911/Pounter/issues/3). 선행 [구조 정리 #1](https://github.com/kyujin0911/Pounter/issues/1)은 completed이며 원격 main `64f47000626ad7f1a35bc48363e05b2bc3655b0d`에 squash 반영됐다. 그 main에서 사용자 지정 `codex/3-koin-di`를 생성했다. 이번 브랜치 이름은 일반 작업 유형 우선 규칙에 대한 사용자 명시 요청의 예외다. 선행 브랜치를 병합하거나 기존 이력을 재작성하지 않았다.

## 버전과 의존성

Koin 4.1.1을 선택했다. [공식 릴리스 설명](https://insert-koin.io/docs/support/releases/)에서 Kotlin 2.1.21, Compose 1.8 계열, Lifecycle/Navigation 2.9 계열을 확인했다. 현재 앱의 Kotlin 2.1.21·Compose 1.8.3·Lifecycle 2.9.4·Navigation 2.9.8과 맞춰 사용한다. 최신 Koin 4.2 계열은 Kotlin 2.3 이상을 기준으로 하므로 이번 작업에서 툴체인 업그레이드를 동반하지 않는다.

직접 추가한 의존성은 `io.insert-koin:koin-androidx-compose:4.1.1` 하나다. Android 초기화와 Compose ViewModel 지원이 전이 의존성에 포함된다. [4.1 Compose 문서](https://lts.insert-koin.io/docs/4.1/reference/koin-compose/compose/)와 릴리스 설명에 따라 `koinViewModel`을 사용한다. Navigation 전용 artifact·annotation·Koin KSP·새 Gradle 모듈을 추가하지 않는다. 기존 Room KSP는 유지한다.

## 생성과 수명

`di/AppModule.kt`의 `appModule()`은 컨테이너별 등록 정의를 만든다. 제품에서는 `PipeApplication.onCreate`가 한 번 시작한 Koin 컨테이너에 등록한다. 별도 Compose 컨테이너나 Activity 초기화는 없다.

| 객체 | 등록·소유 수명 | 생성·연결 |
|---|---|---|
| CountDatabase | single, 앱 프로세스 | Application Context, 기존 counts.db, 기존 schema version 1 |
| TaskDao | single, 앱 프로세스 | 위 DB의 tasks() |
| PhotoStore | single, 앱 프로세스 | Application Context, 기존 사진 저장 경로 |
| PipeDetector | single, 앱 프로세스 | 기존 ValidationRequiredDetector |
| LocalCountRepository / CountRepository | 하나의 single, 앱 프로세스 | DAO·PhotoStore·PipeDetector, interface bind로 같은 객체 조회 |
| HomeViewModel | viewModelOf, Home entry | Repository·entry SavedStateHandle 생성자 주입 |
| CountViewModel | viewModelOf, Work entry | Repository·entry SavedStateHandle 생성자 주입 |
| RecordViewModel | viewModelOf, Record entry | Repository·entry SavedStateHandle 생성자 주입 |
| PhotoImportHandler | 해당 화면 ViewModel | 기존 직접 생성, Repository·SavedStateHandle·viewModelScope |

single은 컨테이너 내 지연 생성이며 화면 전환·Activity 재생성으로 새 DB/Repository를 만들지 않는다. ViewModel 등록은 생성 정의일 뿐이다. 실제 재사용·종료는 `PipeNavigation`에서 명시한 entry의 ViewModelStore와 복원 key에 따른다. 같은 ViewModel 클래스의 다른 entry는 별도 객체다. SavedStateHandle을 singleton으로 등록하거나 taskId를 별도 전역 상태로 만들지 않는다.

di는 생성·연결·수명 선언만 담당한다. data/core는 di를 참조하지 않으며 Repository·ViewModel의 생성자는 Koin API에 의존하지 않는다. NavController는 UI에 있고 ViewModel에 Activity·Context를 주입하지 않는다. 화면·공유 사진 UI에는 Repository/DAO 조회를 추가하지 않는다.

## 유지한 동작과 제거한 생성 코드

Application은 Koin에서 동일 LocalCountRepository를 얻어 기존 IO coroutine의 `finishDeletes()`를 호출한다. tombstone 재처리와 미참조 사진 정리는 Repository에 그대로 남는다. 실패 로그와 다음 시작 재시도도 유지한다. 이 처리는 화면 조회와 같은 Repository의 기존 Mutex를 사용한다.

Application의 photos/repository lazy 생성, Activity의 Application cast·Repository 전달, Navigation의 `entryViewModel`·수동 `viewModelFactory`·`initializer`·`createSavedStateHandle`을 제거했다. ViewModel/Repository 생성자와 데이터·복구·화면 전환 로직은 변경하지 않았다.

기존 `LEGACY_ENTRY_VIEW_MODEL_KEY`, taskId/pendingSave/importedTaskId, Route SerialName, applicationId, DB 이름·버전·schema·payload·사진 경로를 유지한다. 검출기는 계속 검증 대기이며 자동 검출 구현·실험을 포함하지 않는다.

## 검증 방식

DI 기기 테스트에서 제품 Application의 실제 등록과 singleton identity를 확인한다. Navigation 회귀 테스트는 `koinApplication`과 `KoinIsolatedContext`로 대역 Repository를 등록하고 종료 시 해당 컨테이너만 닫는다. 제품 컨텍스트가 이미 있는 상태에서 Compose `KoinApplication`을 사용하면 4.1.1의 loader가 전역 컨텍스트를 재사용하므로 테스트 격리에 사용하지 않는다. 컨테이너별 `appModule()` 정의로 singleton factory도 공유하지 않는다.

기존 JVM 테스트는 계속 생성자에 대역을 전달한다. 실행한 클래스/메서드와 실제 결과·미검증은 [검증 이력](verification.md)에 기록한다. Activity 재생성·저장 상태 복원 테스트는 실제 OS 프로세스 종료 검증을 대신하지 않는다.
