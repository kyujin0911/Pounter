# 구현·실험 검증 이력

이전 구현·문서 저장·이번 Navigation 검증을 구분한다. 아래 이전 결과는 그 시점의 코드/실험에만 해당한다. 문서 저장 단계에서는 빌드·테스트·검출 실험을 재실행하지 않았다. 이번 Navigation 결과는 마지막 별도 절에 기록하며 과거 결과를 새 실행이나 최신 복합 검출의 합격 근거로 쓰지 않는다. 최신 요구는 [PRD](../PRD.md), 현재 채택 접근은 [자동 계수 설계](auto_counting.md)를 참조한다.

## 당시 확인

- 디버그 APK 빌드 성공.
- JVM 테스트13개 통과: 표시/되돌리기/확정 불변, 영역 초기화, 영역 밖 추가 거절, 동심원 중복 제거, 확대 좌표 변환, 상태 복원, 저장 실패 재시도, 중복 확정, 취소 후 늦은 분석,0개 검출, 검출 적용 게이트.
- 초기 AGP의 Compose Lint 도구 충돌은 AGP8.13.2·Gradle8.13으로 해결. 검사를 비활성화하지 않았다. Lint 오류0개, 버전 업데이트 안내 경고24개.
- Small_Phone(API36,720×1280) 에뮬레이터 테스트8개 통과: 실제 Room·파일 가져오기/보정/재열기/확정/삭제, stale revision 거절, 삭제 중단 복구, 잘못된 이미지 거절, DB 삽입 후 취소 시 사진 보존, 합성12개 원형의 네이티브 검출, Compose 보정→되돌리기→확정→Activity 재생성→재열기→삭제, 다크·글꼴200% 조작 도달.
- JVM13+에뮬레이터8=총21개 테스트 통과. 합성 원 검출 결과는 현장 성능 근거가 아니다.
- 라이트 홈·수동 보정·확정·다크 큰 글꼴 화면 이미지를 로컬에서 직접 확인. 사진은 테스트가 만든 합성 이미지이며 사용자 현장 사진을 화면 증빙으로 사용하지 않았다.
- OpenCV 프로토타입과 의존성은 androidTest 전용. 실제 앱 APK에는 포함하지 않는다. 독립 검출 후보의 테두리 지지 검사로 합성 원 과검출을 수정했으나 현장 검증 게이트는 유지한다.
- 사진/DB 작업을 직렬화하고 가져오기 확정 구간은 취소로 참조 사진이 삭제되지 않도록 보호. 앱 시작 시 삭제 tombstone과 DB 미참조 내부 사진을 정리한다.

## 현장 사진 기술 조사

사용자 제공3장에 기본 원형 검출을 적용한 결과 후보426/444/605개. 각 사진을 시각 확인했으며 바닥·반사 등의 과검출로 채택 불가. 실제 정답 개수/위치는 사용자 확인을 받지 않았다. 로컬 Python 실행시간을 Android 기기 속도로 보고하지 않는다.

기울어진 단면과 주변 묶음을 포함한 사진이므로 정면 조건의 합격 성능을 판정할 충분한 자료가 아니다. 사용자 결정: 조건 유지·추가 사진 검증. 앱 검출기는 ValidationRequiredDetector이며 프로토타입은 검증용으로 분리한다.

로컬 입력·오버레이는 docs/local-validation 아래(Git 제외). 스크립트 scripts/probe_detector.py로 재현할 수 있다. opencv-python-headless==4.12.0.88 사용. 이미지 폴더는 인자로 전달한다.

## 당시 미검증 항목 — 최신 인수 조건은 PRD 참조

- 실제 정면 사진20장(튜닝10/검증10), 사람 확인 정답 개수·위치.
- 최대300개 표시 밀도·자동 검출 보정량5%·최종 정답 일치.
- 대표 실기기 첫 분석/반복 분석5초, 메모리·발열.
- 실제 카메라 및 Photo Picker 취소/권한 취소/공급자 오류.
- 실제 TalkBack 사용성과 확대/삭제의 현장 사용성.
- 백업/기기 이전 실동작: manifest/XML 제외 규칙과 실기기 이전은 구분.
- 프로세스 강제 종료 복원: 회전/Activity 재생성과 DB 재열기 테스트를 프로세스 종료 검증으로 확대하지 않는다.

자동 계수 MVP 완료 판정은 보류한다.

## Navigation 화면 분리 검증 — 2026-10-12

범위: 기존 수동 표시 재사용·세 목적지·ID 기반 entry ViewModel·저장 실패 재시도·복원. 자동 검출기/사진 처리/DB 스키마는 변경하지 않았으며 검출 실험을 실행하지 않았다.

### 이번 실행 결과

- assembleDebug 성공, testDebugUnitTest 19개(모델 7 + ViewModel 12) 통과.
- lintDebug 성공: 오류 0, 경고 26. GradleDependency 20, NewerVersionAvailable 4, OldTargetApi 1, AndroidGradlePluginVersion 1. 검사 비활성화나 전체 의존성 업그레이드 없이 유지.
- Small_Phone API 36: connectedDebugAndroidTest 15개 통과(Navigation 8 + 기존 실제 Repository 5 + 앱 흐름 1 + 큰 글꼴 1).
- Navigation: 저장 실패 시 계수 화면 유지·뒤로 이동 차단·동일 변경 재시도 후 기록으로 교체, 완료 후 뒤로 목록, 목록 복귀/초안 재열기, 없는 ID 복귀, 읽기 전용 기록/삭제 실패 유지·성공 복귀, 잘못된 종류의 경로 정정, 입력 취소, 새 사진 성공/실패 시 이전 작업 보존.
- 입력 결과 테스트는 ActivityResultRegistry 대역으로 취소/성공을 전달했다. 실제 시스템 Picker/삼성 카메라 조작 검증으로 확대하지 않는다.
- 실제 앱/Room/파일 흐름: 수동 추가·삭제/되돌리기·완료·재열기·삭제, 작업 및 기록 화면 각각 Activity 재생성 후 복원. 화면 회전 또는 OS 프로세스 종료와 구분한다.
- SavedStateHandle에 저장된 실패 보정 스냅샷으로 새 ViewModel을 만들어 복원·동일 변경 재시도하는 JVM 테스트 통과. 실제 OS 종료/Bundle 전달 검증으로 확대하지 않는다.
- 이번 앱 테스트에서 생성한 합성 사진의 홈/보정/읽기 전용 기록 및 다크 큰 글꼴 화면을 직접 확인. 실제 단면 계수 정확도 증거가 아니다. 이미지는 로컬 임시 경로에 두고 커밋하지 않음.
- 중간 실행에서 이전 저장 실패 기대값과 추가 테스트의 컴파일 오류를 수정했다. 최종 관련 검증은 위와 같이 통과했다. 합성 원 검출 메서드는 명시적으로 제외했으며 최종 보고서에 실행되지 않은 것을 확인했다.

실행 명령(JDK 17 사용):

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.notClass=com.example.pipecounter.RepositoryInstrumentedTest#prototypeLoadsNativeLibraryAndDetectsSyntheticCircles'
```

### 미검증과 최소 확인 절차

- S22+ Android 16: 연결 후 실제 사진 선택/촬영·취소·작업 복원·완료/삭제·뒤로 가기를 실행. 현재 기기 정보는 사용자 보고다.
- 실제 회전: 작업/기록/입력 복귀에서 회전 후 ID·표시·현재 화면을 확인. Activity 재생성 테스트만으로 통과 처리하지 않음.
- 백그라운드 프로세스 종료 복원: 저장된 초안 또는 실패 보정 화면에서 홈으로 보내 상태 저장 후 프로세스를 종료하고 Recents로 복귀. back stack·경로 ID·영속 표시·가능한 미저장 스냅샷을 확인. 자연적인 OS 메모리 회수와 통제된 종료도 구분해 기록.
- 강제 종료 재실행: force-stop 후 런처로 열어 목록에서 마지막 저장 작업을 재열기. 강제 종료에서 미저장 SavedState 보존은 보장하지 않음.
- 실제 TalkBack/삼성 시스템 입력 UI/백업·기기 이전 동작은 미검증. 현장 300개·5%·5초와 복합 OpenCV는 이번 검증 범위 밖.

SavedStateHandle 복원 경계의 공식 근거: [Saved State 모듈](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate). 검출 성능은 계속 PRD 인수 조건과 실제 정면 자료로 별도 평가한다.

## Pounter 이름 변경 검증 — 2026-10-12

- 프로젝트명·표시 이름 Pounter, main/test/androidTest 패키지·namespace·applicationId io.github.kyujin0911.pounter로 변경. DB 스키마 경로 이동 전후 내용 동일 확인.
- assembleDebug·testDebugUnitTest(19개)·lintDebug 성공. Lint 오류 0, 경고 26.
- Small_Phone API 36 관련 기기 테스트 15개 통과. 합성 원 검출은 제외했으며 검출 구현·실험 없음. Navigation·수동 보정·Room/파일 저장·재열기·삭제·Activity 재생성 확인.
- 생성 APK의 applicationId·MainActivity 패키지·표시 이름을 aapt2로 확인. FileProvider는 applicationId 기반 authority 유지.
- Android Studio IDE 화면의 Sync/프로젝트명 표시, 실제 S22+·삼성 카메라·OS 프로세스 종료·기존 앱에서 새 앱으로 데이터 이전은 미검증. SDK 위치·DB 버전·기존 앱 데이터는 변경하지 않음.

이번 기기 실행 명령:

```sh
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.notClass=io.github.kyujin0911.pounter.RepositoryInstrumentedTest#prototypeLoadsNativeLibraryAndDetectsSyntheticCircles'
```

## 파일·책임·패키지·명명·포맷 정리 — 2026-10-12

범위: [Issue #1](https://github.com/kyujin0911/Pounter/issues/1), codex/1-code-structure. 기준 main e30d20c, 포맷 커밋 e8eabb2, 구조 코드 커밋 8a7f855, 공유 표시 삭제/읽기 전용 테스트 커밋 b903d69. Koin·자동 검출 구현/실험·push·PR/병합은 제외했다. 초기 Issue 생성의 403은 사용자 재개 요청 후 재시도로 해소됐으며 실제 Issue 생성 성공을 확인했다.

### 이번 실행 결과

- spotlessApply/spotlessCheck, assembleDebug, testDebugUnitTest, lintDebug 성공. JVM 27개: Count 12, 모델/좌표 7, Home 2, Record 4, 가져오기 복원 1, 경로 직렬화 호환 1. 기존 검증을 이번 결과로 복사하지 않았다.
- Lint 오류 0·경고 26: GradleDependency 20, NewerVersionAvailable 4, OldTargetApi 1, AndroidGradlePluginVersion 1. 기존 SDK/의존성 경고는 유지했고 검사 비활성화·전체 업그레이드는 하지 않았다.
- Small_Phone API36(Android16) arm64 에뮬레이터: 허용된 UI 10개(Navigation8 + 앱1 + 큰 글꼴1), Repository6개 통과. 16개 선택 실행 성공 후, 마지막 DB 쓰기 전 값 이름 정리 뒤 빌드/JVM/Lint와 Repository6개를 다시 실행해 통과했다. 고유 테스트 수를 반복 실행만큼 늘리지 않는다.
- UI: 입력 취소/실패·가져오기 후 기존 작업 보존, 실패 확정/재시도, 편집 entry 제거, 없는 ID 복귀, 읽기 전용 기록·삭제 실패/성공, 잘못된 경로 종류 정정, 수동 추가·표시 목록 삭제·되돌리기·확정·재열기·삭제. 확정 기록의 표시 목록에서 삭제 부재를 추가 확인했다. 작업/기록의 Activity 재생성 확인. 시스템 Picker/삼성 카메라 조작은 ActivityResultRegistry 대역과 구분한다.
- JVM: Count/Record의 목록 미구독, Home 목록 갱신/오류, Record 삭제 실패/중복 방지, 기존 importedTaskId 복원·entry별 처리, 저장 실패 보정/확정 스냅샷·동일 변경 재시도, 취소된 늦은 결과 차단. 실제 OS 종료 복원으로 확대하지 않는다.
- 호환: 이전 v1 SQL/identityHash와 기존 JSON payload로 별도 SQLite 파일을 만든 뒤 새 CountDatabase/Repository에서 마이그레이션 없이 열고 revision·표시·확정과 payload 원문을 확인했다. Room 스키마 경로 이동 전후 바이트 동일·TaskCodec JSON key 동일·applicationId/DB version/key 유지 확인. 원래 앱 데이터나 사진을 초기화하지 않았다.
- 화면: Gradle 정리 후 합성 QA 화면 확보를 위해 앱/큰 글꼴 두 테스트만 adb instrument로 추가 실행(2개 성공). 홈·보정·확정 기록·다크 큰 글꼴 스크린샷 직접 확인. 이미지는 로컬 임시 저장, 미커밋. 단면 정확도/현장 성능 자료가 아니다.
- 구조/명명 리뷰: 주요 파일명·패키지 경로·금지 import 방향, shared UI의 데이터/callback 계약, readonly 기록, 상태/Job/세대/저장 단계/좌표 단위, 모델 속성과 영속 key 유지 확인. 임시 경로/import 비교 스크립트를 사용했으며 패키지 규칙이 Gradle로 강제된다고 보고하지 않는다.
- 문서 링크·git diff --check 통과. 기존 CodeRabbit의 UI 경로를 feature/navigation으로 맞추고 컨벤션 참조 추가. YAML 파싱 및 minimatch의 feature/navigation/data 경로 표본 확인. 원격 CodeRabbit 리뷰 적용은 미검증.
- 중간 컴파일에서 이동 후 import 누락 등을 수정했으며 최종 위 검증 통과. 자동 승인 검토가 불확실한 메서드 제외 필터 실행을 거절해, 아래처럼 허용 클래스/메서드를 명시했다. 최종 실행 목록에 Hough 프로토타입이 없는 것을 확인했다. 검출 코드는 알고리즘 변경 없이 파일 위치/import/포맷만 정리했다. 추가 앱 흐름 테스트에서 배경 삭제 모드와 다이얼로그 버튼이 함께 검색돼 실패한 선택자를 Dialog 범위로 한정해 수정했고, 삭제/읽기 전용 검증까지 최종 앱 테스트가 통과했다.

대표 실행 명령(기존 Java17 환경, 자동 검출 실험을 제외한 명시적 선택):

```sh
./gradlew spotlessCheck assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.AppUiTest,io.github.kyujin0911.pounter.LargeFontUiTest,io.github.kyujin0911.pounter.NavigationUiTest,io.github.kyujin0911.pounter.RepositoryInstrumentedTest#importEditReloadConfirmAndDelete,io.github.kyujin0911.pounter.RepositoryInstrumentedTest#staleRevisionCannotOverwriteSavedChanges,io.github.kyujin0911.pounter.RepositoryInstrumentedTest#interruptedDeletionIsCompletedOnStartup,io.github.kyujin0911.pounter.RepositoryInstrumentedTest#invalidInputDoesNotReplaceExistingRecord,io.github.kyujin0911.pounter.RepositoryInstrumentedTest#cancellationAfterInsertKeepsReferencedPhoto,io.github.kyujin0911.pounter.RepositoryInstrumentedTest#legacyVersionOneDatabaseAndPayloadReopenWithoutMigration'
```

### 미검증·다음 확인

- Galaxy S22+/Android16 실기기 미연결. 실제 선택·촬영/취소·보정·확정/삭제를 실행해야 한다.
- 실제 기기 회전, OS 메모리 회수/백그라운드 프로세스 종료 후 Recents 복원, force-stop/런처 재시작은 별도 미검증. Activity 재생성·SavedStateHandle JVM 테스트를 대신한 증거로 사용하지 않는다. 위 Navigation 검증 절의 최소 확인 절차를 따른다.
- 실제 TalkBack·삼성 시스템 입력 UI·백업/기기 이전·현장 300개/5%/5초 미검증. 자동 계수는 검증 대기 상태 그대로다.
- Android Studio IDE Sync/개별 기기 화면과 원격 CodeRabbit 설정/PR 적용은 미검증. 이후 원격 운영은 사용자 요청 범위에 따른다.


## Koin 의존성 주입 검증 — 2026-10-13

[Issue #3](https://github.com/kyujin0911/Pounter/issues/3), `codex/3-koin-di`. 선행 #1은 completed이고 원격 main `64f4700`에 구조 정리가 squash 반영된 것을 원격 SHA 조회·fetch·앱 코드/컨벤션 diff로 확인했다. 그 origin/main에서 시작했다. 선행 브랜치 자동 병합·push·PR 생성/병합은 수행하지 않았다.

### 실제 실행 결과

- `spotlessApply spotlessCheck assembleDebug testDebugUnitTest lintDebug`: 성공. JVM 테스트 28개, 실패·skip 0. 기존 생성자 대역 주입 테스트를 그대로 실행했다.
- Lint: 0 errors, 27 warnings. target/compile SDK·기존 의존성/Gradle 및 Koin 최신 버전 안내다. Koin 4.1.1은 현행 Kotlin 2.1.21 호환성을 기준으로 선택했고 경고를 숨기거나 툴체인을 일괄 업그레이드하지 않았다.
- `ANDROID_SERIAL=emulator-5554`의 Small_Phone API 36(Android 16): allowlist 기기 테스트 18개 통과, 실패·skip 0. 연결된 다른 에뮬레이터는 실행 대상으로 선택하지 않았다. 아래 class 인자를 사용했다.

```text
io.github.kyujin0911.pounter.DiInstrumentedTest
io.github.kyujin0911.pounter.NavigationUiTest
io.github.kyujin0911.pounter.AppUiTest
io.github.kyujin0911.pounter.RepositoryInstrumentedTest#importEditReloadConfirmAndDelete
io.github.kyujin0911.pounter.RepositoryInstrumentedTest#staleRevisionCannotOverwriteSavedChanges
io.github.kyujin0911.pounter.RepositoryInstrumentedTest#interruptedDeletionIsCompletedOnStartup
io.github.kyujin0911.pounter.RepositoryInstrumentedTest#invalidInputDoesNotReplaceExistingRecord
io.github.kyujin0911.pounter.RepositoryInstrumentedTest#cancellationAfterInsertKeepsReferencedPhoto
io.github.kyujin0911.pounter.RepositoryInstrumentedTest#legacyVersionOneDatabaseAndPayloadReopenWithoutMigration
```

`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=`에 위 목록을 쉼표로 연결했다. 전체 RepositoryInstrumentedTest 클래스와 `prototypeLoadsNativeLibraryAndDetectsSyntheticCircles`는 실행하지 않았다. androidTest APK에 기존 프로토타입을 컴파일한 사실은 검출 실험 실행이 아니다.

| 검사 | 실제 확인 범위 |
|---|---|
| DiInstrumentedTest 1개 | Application의 Koin 시작, DB/DAO/PhotoStore/Detector/Repository single identity, Repository interface/implementation 동일 객체, ValidationRequiredDetector 등록 |
| NavigationUiTest 10개 | 세 VM 등록·경로 taskId 주입, 같은 CountViewModel 클래스의 별도 entry 인스턴스, 기존 entry 재사용·pop 후 재열기 시 새 인스턴스, 사진 취소/실패/성공, 없는 ID·경로 종류 보정, 저장/삭제 실패·재시도·이동 |
| Navigation 저장 상태 검사 | StateRestorationTester로 Compose 저장 상태와 Navigation WorkRoute/taskId·실패 보정·재시도 유지. 실제 OS 프로세스 종료나 반드시 새 VM의 복원을 입증하는 검사는 아님 |
| AppUiTest 1개 | 실제 Application graph/Room/사진 저장소의 수동 추가·되돌리기·공유 표시 삭제·확정·읽기 전용·재열기·삭제, 계수/기록 ActivityScenario.recreate |
| Repository 테스트 6개 | 가져오기·저장·확정·삭제, stale revision 거절, 중단 삭제 복구, 잘못된 입력·취소 보존, version 1 DB/payload 재열기 |

DI 초기화 테스트를 연결 전 실행하여 `KoinApplication has not been started` 실패를 확인했고 연결 후 통과했다. Navigation 테스트 첫 실행에서는 Compose KoinApplication이 제품 전역 컨텍스트를 재사용하여 테스트 대역이 아닌 실제 Repository를 조회했다. 선택 버전의 공식 소스·실제 객체 로그로 확인하고, KoinIsolatedContext/컨테이너별 module 정의/종료 정리로 수정했다. 수정 후 관련 9개와 최종 allowlist 18개가 통과했다. 테스트 기대값을 약화하지 않았다.

### 보존·정적 확인

- data/core/feature·Room schema·Manifest는 origin/main 대비 diff 없음. applicationId·DB version 1·counts.db·payload/사진 경로·Route SerialName·SavedStateHandle key 유지.
- Repository/VM 생성자에는 Koin/Context/NavController를 추가하지 않았다. data/core/feature의 di·Koin import 없음. Composable의 Repository/DAO 직접 조회 없음.
- Application은 동일 LocalCountRepository의 기존 finishDeletes를 IO coroutine에서 호출한다. 복구·미참조 사진 정리 로직은 data에 유지했다. Application 재시작 시 해당 호출 경로는 정적 확인이며 아래 실제 종료 실험과 구분한다.
- `graphify update .` AST 갱신 성공. 기존 미추적 graphify-out은 커밋하지 않는다. 사용자 .codex/assets/아이콘 파일은 스테이징하지 않는다.

읽기 전용 AI 코드 리뷰에서 차단할 제품 결함 지적은 없었다. 저장 상태 테스트의 살아 있는 ViewModelStore와 실제 새 프로세스/VM 복원을 구분해야 한다는 한계를 확인하여 위 표와 아래 미검증에 반영했다. 이는 인간 승인·원격 리뷰가 아니다.

### 미검증과 최소 후속 절차

- 실제 OS 프로세스 종료 후 새 VM·SavedStateHandle 복원: 작업/미저장 보정 상태에서 백그라운드로 보내 OS 저장 상태를 확보한 뒤 background process 종료·복귀, taskId/pendingSave/표시·재시도 확인. Activity 재생성과 StateRestorationTester를 이 결과로 확대하지 않는다.
- 강제 종료 재실행의 삭제 복구·미참조 사진 정리: 별도 테스트 데이터로 tombstone/미참조 파일을 준비하고 강제 종료·재실행한 뒤 DB/file 정합성 확인. 현재 Repository 복구 테스트와 Application 호출의 정적 확인만 수행했다.
- 실기기 갤럭시 S22+·실제 카메라·회전: 대표 기기로 같은 수동 흐름·회전 확인. 이번 대상은 에뮬레이터이며 Activity 재생성만 실행했다.
- 현장 자동 계수 성능·Hough 실험: 이번 요청의 제외 범위. 검증 대기 검출기를 유지했다.

## 자동 계수 평가 구현 검증 — 2026-10-13

`b1cd809` 승인 계획에서 `feat/auto-counting`으로 직접 실행했다. Task 1~5 구현, Task 6 평가 코드/규약 준비. 기존 사용자 변경/원본 사진은 사용하거나 커밋하지 않았다. 일반 앱 detector와 OpenCV 의존성의 제공 범위는 기존 검증 대기를 유지한다.

- 포맷·debug 앱/test APK 빌드·JVM·Lint: `spotlessCheck assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 통과. JVM 총 52개, 실패 0.
- Small_Phone API36: AppUiTest, LargeFontUiTest, NavigationUiTest, DiInstrumentedTest, DetectionFlowUiTest, AnalysisMetadataInstrumentedTest, AnalysisPhotoInstrumentedTest, CompositeDetectorInstrumentedTest, EvaluationProtocolInstrumentedTest 총 32개 통과. DetectionEvaluationTest/DetectionAcceptanceTest 각 1개는 외부 manifest/config 미제공으로 skip. skip은 현장 합격이 아니다.
- RepositoryInstrumentedTest: importEditReloadConfirmAndDelete, staleRevisionCannotOverwriteSavedChanges, interruptedDeletionIsCompletedOnStartup, invalidInputDoesNotReplaceExistingRecord, cancellationAfterInsertKeepsReferencedPhoto, legacyVersionOneDatabaseAndPayloadReopenWithoutMigration 6개 통과. 기존 Hough 프로토타입 메서드는 이 회귀 목록에서 실행하지 않았다. Task 6 실제 tuning 입력도 없으므로 현장 Hough 기준선 평가 미실행.
- Task 1 메타데이터/기존 v1 SQL, Task 3 EXIF·입력·자원(4개), Task 4 합성 복합 검출·상한·직렬 취소(5개)는 API36/API36.1 양쪽에서 개별 통과.
- 초기 새 타입 테스트는 미구현 API로 컴파일 실패했고, Task 5 저장 실패·snapshot 회귀 2개는 기존 구현의 assertion 실패를 확인한 뒤 통과했다. 환경/파서 오류는 기능 실패 증거와 구분한다. 초기 연쇄 통합 fixture는 포함 관계가 없던 동률 원 3개를 실제 포함되는 축/점수 차이의 연쇄로 수정했으며 기대 개수/포함 기준은 유지했다.
- Task 4 첫 fixture는 앱 startup orphan 정리와 충돌했고, 테스트 사진을 별도 cache 저장소로 분리했다. OpenCV Gaussian 분리의 float convertTo에서 SIGILL을 확인해 양극성 국소 평균 분리로 바꾼 뒤 동일 테스트를 통과했다. 실제 기기 최적화·속도 증거가 아니다.
- API36.1 UI 13개는 기존 Espresso의 InputManager.getInstance reflection 오류로 실패. 테스트 의존성 전체 업그레이드는 하지 않았고, API36에서 UI 13개를 다시 통과했다. API36.1 UI 호환성은 미검증/실패 상태로 남긴다.

실사진의 정답 위치/개수·독립성·현장 고정 config가 없어 튜닝/독립 인수는 실행하지 않았다. S22+ 연결/성능, 5초 사용자 관찰 지연, peak 메모리, 보정 후 정답, 전체 검수 효용, OS 프로세스 종료와 force-stop은 검증 대기. 기존 Navigation의 StateRestorationTester/entry 동작과 이 현장 검증을 동일시하지 않는다. Task 7은 진행하지 않는다.

### 최종 리뷰 수정 후 재검증

독립 전체 리뷰에서 Important 1건(분석 Bitmap의 IO 반환 전달 직전 취소 시 미해제)을 확인했다. 동일 반환 경계를 보류한 회귀 테스트의 해제 assertion 실패를 확인하고, 반환 컨텍스트 바깥에서 미전달 자원을 닫도록 수정했다. 재현 테스트와 정상 사진/복합 검출 10개 통과. Critical/Minor 지적 없음.

수정된 코드에서 포맷·앱/test APK 빌드·Lint 및 JVM 52개 통과. API36의 앞서 명시한 모든 클래스와 Repository 허용 6메서드를 합친 최종 회귀는 **39개 통과, 외부 현장 평가 2개 skip, 실패 0**이다. API36.1 UI는 기존 호환성 실패 상태로 유지한다. 실제 현장 인수·실기기·프로세스 종료를 이 결과로 대신하지 않는다.

집계는 결과 XML의 testcase/skip/failure를 기준으로 했다. 최종 testcase 41개 중 skip 2개이므로 통과는 39개다. Gradle 진행 로그의 Finished 43 표시는 통과 개수 근거로 쓰지 않는다.

## 2026-10-13 디자인 시스템 구현

기준: 구조 변경 `64f4700` + 디자인 문서 결합 `9e03aba`, 브랜치 `codex/design-system-implementation`. 테마/패키지/안내/입력·기록/보정·확정/완료 UI를 각각 `9b8759a`, `5a57148`·`77d710b`, `0030ea3`, `43519dc`, `bdc4cc4`에 적용했다. 자동 검출·저장 모델·Room 스키마·앱 ID·Navigation 경로·의존성은 바꾸지 않았다.

### 자동 실행 결과

- JDK 17에서 `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck`: BUILD SUCCESSFUL. JVM 30개, 실패/오류/건너뜀 0. Lint 오류 0, 경고 26개(주로 기존 버전 업데이트 권고); 기존 권고는 유지한다. 활성 글자/표면 54개 쌍의 대비 4.5:1 이상을 sRGB 계산으로 검사한다. disabled/pressed/사진 위 대비를 이 결과로 대체하지 않는다.
- `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`: Small_Phone Android 16/API36, 720×1280(360×640dp), 40개 통과, 실패/건너뜀 0. 일반 안내 슬롯 수명·외부 Modifier·부모 소유 모드·미분석/0/완료 구분·저장 재시도/입력 잠금·사진 실패 읽기 전용·삭제 확인을 포함한다. 기존 실제 Repository·파일·Navigation·Activity 재생성 검증을 보존했다.
- 고정 확정 버튼: 에뮬레이터 크기 720×2000으로 일시 변경 후 `CountDesignUiTest#zeroReviewStillRequiresConfirmation` 1개 통과. 작은 높이 스크롤 버튼: 기본 크기 및 가로 1280×720의 `LargeFontUiTest#lightAndDarkWorkAtTwoHundredPercentKeepActionsWithinBounds` 1개 통과. 0개도 확인 대화상자를 거치며, 큰 글꼴에서 개수/단위·확정·되돌리기·재시도에 도달한다. 검사 후 wm size를 복구했다. 크기 변경은 물리 기기 회전/자세 전환 검증과 구분한다.
- 화면 캡처 보존을 위한 직접 instrumentation: AppUiTest와 LargeFontUiTest 3개 통과. Gradle 테스트 종료가 앱/캡처를 정리하므로 테스트 APK를 직접 설치해 동일 합성 fixture의 PNG를 로컬 임시 폴더에 보존했다. 사용자 사진·캡처는 커밋하지 않았다.
- RED→GREEN: 토큰/새 계약의 컴파일 실패, 홈 카드 태그, 사진 높이/보정 도구 계약, 삭제 대화상자 루트 등 새 계약 실패를 관찰한 뒤 구현했다. 이미 통과한 읽기 전용·외부 Modifier·200% 테스트는 그대로 보존했다. 과거 문안에 의존한 조회만 새 상태 문구/기록 ID로 바꾸었고 저장·좌표·경로 기대값은 유지했다.
- debug/preview의 독립 fixture로 홈·미분석·0·300·저장 실패·완료·Notice 슬롯 Preview를 작성하고 컴파일했다. IDE Preview 렌더링을 실제로 관찰했다고 보고하지 않는다.

### 화면 관찰과 제한

합성 이미지의 홈·보정·완료, 라이트/다크 200% 개수와 저장 실패 캡처를 직접 확인했다. 가로 크기에서도 200% 작업·오류 캡처를 관찰했다. 개수와 상태가 구별되고 재시도가 오류 설명과 함께 유지되며, 큰 글꼴은 줄바꿈/스크롤로 조작에 도달한다. 밝은 UI/어두운 UI의 토큰 적용과 업무 상태 의미를 관찰했으며 실제 현장 사진 품질의 증거가 아니다.

300개 합성 표시의 기존 번호가 전체 보기에서 겹친다. 번호 숨김/확대/표시 목록·대체 위치 입력은 유지했다. 이번 계획이 제외한 신규 마커 밀도·번호 축약·출처 시각 설계를 완료한 것으로 보고하지 않는다. 다음 검증은 밝고 어두운 독립 실사진에서 번호 on/off·확대 상태·자동/수동 구별·가림 정도를 비교한 뒤 표현 변경 범위를 결정하는 것이다.

TalkBack 실제 낭독·포커스 순서, S22+ 밝은 환경, 실사진 300개 정확한 보정 효율, OS 프로세스 종료와 강제 종료 후 복원은 미실행이다. semantics/대체 입력 테스트와 Activity 재생성이 이 결과를 대신하지 않는다. 최소 다음 절차: 대상 기기에 APK 설치 → TalkBack으로 개수/모드/오류/목록/읽기 전용 이동 → 실사진에서 보정 → 각각 프로세스 종료/강제 종료 후 저장 개수·사진·확정 상태 비교. 가로 검증은 에뮬레이터 크기 변경과 큰 글꼴 작업 화면에 한정했다. 모든 상태·방향 조합 관찰 완료로 확대하지 않는다.

API36.1의 기존 Espresso는 `InputManager.getInstance` reflection 오류로 UI assertion 전에 실패했다. API36에서 검증했으며 이 디자인 작업을 위해 테스트 라이브러리를 업그레이드하지 않았다. API36.1 호환성은 후속 환경 검증 항목이다. 검출 관련 기존 합성 테스트 통과는 현장 검출 성능의 합격 근거가 아니다.

### 실행 중 판단 기록

1. 권장 Native 방식으로 순차 구현하고 마지막에 새 리뷰어 한 명을 사용했다. 잘못된 선택의 비용은 검토 밀도/호출 비용 차이다.
2. 미초기화 worktree 서브모듈 대신 원본 checkout의 Compose Expert를 읽었다. 별도 네트워크 변경 없이 동일 지침을 사용했으며 버전 불일치 시 재확인이 필요하다.
3. API36.1 도구 오류를 피하기 위해 기존 API36 AVD에서 검증했다. 비용은 API36.1 미검증이며 이를 명시했다.
4. 이미 성공한 task 검증을 장부 기록만을 위해 반복하지 않고 최종 전체 suite를 실행했다. 누락 가능성은 최종 전체 결과로 확인했다. 300개 시각 검토도 마지막에 모았다.
5. 바뀐 문안 조회를 상태 문구/기록 ID로 고쳤다. 비용은 잘못된 선택자 가능성이며 Repository·좌표·경로 assertions를 보존했다.
6. 기존에 통과한 읽기 전용/사진 실패/삭제 잠금에 인위적 실패를 만들지 않았다. 새 대화상자 Modifier 계약을 실패 단계로 사용했다.
7. 외부 Modifier와 200% 추가 테스트가 수정 전 통과하여 UI를 불필요하게 바꾸지 않았다. 명시적 import를 적용하고 전체 컴파일/검증으로 전환 누락을 수정했다.
8. 현장·보조 기술·마커 밀도 평가는 실행한 범위와 분리했다. 잘못 판단하면 현장 사용성 문제가 남을 수 있으므로 위 최소 확인 절차를 후속 조건으로 둔다.


### 최종 독립 리뷰와 수정

새 컨텍스트의 Android/Compose 리뷰어가 `9e03aba..d7bee10` 전체 변경을 읽었다. Critical/Minor 없음, Important 1건: 확대·이동한 사진 영역 축소 시 이전 이동 한계가 남아 사진이 영역 밖으로 밀리는 문제. `zoomedPannedPhotoStaysTouchableWhenViewportShrinks`에서 6배 확대·모서리 이동·340→180dp 축소 후 중심 터치 callback 0회 실패를 실제 기기 테스트로 관찰했다. PhotoPanel의 onSizeChanged에서 기존 PhotoGeometry.clampPan을 새 크기로 적용한 후 callback 1회, 정규화 좌표 일치, 확대 6배 유지가 통과했다. 사진 관련 4개 테스트 통과 후 `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck connectedDebugAndroidTest` 전체 실행도 BUILD SUCCESSFUL: JVM30개·기기41개 통과, 실패/오류/건너뜀 0. 이것이 리뷰 수정 후 최종 결과다.

리뷰에서 판정을 보류한 기존 번호 밀도와 실제 TalkBack/현장/프로세스 종료 항목은 위 제한을 유지한다. 번호 표시 방식은 이번에 바꾸지 않으며 다음 수락 조건이다. 영역 축소 보정은 범위 안의 기존 이동량을 유지하고 넘은 값만 제한하는 최소 수정이다. 정규화 중심을 항상 고정하는 새 변환은 추가하지 않았다. 크기 축소로 이동 한계가 줄어드는 만큼 중심은 이동할 수 있으나 확대를 초기화하지 않고 사진과 터치 좌표를 일치시킨다.


## 자동 계수와 디자인 변경의 충돌 해결 검증 — 2026-10-13

`feat/auto-counting`의 `8ebf52a`에서 로컬 main `04cd258`을 병합하던 중 CountWorkScreen.kt와 이 문서의 충돌을 해결했다. 기존 자동 계수/디자인 시스템 검증 절의 내용·기준 SHA·실행 범위·미검증은 각각 그대로 보존했다. 과거 각 브랜치의 테스트 수를 이번 통합 결과로 합산하지 않는다.

- CountWorkScreen: main의 반응형 화면 배치, 디자인 토큰, core 안내 컴포넌트, RecoveryNotice, CorrectionToolbar와 ConfirmAction을 유지했다. 자동 계수 브랜치의 analysisWaiting 안내(이전 분석 정리), 재분석 시 자동 표시/수동 보정 교체 확인을 함께 보존했다. 취소·저장/미저장 잠금·0개 명시적 확정 경로는 그대로다.
- 충돌 해결 전 `testDebugUnitTest`는 충돌 마커와 문법 오류로 compileDebugKotlin 단계에서 실패했다. 이는 동작 assertion 실패와 구분한다.
- 해결 후 JDK17에서 `spotlessCheck assembleDebug testDebugUnitTest lintDebug` BUILD SUCCESSFUL. 결과 XML 기준 JVM **54개, 실패/오류 0**.
- Small_Phone API36에서 명시한 관련 UI 회귀 **33개 통과, 실패/skip 0**. 실행 클래스: DetectionFlowUiTest, feature.count.CountDesignUiTest, feature.shared.DesignStatusUiTest, core.ui.components.ComponentContractUiTest, feature.shared.PhotoPanelDesignUiTest, NavigationUiTest, AppUiTest, LargeFontUiTest(패키지 접두사 io.github.kyujin0911.pounter). 분석 대기/취소·저장 재시도·새 UI 보정/확정·Navigation·200% 글꼴·사진 영역 축소를 검증했다. Hough/복합 검출 실험과 현장 평가 클래스는 이 실행 목록에서 제외했다.
- 읽기 전용 독립 검토: 두 파일의 해결 내용에 지적 없음. 기존 다른 스테이징 변경을 수정하지 않았다. 코드 변경 후 graphify AST 갱신, 충돌 마커와 diff 공백 검사도 확인했다.

현장 정답·독립 자료·S22+ 검출/표시 시간·메모리·검수 효용·실제 OS 프로세스 종료/force-stop·API36.1 Espresso 호환성의 기존 미검증 상태는 유지한다. 이 충돌 해결은 Task 7의 일반 앱 검출기 연결이나 현장 인수 완료가 아니다.

## 자동 계수 개선안 비판 검토와 격리 재현 — 2026-10-14

기준 `feat/auto-counting` / `89bfa63`. 이번 요청은 설계·계획 개정과 필요한 최소 실험이다. 제품 코드·Android 의존성·DI·사진 원본은 변경하지 않았다. [개선 설계 개정 3](superpowers/specs/2026-10-13-auto-counting-improvement-design.md)에 기존 Android 결과와 이번 데스크톱 실험을 구분해 기록했다.

- 현재 후보/통합·PhotoStore·좌표·평가/회귀 코드와 과거 다섯 사진 결과를 대조했다. graphify의 자동 계수 질의로 관련 파일 관계를 확인했다. 코드 수정이 없어 graphify 갱신·앱 빌드·기기 테스트를 재실행하지 않았다.
- OpenCV4.12 공식 API/소스, MSER 원 논문, EDCircles 서지·초록, 저자 구현·라이선스를 조사했다. EDCircles 유료 원문 전체를 읽었다고 보고하지 않는다.
- 실제 로컬 org.opencv:opencv:4.12.0 AAR의 MSER/SimpleBlobDetector 클래스와 getClosestEllipsePoints/fitEllipseAMS/Direct Java API를 확인했다. EdgeDrawing 클래스는 이 AAR에 없었다. Android 대안 빌드·동작 확인과는 구분한다.
- 저장소 밖 Python3.12/OpenCV4.12.0/NumPy2.2.6 환경에서 합성6종·실사진5종의 후보 생성 A(contour)/B(EdgeDrawing)/C1(MSER)을 비교했다. 끊어진 타원 fixture의 중심3px 내 후보 회수는 A=0개, B=1개, C1(MSER)=0개였다. B도 넓은 캡에서 후보2개를 남겨 물체 통합을 해결하지 못했다. 설정·전체 표·script/result hash는 개선 설계 §5에 있다.
- 같은 다섯 원본에 고정 blob 설정과 adaptive binary/inverse 상보성 계산을 실행했다. 원 반경20/중심차1px의 getClosestEllipsePoints 결과와 해석적 거리의 최대오차는 약1.30×10⁻⁶px였다. 이는 해당 수치 fixture의 검증이며 일반 타원 정확도·실사진 성능 검증이 아니다.
- 실사진 입력 hash를 전후 대조했고 원본을 수정하거나 업로드하지 않았다. 실험 script/결과와 개정 전 문서는 저장소 밖 로컬에 보존했다. 문서에는 개인 경로·정답 좌표·오버레이를 넣지 않았다.

이 실험은 원본 decode의 후보 비교이며 PhotoStore JPEG95와 병합을 포함한 Android 계수 결과가 아니다. Python 반복문/native 호출 비용이 달라 데스크톱 측정 시간을 알고리즘·기기 속도 순위로 사용하지 않았다. 위치 정답의 원본 매핑/검토가 없어 이번 실사진 FP/FN·정확도 개선은 미확정이다. 기존 다섯 장의 crop 결과는 개수 차이의 하한만으로도 PRD 보정량 기준 미달이며 독립 자료가 아니다.

현재 상태: 개선 설계·8단계 실행 계획 개정, 알고리즘 채택은 조건부. 원본 위치 정답 동결→동일 입력/metric의 Android A/B 비교→선택한 최소 품질/통합→S22+ 자원→새 세션/묶음의 독립 인수 순서가 남는다. 사용자 보정 후 정답·전체 검수 효용·OS 프로세스 종료/force-stop의 기존 검증 대기를 유지한다. 최초 계획 Task 7 제품 연결을 진행하지 않는다. 이번에 커밋·push·PR 변경·병합을 하지 않았다.

문서 최종 확인: 관련6문서의 로컬 링크/앵커35개, 공백·충돌 마커·코드 fence, 새8단계의 필수 계약, 실사진 후보 표5행과 실험 artifact hash4개를 대조했다. `git diff --check` 통과. PRD 인수 조건 본문이 HEAD와 같고 제품 코드·의존성 diff 및 스테이징 파일이 없는 것을 확인했다. 사진5장의 source hash도 기존 실험 결과와 같았다. 두 개선 문서는 기존 미추적 상태를 유지한다.

## 개정 3 Task 1~3 직접 실행 — 2026-10-14

사용자 요청에 따라 `feat/auto-counting` / `89bfa63`를 재사용했다. 시작 때 수정된 PRD·자동 계수 문서·기존 상세 설계·검증 이력과 미추적 개선 설계/계획, 사용자 assets/아이콘/graphify를 보존했다. 새 브랜치·worktree·커밋·push·PR 변경·병합 없음. 이번 기록은 최초 계획 Task 1~3과 다른 **개선 계획 Task 1~3**이다.

| Task | 코드/실험 상태 | 남은 인수 근거 |
|---|---|---|
| 1 | metric-v1/schema1 보존, 픽셀 거리 metric-v2·최대 대응·ID 대응/미대응, schema2·실제 hash/치수·STARTED journal·단계 실패 보존 구현 | metal-18 위치18개 수동 초안은 DRAFT/groundTruthFrozen=false. cap-151/55·metal-40/37 위치 작성/검토, 허용치 동결·R0 v1/v2 실사진 위치 비교 대기 |
| 2 | bounded trace·정확한 raw/fit/geometry/quality/merge 총량·거절 이유·대표 연결, on/off·0예산·포화·reset 회귀. 실제 JPEG95 경로 full/crop10회·세 마스크 ablation 실행 | 고유 **target** 회수·FP/FN과 물체/배경 오류 분류는 검토된 위치 정답 대기. 근거 없는 원인 UNRESOLVED |
| 3 | G/M/S 합성9종, 공통 support/기하 gate/후처리 비교, 원·회전 타원 표준 거리 Android API 검사, 기존 실사진 후보 재사용. C 고유 회수 근거 없어 확장 보류 | B 단일 contrib SDK의 공식 빌드 사전 점검은 CMake 부재로 중단; NDK/Ninja도 없음. 실제 ABI/호출/자원/재현 build/배포 고지/크기 미검증 |

### 실제 입력·평가 규약

- 과거 자료4폴더와 script/JSON의 SHA-256을 확인했다. 다섯 원본은 존재하며 이전 source hash와 일치한다. 개인 경로·사진·초안 좌표는 저장소 밖에만 보존한다. 금속 원본40·18·37, 번호사진40·37·18의 기존 대응을 유지했다.
- metal-18 원본을 검출 overlay 없이 읽어 중심18개·보이는 짧은 반축·불확실성5px의 초안을 작성했다. 번호 상자 중심이나 후보에 맞춰 조정하지 않았다. `t=.25b`는 미확정 제안이며 검토 상태와 허용치 확대를 혼용하지 않는다. 다른 네 사진은 위치 초안 미작성; cap-55 위치55개도 대기다.
- schema2는 `datasetVersion/truthVersion/annotationVersion`, sample의 `sourceSha256/referenceWidth/referenceHeight/confirmedCount/expected/tolerancesPx/truthSha256`를 요구한다. expected 각 행의 `bPx/uPx/reviewStatus`를 구분한다. truthSha256은 Android JSONArray의 UTF-8 `expected.toString()` 실제 바이트 해시이고, manifestSha256은 원 manifest 파일 바이트 해시다. 다른 JSON serializer의 정렬/공백과 혼동하지 않는다. source는 실제 읽은 파일과 비교한다. code는 실제 app/test APK 해시, config는 실제 파일 해시로 식별하며 metric-v1/v2는 별도 버전이다.
- reference는 EXIF 방향 적용 후 전체 원본이다. source/EXIF/stored/decode/ROI의 실제 치수·offset·x/y scale을 보고한다. import/primary/repeat/비교 전 STARTED를 fsync journal에 기록하고 primary 성공을 먼저 보존한다. 취소는 전파하고 알려진 입력/검출 실패는 sample/단계로 보존한다. 예상 밖 프로그램 오류는 세션 실패 후 전파한다. Hough·original 비교는 명시적 인자 없으면 SKIP이다.
- schema1은 기존 normalized metric을 유지한다. schema2는 동일 예측의 v1 지표와 픽셀 metric-v2를 함께 보고한다. precision/recall 분모0은 null이다. DRAFT는 개발 평가만 가능하며 frozen/independent 인수에는 사용할 수 없다.

### Android R0 진단 — synthetic-v1 그대로, Small_Phone API36

| sample / scope | raw | fit | geometry | quality | merge 결과 |
|---|---:|---:|---:|---:|---:|
| cap-151 / full | 8077 | 5855 | 1225 | 988 | COUNT_LIMIT (406 내부 대표) |
| cap-151 / central_rectangle | 3377 | 2648 | 915 | 732 | 275 |
| cap-55 / full | 6535 | 4602 | 342 | 180 | 73 |
| cap-55 / central_rectangle | 867 | 683 | 235 | 167 | 65 |
| metal-40 / full | 16505 | 10977 | 666 | 142 | 104 |
| metal-40 / central_rectangle | 6791 | 4115 | 372 | 119 | 84 |
| metal-18 / full | 10203 | 7056 | 233 | 76 | 37 |
| metal-18 / central_rectangle | 852 | 527 | 119 | 71 | 32 |
| metal-37 / full | 20919 | 14056 | 811 | 70 | 54 |
| metal-37 / central_rectangle | 8501 | 5240 | 430 | 68 | 52 |

성공9행은 trace on/off의 표시 중심/반경/순서가 완전히 같고 과거 R0 출력73/65,104/84,37/32,54/52 및 cap-151 crop275와 일치한다. cap-151 full은 COUNT_LIMIT로 보존했다. 총 raw는 세 mask 합이고 포화 검사는 mask별 findContours 이후이다; 총량이10,000을 넘는 행을 사전 메모리 상한 위반/정상 출력 절단으로 해석하지 않는다. LIST/CHAIN_APPROX_NONE·R0 gate/quality/merge는 유지했고 TREE 실험은 필요 근거 없어 실행하지 않았다.

Canny/binary/inverse 단독 quality·merge 및 다른 mask와 R0 pair 병합상 구별되는 geometry 기여를 기록한다. 이는 검토된 정답의 고유 회수가 아니다. binary/inverse 상보성이나 후보 감소를 경로 삭제 근거로 쓰지 않았다. 진단은 Mat/Bitmap을 소유하지 않고 상세 entry/point 총 예산을 둔다. 예산 소진은 trace만 잘라내며 총량/검출을 자르지 않는다. 표본이 없는 관측의 원인과 cap 내외곽/같은 rim/질감/다른 묶음의 개별 분류는 UNRESOLVED로 남겼다.

### Task 3 판단과 Task 4 조건

**결론: 모두 보류 — A를 비교 기준선으로 유지하고 B를 조건부 다음 실험 후보로 둔다.** A/B/C의 최종 승자를 선언하지 않는다. C0 blob/MSER의 과거 실험은 재사용했고, 검토된 실사진 고유 회수 근거가 없어 새 C 합집합이나 MSER 탐색은 하지 않았다.

- 공통 설정에서 끊어진 타원은 A 후보회수0/1, B1/1이다. 넓은 cap은 A/B 모두 내외곽 중복을 남긴다. 작고 흐린 rim·접촉·반사/몸체 원호·blank도 비교했다. 편평 타원은 gate 전 A/B 모두 중심 회수1/1, 기존 축비2.5 gate 뒤0/1이므로 생성기 한계와 gate 탈락을 구분해야 한다. 후속 S 카드는 gate 하나만 별도 버전으로 시험해야 한다.
- 같은 크기1px 이동·원의 angle·angle+π·반축 swap·이웃/연쇄를 단순 center/axis/orientation gate와 표준 양방향 최근접점으로 비교했다. 원 해석해와 Android 회전 타원의 고밀도 reference 검사는 통과했다. 단순 정책이 실사진의 이웃을 보존한다는 증거는 아직 없으므로 main 병합 정책을 교체하지 않는다. 맞춤 solver/법선/hierarchy 일괄 구현 없음.
- metal-18 DRAFT의 데스크톱 공통 후처리 결과는 A M18/FN0/FP29, B M18/FN0/FP8이다. **미검토 주석·첨부 원본 decode·시험 후처리 기준의 잠정 진단**이며 Android JPEG95 결과/인수/우승 근거가 아니다. 다른4사진 위치 지표는 대기다. Python A loop와 B native 시간 순위는 제외했다.
- B/C에는 contour fitError가 없으므로0이나 NFA/MSER stability를 edgeSupport로 넣지 않았다. 공통 영상 support를 별도 측정하고 geometry tie-break로 비교했다. Task 4를 열면 평가 계층의 geometry + nullable measured-quality adapter, `DetectionGeometry.kt`의 quality 없는 좌표 변환 overload와 `CandidateMerge.kt`의 명시적 공통 근거 대표 선택 계약을 먼저 확정해야 한다. 기존 EllipseCandidate/R0 score 의미와 Marker/저장 형식은 유지한다. 변경 후보 파일은 I/CandidateExtractor.kt, 신규 I/EdgeDrawingCandidates.kt(선택 시), I/CandidateGenerationInstrumentedTest.kt, I/DetectionEvaluationTest.kt 및 필요 M/DetectionGeometry.kt·CandidateMerge.kt/단위 회귀다.
- B는 [공식 OpenCV4.12 Android builder](https://raw.githubusercontent.com/opencv/opencv/4.12.0/platforms/android/build_sdk.py)의 실제 get_cmake 사전 점검이 `Can't find cmake`로 실패했다. arm64-v8a는 **계획 ABI**일 뿐 빌드된 ABI가 아니다. 단일 contrib SDK를 기존 AAR와 교체하는 격리 경로로 시험해야 하고 두 native 배포본 동시 포함은 금지한다. 현재 app/build.gradle.kts/DI 변경 없음. NDK·CMake·Ninja 설치 허용/완료, 실제 ABI 호출/해제/패키징·재현 build·고지·크기 확인 전 B Android 가능성을 통과로 보고하지 않는다.
- Task 4 진행 조건: 위치 정답/허용치 검토와 동일 입력·공통 후처리에서 실제 고유 누락 회수 또는 correction 개선 확인, 선택한 경로의 Android feasibility 완료, 필요한 최소 adapter 확정. 대표 S22+·독립 자료/시간·peak·사용자 전체 검수는 Task 7/8에서 여전히 대기다. 일반 앱 ValidationRequiredDetector 유지, Task 4~8/최초 Task 7 제품 연결 미실행.

### 최종 검증과 독립 리뷰

- `spotlessApply spotlessCheck assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 성공. 전체 JVM56개 실패/skip0, Lint0 errors/31 warnings(기존 SDK/의존 버전 안내). 일반 APK의 OpenCV 관련 entry0 확인; androidTest 의존성/ValidationRequiredDetector/DETECTOR_VALIDATED=false 유지.
- Small_Phone API36: 아래 명시 목록의 **38개 회귀 통과**, 실패/skip0. 실제 runner에서 잘못된 import 다음 sample의 primary 보존, schema2 좌표/hash/metric, STARTED journal, draft/frozen, metric/trace/API 회귀 포함. 이어 `BaselineDiagnosticsInstrumentedTest#diagnoseLocalDevelopmentSet` 1개 테스트로 실제 full/crop10행을 재검증했고, 과거R0 count와 성공9행 on/off geometry가 동일했다. raw=fit+point/fit 거절, fit=geometry+geometry 거절, geometry=quality+quality 거절의 보존식도 모든10행에서 확인했다. 원본 파일 hash 실행 전후 동일.
- source/truth/config/code/metric/실험script/APK 실제 해시와 환경, 위치 초안·실험 결과·원시 로그는 저장소 밖 별도 로컬 증빙에 보존했다. 개인정보/좌표/사진은 Git diff나 스테이징에 넣지 않았다. staging 비어 있음, HEAD89bfa63 유지.
- `graphify update .` 최종 AST 갱신 성공:2296 nodes/4087 edges/141 communities. 지원하지 않는 Gradle 소스12개 symbol0 및 community label 변화 안내는 남겼으며 semantic 재분석/LLM labeling은 실행하지 않았다. `git diff --check` 통과.

```sh
./gradlew spotlessCheck assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.EvaluationProtocolInstrumentedTest,io.github.kyujin0911.pounter.DetectionDiagnosticsInstrumentedTest,io.github.kyujin0911.pounter.AnalysisPhotoInstrumentedTest,io.github.kyujin0911.pounter.CompositeDetectorInstrumentedTest,io.github.kyujin0911.pounter.CandidateGenerationInstrumentedTest,io.github.kyujin0911.pounter.BaselineDiagnosticsInstrumentedTest#comparisonFailureKeepsPrimaryOnDisk'
```

실사진 진단은 외부 diagnosticManifest를 명시한 `BaselineDiagnosticsInstrumentedTest#diagnoseLocalDevelopmentSet`만 별도로 실행했다. 데이터 경로/전송 명령은 공용 문서에 넣지 않는다. Hough 프로토타입·전체 무관 실험·대표 실기기/현장 인수는 실행하지 않았다.

fresh-context reviewer의 Critical0/Important4를 실제 실패 회귀로 확인해 한 수정 단계에서 처리했다: 초기화 false/예외/LinkageError의 terminal 상태, 값이 같은 두 후보의 서로 다른 observation 연결, 거절 타원의 major/minor swap 각도, 실제 진단 runner의 import/primary/trace-off/각 mask journal 분리와 primary 우선 저장. 네 회귀 RED→GREEN과 위38개 suite 확인. 중간 테스트의 blank-frame 기대 오류와 JUnit Boolean 반환형 오류도 원인을 확인해 수정했으며 최종 결과에 실패를 포함하지 않는다.

미뤄둔 Minor2개: 사용하지 않은 original 비교의 budget/read 결과는 내부 status로 구별하지만 바깥 stage가SUCCESS일 수 있으므로 비교 runner의 통일된 SKIP/FAILURE 매핑 보완이 남았다. CompositePipeDetector의 diagnostics 인자를 nativeProbe 앞에 추가한 positional 호출 호환성도 후속 평가 연결 때 정리한다(현재 저장소 호출은 named/default로 빌드·회귀 통과). original 비교는 기본SKIP이며 이번 정확도 근거에 사용하지 않았다.

이번 판단의 비용: 기존 checkout 재사용으로 기존 문서 수정과 이번 diff가 공존한다. 커밋 금지로 버전 증빙은 로컬 ledger/hash에 남긴다. blank threshold의 frame contour를 실제 관측으로 유지하므로 진단 표본 규약 변경 시 회귀 기대를 검토해야 한다. B/C의 공통 영상 support+geometry 비교는 R0 품질/병합과 다른 시험 경로여서 R0 현장 개선을 입증하지 못한다. 독립 리뷰는 위치 주석의 정확성·B Android feasibility·현장 성능을 판정하지 않았으며 이 입력은 계속 대기다.

세 mask의 아래 값은 **다른 mask의 대표와 R0 pair 병합으로 같다고 판정되지 않은 대표 geometry 수**다. target 고유 회수나 실제 FN이 아니다. 이 결과만으로 경로를 삭제하지 않는다.

| sample / scope | Canny | binary | inverse |
|---|---:|---:|---:|
| cap-151 / full | 64 | 8 | 2 |
| cap-151 / central_rectangle | 50 | 2 | 2 |
| cap-55 / full | 18 | 5 | 0 |
| cap-55 / central_rectangle | 15 | 3 | 0 |
| metal-40 / full | 70 | 5 | 3 |
| metal-40 / central_rectangle | 55 | 3 | 3 |
| metal-18 / full | 14 | 0 | 0 |
| metal-18 / central_rectangle | 9 | 0 | 0 |
| metal-37 / full | 44 | 0 | 2 |
| metal-37 / central_rectangle | 43 | 0 | 1 |

이번 코드 변경 파일(사진·좌표·개인 경로는 제외):

- M/data/detector: `DetectionMetrics.kt`, `CandidateMerge.kt`.
- I 평가/진단: `DetectionEvaluationTest.kt`, `data/detector/DetectionDiagnostics.kt`, `data/detector/CandidateExtractor.kt`, `data/detector/CompositePipeDetector.kt`.
- T 회귀: `DetectionMetricsTest.kt`.
- I 회귀/로컬 probe: `EvaluationProtocolInstrumentedTest.kt`, `CompositeDetectorInstrumentedTest.kt`, `DetectionDiagnosticsInstrumentedTest.kt`, `CandidateGenerationInstrumentedTest.kt`, `BaselineDiagnosticsInstrumentedTest.kt`.
- 문서: 개선 실행 계획·개선 설계의 최신 상태와 이 검증 기록만 갱신했다. 시작 때의 PRD/자동 계수 문서/최초 상세 설계 수정은 보존했다. M/I/T 접두사는 개선 계획의 파일 원칙을 따른다.

## 번호사진 추가와 원본 위치 주석 반영 — 2026-10-14

사용자가 새로 제공한 다섯 번호사진을 참고해 개선 Task 1의 위치 자료를 보완했다. 첨부 순서는 cap-151·metal-18·cap-55·metal-37·metal-40이다. 기존 금속 원본40·18·37 대응과 혼동하지 않는다. 원본/번호사진·좌표·개인 경로는 저장소 밖 로컬 증빙에 보존했으며 앱 코드·의존성·DI·커밋·원격 작업은 변경하지 않았다.

| sample | 사용자 확정 개수 | 원본 중심 초안 | 검토 상태 |
|---|---:|---:|---|
| cap-151 | 151 | 151 | DRAFT |
| cap-55 | 55 | 55 | DRAFT |
| metal-40 | 40 | 40 | DRAFT |
| metal-18 | 18 | 18 | DRAFT; 기존18개 초안의 사용자 번호 대응 보완 |
| metal-37 | 37 | 37 | DRAFT |

총301개다. 번호사진을 대상 식별에 사용하고 주석 없는 첨부 원본에서 중심을 별도로 읽었다. 번호 상자 중심을 좌표 정답으로 복사하지 않았고 검출 출력으로 중심을 이동하거나 개수를 보충하지 않았다. 번호/대상 ID 대응과 새 중심 좌표의 검토는 구분한다. cap-151의 capless11, cap-55의 capless2도 유지했다.

번호사진은 원본 전체와 같은 크기가 아니므로 표시를 제외한 영상 특징으로 원본 대응을 확인했다. 각 사진의 SIFT/RANSAC 전역 대응215~845개, 원본 픽셀 기준 median 잔차0.075~0.132px/p950.386~0.575px였다. 이는 crop/resize 관계 확인이며 중심 주석 정확도 보증이 아니다. 실제 affine·원본/번호사진 SHA-256은 외부 registration/annotation 자료에 기록했다. 원본·번호사진 hash는 실행 전후 동일했다.

각 행은 사용자 번호·고유 ID·원본 정규화/픽셀 중심·bPx·uPx·DRAFT 상태를 가진다. bPx/uPx는 아직 시각 추정이며 측정/오차 교정 완료값으로 보고하지 않는다. 허용치는 초기 제안 `min(.25b, .45×최근접 중심 거리)`로 별도 저장했으며 동결하지 않았다. 주석 불확실성을 허용치 확대에 사용하지 않았다. cap-151 번호125/128/133은 겹친 번호의 정확한 ID 대응 검토가 남고, metal-37 번호19는 종이가 단면 일부에 겹쳐 중심/반축 검토가 필요하다. 배경 묶음의 상세 excluded 위치는 미작성이다. 전체 자료는 development/tuning, session/bundle unknown이며 independent로 전환하지 않았다.

검증: sample별1..N 번호와 ID/좌표 중복 없음, 정규화 범위·치수·양수/finite 허용치와 정확한 key 집합 확인. 기존 빌드의 **실제 Android `validateEvaluationManifest`**로 schema2 manifest의5장/301개를 검증했고, 동일 초안에서 frozen=true를 시도하면 거부되는 것을 확인했다. Android `expected.toString()`의 실제 UTF-8 해시를 기록했으므로 다른 serializer의 순서/공백을 같은 truth hash로 가정하지 않는다. 첫 검증에서 v1 병행 비교용 normalizedTolerance 누락을 확인했고 legacy .02 필드를 보완한 뒤 동일 검증을 통과했다. 이는 새 검출 실행·정확도/FP·FN·현장 인수 결과가 아니다. 앱 빌드/검출/Hough를 불필요하게 재실행하지 않았다.

최종 canonical manifest SHA-256: `2f6f6b13f7a70735c9a4526f2a7734209c629e8c767b16ef93599f1332fe6bff`. 개별 위치 초안과 canonical manifest의 expected 행은 일치하며, 추가 ID 검토 메모는 별도로 보존했다. `groundTruthFrozen=false`를 유지한다. Task 1의 위치 **작성** 대기는 해소됐지만 주석/반축/허용치 **검토·동결**은 남았다. 후보 선택과 Task 4 진행 조건은 그대로다.

## 개정 3 Task 1–3 후속 실행 — 2026-10-14

기존 `feat/auto-counting` / `89bfa63`와 사용자 변경을 보존했다. 이전 보류 두 건을 수정하고, 위치 검토·실제 JPEG95 기준선 평가·격리 native spike를 진행했다. 아래 수치는 **개발 자료와 미동결 허용치의 잠정 진단**이며 현장 인수나 최종 우승 근거가 아니다. Task4~8·최초 Task7 제품 연결·커밋·원격 작업은 수행하지 않았다.

### 위치와 runner

- 번호 상자나 검출 후보에서 중심을 복사하지 않고 원본 확대301개를 검토했다. 사용자는 cap-151의52/53/76/112/148, metal-37의27/29/31/35를 지적했다. 금속4개와 캡5개 수정은 사용자 검토 완료이며 마지막112의 원본 y=765px도 승인했다. 현재 중심301개 검토/0개 대기와 **전체 annotation DRAFT**를 구분한다. 모든 b/u/t는 미검토이며 frozen=false다. 원래의125/128/133 식별·capless11/2·종이 가림19의 대상 포함은 유지한다.
- bPx/uPx와 tPx는 미검토 시각 추정·초기 제안이다. 중심 피드백을 허용치 조정이나 `groundTruthFrozen=true` 승인으로 확대하지 않았다. 최신 위치 버전은 외부 `corrected-draft-v5`; v2~v4 이력도 보존한다. 배경은 번호가 지정한 앞 묶음 밖의 scope이며 상세 excluded 좌표·물리적 FP 분류는 대기다.
- 원본/번호사진10개 hash를 다시 확인하고 정확한 원본 bytes를 외부에 보존했다. 원본1400×1050 또는1050×1400, EXIF/import/decode/ROI의 **실제** 치수·offset·scale·JPEG95 import hash를 runner에서 기록했다. 전역 registration 잔차는 주석 정확도로 취급하지 않는다.
- Android 실제 `expected.toString()` SHA-256, annotation 파일 SHA-256, canonical manifest/config/source/gray/code/APK hash를 각각 외부 index에 기록한다. schema2의5장/301개 및 full/crop10행/602개 검증과 DRAFT frozen 거부를 확인했다. 원본 bytes와 검토용 재인코딩 PNG도 동일 파일 hash로 취급하지 않는다.
- `original` budget 초과·read 실패가 외부 SUCCESS로 포장되는 두 회귀를 실제 RED로 확인했다. budget은 typed SKIP, decode/read는 FAILURE로 수정했다. primary/STARTED/import 실패 보존·취소 전파·schema1/v1은 유지했다. 네 번째 positional nativeProbe 호출의 컴파일 RED를 확인하고 기존 인자 순서를 복원했다.

### R0 잠정 위치 진단

설정은 synthetic-v1 그대로다. 관측만 확장했으며 LIST, mask, gate, quality, merge를 바꾸지 않았다. 위치 export는 명시적으로 켠 실행에만 최대10행·각 trace25000 entry·0 raw point를 허용하고, native/Bitmap을 소유하지 않는다. 상세 JSON은 별도 파일, 누적 summary/journal에는 filename/hash/집계만 남긴다. NaN fit를0으로 만들지 않고 INVALID_FIT로 기록한다. 초기 상세 export에서 NaN 직렬화 실패와 약72MB summary 문자열 할당 OOM을 재현했고 각각 회귀 RED→GREEN으로 수정했다. 이 실패를 검출 실패나 정상0개로 보고하지 않는다.

다음은 v4 중심의 같은 R0 예측에 대한 실제 Android metric-v1/v2와 후보 단계 표다. 마지막112 추가 정정 전 snapshot이므로 v5 결과로 가장하지 않는다. 외부 v5 분석은 같은 예측을 재사용해 버전을 별도 기록한다.

| sample/scope | fit M → geometry M → quality M | 최종 v2 M/FN/FP/correction | 같은 예측 v1 M |
|---|---|---|---:|
| cap-151 full | 149→149→149 | COUNT_LIMIT; 내부 merge406, 최종 위치 지표 없음 | — |
| cap-151 crop | 149→149→149 | 148/3/127/130 |151|
| cap-55 full |55→46→45|45/10/28/38|47|
| cap-55 crop |55→46→45|45/10/20/30|47|
| metal-40 full |40→38→35|35/5/69/74|37|
| metal-40 crop |40→38→35|35/5/49/54|37|
| metal-18 full |18→18→18|18/0/19/19|18|
| metal-18 crop |18→18→18|18/0/14/14|18|
| metal-37 full |30→17→15|15/22/39/61|24|
| metal-37 crop |30→17→15|15/22/37/59|24|

fit 회수는 finite fit 중심의 최대 일대일 위치 대응이다. 같은 위치의 잘못된 경계가 물리적 단면 후보임을 증명하지 않는다. fit 전 raw에는 중심이 없으므로 raw 자체 부재는 UNRESOLVED로 남긴다. gate/quality/merge 사이의 **관측된 중심 회수 손실**과 실제 물리적 원인은 구분한다. 같은 경계·캡 내외곽·오통합·질감·다른 묶음의 구체적 FP 원인은 독립적인 경계/원본 대조가 끝나지 않은 항목을 UNRESOLVED로 유지한다.

full의 quality M(다른 mask의 대응 target 집합에 없는 고유 M): cap151 Canny149(1)/binary148(0)/inverse148(0), cap55 40(1)/44(3)/32(0), metal40 35(20)/11(0)/15(0), metal18 18(1)/17(0)/17(0), metal37 14(12)/2(0)/3(0). 이는 canonical mask별 최대 대응에서의 고유성이고 애매한 대응의 절대적 배타성 증명이 아니다. binary의 실제 고유 회수 사례가 있고 inverse의 이 개발 집합0만으로 경로를 삭제하지 않았다. 성공9행의 trace on/off marker geometry·순서 일치, 전체 stage 집계 보존을 확인했다. RETR_TREE는 실행하지 않았다.

### A/B/C 판별과 Android 가능성

- **A1:** 실제 측정 contour quality와 기존 Kotlin merge 코드를 Android에서 재사용했다. R0 성공9행의 중심/순서를 재현하고 gap만.5→.1로 바꿨다. v4 crop 보정량은 cap151 130→145, cap55 30→48, metal40 54→61, metal18 14→16, metal37 59→62로 악화했다. full cap151도 내부406→452로 COUNT_LIMIT를 유지했다. 이 한 요인 수정은 채택하지 않는다.
- **공통 A/B:** 실제 PhotoStore JPEG95/import/decode/ROI gray의 동일 hash10개에서 Android A trace와 B JNI geometry를 얻었다. 공통 gate7~80/축비2.5, 공통 영상 support≥.65, 같은 simple/표준 closest-point 후처리는 **desktop Python OpenCV4.12 comparator**로 실행했다. A의 고유 median/mask와 B 기본 Sigma1/PREWITT/NFA on/PF off 설정을 기록했으며 같은 중간 edge map이라고 주장하지 않는다. 이것은 완성된 Android B `detect()` 결과가 아니다. B에 fitError=0이나 NFA를 edgeSupport로 넣지 않았다.
- 이 공통 probe의 v4 full 보정량 A/B는 cap55 102/40, metal40 222/41, metal18 29/7, metal37 211/36이다. R0 고유 quality의 보정량과 혼동하지 않는다. cap151은 full과crop 모두 A/B 공통 후처리 결과가300 초과하여 COUNT_LIMIT다. 제한 전 관측 지표를 정상 final 지표로 채택하거나300으로 절단하지 않았다.
- **S:** 동일 후보에서 maxSemiAxis80→120, 또는 axisRatio2.5→5만 각각 전역으로 바꿨다. B metal37 full은 크기 gate 변경으로 M17→23/FN20→14/FP16→22지만 보정량36은 그대로다. 편평 타원은 A/Android B 모두 gate 전 회수하며 축비2.5 뒤 누락된다. 생성기 부재로 분류하지 않고 gate 변경의 비용을 별도 카드에 남겼다. 사진별 튜닝·복합 변경은 없다.
- **C0:** 실제 gray10개에 과거 고정 SimpleBlob 설정을 적용한 desktop screening에서 A/B fit 중심 모두 없는 target25(metal37)의 center-only 고유 가능성1개만 나왔다. 종이/단면 경계에 대응한 검출인지는 미검토이며, full에서106 blob 중7개만 위치 대응한다. blob keypoint size는 ellipse 반축/fit quality가 아니므로 공통 ellipse 후처리/최종 오류로 포장하지 않았다. 경계 adapter와 안정성 증거 없이 합집합/MSER로 확대하지 않는다.
- **B 격리 build:** AAR prefab의 API21/NDK27/shared STL metadata에 맞춰 공식 NDK r27d를 저장소 밖에서 읽기 전용 사용했다. 공식 SHA1 `80f11292080fab4e869799f1d23caa88dcf3c709` 일치. 기존 AAR 헤더·libopencv_java4 한 배포본만 연결한 최소 JNI bridge이므로 CMake/Ninja는 필요하지 않았다. 시스템 SDK·앱 Gradle/DI는 바꾸지 않았다. [공식 NDK 자료](https://github.com/android/ndk/wiki/Unsupported-Downloads), [OpenCV4.12 source](https://github.com/opencv/opencv_contrib/blob/4.12.0/modules/ximgproc/src/edge_drawing.cpp)를 사용했다.
- arm64-v8a/armeabi-v7a/x86/x86_64의 빌드·NEEDED/16KiB LOAD 정렬·재현 hash를 확인했다. 실제 호출은 API36 arm64 에뮬레이터다. circle `[x,y,r,0,0,0]`/ellipse `[x,y,0,a,b,degree]`를 major≥minor/angle modπ radians로 변환했다. blank·타원·정상100·invalid input100·Mat/Ptr 할당 후 native 예외100 및 실제 gray10행을 실행했다. 자원은 RAII, 반환은 복사된 배열이며 native handle을 넘기지 않는다. invalid/native/allocation 예외 변환은 구분한다. 합성9종도 JNI에서 검사했다. 다른 ABI 실기기·실제 OOM·peak·취소 도중 native 종료는 확인하지 않았다.
- bridge arm64의 비용은 약190KiB(압축 약90KiB), 기존 OpenCV native 약22.4MiB는 별도 기존 비용이다. **상류 source에 두 color LUT 약64MiB BSS가 있음**을 ELF/nm으로 확인했다. gray 경로는 color LUT 초기화를 호출하지 않지만 virtual mapping 비용을0으로 간주하지 않는다. bridge load 전후 RSS132724→132852KiB,10행 뒤147292KiB; 정상/할당 후 예외 반복의 native heap967632→976080 bytes는 snapshot이며 leak/peak/대표 기기 합격 증명이 아니다.
- source는 Apache2.0 license/comments를 외부에 보존했다. 기존 AAR에는 LICENSE/NOTICE 파일이 없음을 확인했으며, 실제 앱 배포 때 기존 OpenCV/STL 및 third-party 고지를 준비하는 것은 남은 패키징 조건이다. 이번에는 배포·업로드하지 않았다.

### 검증과 다음 결정

`spotlessCheck assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` 성공. `testDebugUnitTest --rerun`으로 **JVM57** 재실행/실패0 확인. 실제 Android **46개**(BaselineDiagnostics, EvaluationProtocol, CompositeDetector, DetectionDiagnostics, CandidateGeneration, AnalysisPhoto) 통과. 실제 평가 runner의 full/crop10행과 schema2 검증은 별도 실행했다. 원본 비교/Hough/무관한 전체 기기 테스트는 실행하지 않았다. 코드 후 graphify AST 갱신·diff 검사는 최종 확인 기록에 남긴다.

최종 fresh review는 Critical0/Important1/Minor0이었다. mask 상세 파일 쓰기 실패 후 부분 비교가 정상으로 보이거나 다음 sample을 중단하는 문제를 실제 IOException 회귀로 재현했다(RED: JSONException). 성공한 mask 보고서와 대표 목록을 함께 등록하고, 세 mask가 모두 준비되지 않으면 비교 FAILURE/고유 기여 미산출로 보존하도록 수정했다. 같은 회귀 GREEN 및 위46개 suite를 확인했고 primary와 다음 sample도 보존했다.

검토 완료 중심 v5에 같은 R0 예측을 재사용한 잠정 재평가: cap-151의 fit/geometry/quality 회수는150, crop final M149/FN2/FP126/correction128이다. full은 여전히 COUNT_LIMIT이며 나머지 사진 지표는 위 v4 표와 같다. 이는 중심 수정에 따른 metric 변화로 검출 개선이 아니다. v5 공통 A/B 비교도 별도 보존했고 cap151 full/crop 모두 COUNT_LIMIT를 유지한다. b/u/t 미검토 때문에 인수·우승 근거로 사용하지 않는다.

최종 코드 후 `graphify update .` 성공:2319 nodes/4140 edges/136 communities. 지원하지 않는 Gradle 소스12개 symbol0/커뮤니티 이름 변경 안내는 보존하고 semantic 재분석은 실행하지 않았다. `git diff --check` 통과, 스테이징 파일 없음, branch/HEAD 동일, 일반 앱의 ValidationRequiredDetector/DETECTOR_VALIDATED=false 확인. 최종 B bridge4 ABI 재빌드 hash는 모두 동일하며 arm64 bridge193560 bytes/zip89463 bytes다. 다른 ABI는 빌드 검증만 완료했다.

이번 후속 코드 변경은 I의 `DetectionEvaluationTest.kt`, `EvaluationProtocolInstrumentedTest.kt`, `CompositePipeDetector.kt`, `CompositeDetectorInstrumentedTest.kt`, `BaselineDiagnosticsInstrumentedTest.kt`다. 기존 M metric/merge 구현과 사용자 문서·미추적 자료는 보존했다. native source/build/사진/좌표/개인 경로는 저장소 밖 증빙에만 둔다.

**결론: 모두 보류.** A/R0는 비교 기준선, B는 Android 가능성 장벽을 해소한 조건부 우선 후보, C는 미검토 고유 가능성 이상으로 확대하지 않는다. Task4를 시작하지 않는다. 남은 조건은 모든 b/u/t 검토와 동결, 물리적 경계/FP/FN 원인 대조, 같은 기준에서 보정량 개선·반례/COUNT_LIMIT 회귀, 배포 고지/자원 비용을 감당할 연결 정책이다. B를 선택할 경우 I의 최소 geometry+measured-quality adapter와 신규 EdgeDrawingCandidates/JNI, 필요 M/DetectionGeometry의 quality 없는 좌표 변환 및 CandidateMerge의 명시적 대표 선택 계약을 확정해야 한다. 기존 EllipseCandidate/R0 quality·Marker/저장 형식은 유지하며 일반 DI/제품 연결은 별도 승인·독립 자료/S22+ 인수 대기다.

## 원본 주석 검토와 Task4 착수 판단 — 2026-10-14

직접 실행 original-review-1. 같은 feat/auto-counting/89bfa63과 기존 dirty files를 보존했다. 이 기록은 바로 위 **모두 보류/모든 주석·자원 인수를 Task4 선행으로 묶은 판단**을 대체하며 과거 실행 기록은 수정하지 않는다. 이번 변경은 문서·ledger와 저장소 밖 원본 검토/재평가/spike다. 앱/evaluation detector 소스·Gradle·DI·저장 스키마·R0는 변경하지 않았다.

### 주석·scope와 실제 재평가

- 기존 증빙/코드/입력 hash129건 및 canonical manifest 파일 hash를 확인했다. annotation JSON 파일 hash와 Android expected.toString() hash는 별도다. 원본과 예측을 재생성하지 않고 확인된 v5 정답/같은 예측을 재평가했다.
- 원본 crop301개/확대 atlas15개를 시각 검토하고 항목별 기존 b/u/t, 원본 근거·범위 제안·가림/흐림·방법/상태를 외부에 기록했다. 7개는 외곽 짧은 지름 endpoints를 직접 읽었으며 나머지는 시각 범위 제안이다. 반복 주석에 의한 u calibration·전체 정밀 측정·사용자 전체 승인이 아니다. 301개 승인 중심과 개수, capless/기존 포함 ID를 유지했다. 국소 중심/ID 불일치 의심은 해당 항목만 재질문했고 승인 없이 수정하지 않았다.
- metal40 왼쪽 가장자리/metal37 오른쪽 아래는 사용자 답변 **둘 다 별도 묶음**을 원인 주석으로 기록했다. 검출 출력 필터는 만들지 않았다. 번호 유무나 개수를 맞추기 위한 제외가 아니다. crop 중심 밖/짧은 반경 경계 근접 screen0과 원본 프레임 잘림을 별도 기록했으며 회전 외곽 교차를 모두 검증한 것은 아니다.
- P0=min(.25b,.45d)는 개발 제안으로 유지했다. 평가 전에 고정한 .20/.30 및 b 범위는 민감도이며 u를 t에 추가하지 않았다. target별 u 상한8방향 perturbation에서 대응 ID·M/FN/FP가 바뀌었으므로 인수는 보류다. 총M 동일/대응 detection 교체도 보존했다. 이번 sampled probe의 다른 target으로 동일 detection 재할당/동일M의 matched target 집합 대체는0이지만 연속·모든 동시 perturbation의 안전성은 증명하지 않았다. 전체 annotation은 계속 **DRAFT/frozen=false**, 다섯 사진은 development/tuning이다.

아래는 **동일 R0 예측·v5 기존 미동결 t**의 재평가다.

| sample/scope | fit→geometry→quality M | R0 final M/FN/FP/correction |
|---|---|---|
| cap151 full |150→150→150|COUNT_LIMIT, 내부406|
| cap151 crop |150→150→150|149/2/126/128|
| cap55 full/crop |55→46→45|45/10/28/38 · 45/10/20/30|
| metal40 full/crop |40→38→35|35/5/69/74 · 35/5/49/54|
| metal18 full/crop |18→18→18|18/0/19/19 · 18/0/14/14|
| metal37 full/crop |30→17→15|15/22/39/61 · 15/22/37/59|

primary 성공9/실패1, mask비교10 SUCCESS, trace-off9 SUCCESS/COUNT_LIMIT1 SKIP를 재사용했다. 최종 FN76건의 마지막 단계·대표 관계와 미확정 원인을 기록했다. full 실패151개 target 관계를 FN으로 더하지 않았다. fit 중심 대응 부재는 raw 후보 부재의 증명이 아니다.

cap151 crop FP126은 원본/후보 경계 대조로 배타 분류했다: **캡 내외곽43, 같은 경계33, 비대상 묶음27, 질감·몸체·반사13, UNRESOLVED10**. 범주 개수를 FP에 다시 더하지 않는다. 다른 성공행275 FP는 원본 경계 분류가 끝나지 않아 UNRESOLVED와 다음 대조 절차를 남겼다. 전체 FP 원인을 완료했다고 포장하지 않는다.

R0 대표406을 기존 quality 순위/관측 그룹으로 재구성하고 성공9행의 marker 중심을1e-3px 이내로 재현했다. full 내부 M149는 출력 제한 전 oracle 진단이다. 전경과 떨어진 좌/우 배경 box의 대표 중심72/55는 배경 기여를 보여주지만 별도 검출 필터로 쓰지 않는다. 전경과 겹치는 rear box18은18 FP로 분류하지 않았다. full406/crop275의131 차이와 crop 중복76으로 배경 증가·같은경계/내외곽 문제를 함께 좁혔으며 단일 원인으로 확정하지 않는다.

### 제한된 비교와 결정

아래는 **Android 생성 geometry + desktop Python4.12 공통 후처리**의 correction이다. R0 고유 quality/final 결과와 직접 같은 열에서 비교하지 않는다. PhotoStore JPEG95 gray10 hash, gate7~80/ratio2.5, measured image support≥.65와 대표 순위를 고정했다. native NFA/없는 residual을 기존 quality로 위장하지 않았다. Float normalized Marker 환산을 반영했고 이전 공통 표의 수치는 유지됐다.

| sample | 공통 A full/crop correction | 공통 B full/crop correction |
|---|---:|---:|
| cap151 |398/199 (COUNT_LIMIT 전 진단)|239/168 (COUNT_LIMIT 전 진단)|
| cap55 |102/77|40/42|
| metal40 |222/173|41/36|
| metal18 |29/16|7/8|
| metal37 |211/175|36/36|

- 표준 getClosestEllipsePoints same-rim 비교는 A에서 악화/동일, B에서 추가 이득 없음. 단순 정책을 먼저 재현하고 custom solver를 만들지 않는다.
- **신규 변형1개**: 전역 고정 내외곽 postpass. B cap151 full239→101/crop168→32지만 M150→148(#59/#144), cap55 M54→53. 원본 반례는 같은 단면 외곽 대표 선택으로 draft 중심/t 대응을 잃는 사례이며 이웃 단면 오통합으로 단정하지 않았다. 그대로 비채택, Task6의 대표 중심/관계 계약으로 전달. 사진별 튜닝·출력 절단·추가 탐색 없음.
- C0#25는 실제 slanted 단면 근처지만 원형 keypoint가 전체 경계가 아니다. 고정 threshold50/130/210의 component 관측은 중간 threshold의 부분 밝기 영역과 영상 전체 연결을 보여줄 뿐 정확한 Blob region/전체 경계 안정성 증명이 아니다. C/MSER/union 확대는 비채택한다.
- 공통 A/B의 평가 전 정책 민감도 모두에서 B의 개발 개선 방향은 유지됐다. cap55 geometry46→54와 FP 부담 감소, 기존 Android JNI 근거로 **B 교체 경로 조건부 개발 선택 / Task4 착수 가능**을 판단한다. B raw metal37 회수23/37(A30/37)와 미동결 주석은 여전히 위험이며 현장 우승자가 아니다. Task4는 이번에 시작하지 않았다.

### Android 근거·최소 계약·검증 범위

기존 source/bridge/4ABI binary·최종 호출 로그를 hash/bytes로 확인해 재사용했다. source-build-hashes의 derived metadata5건은 마지막 ABI크기/ELF 파일과 달랐다. 새 current hash index로 과거 metadata와 구분했고 source/실제 binary 일치는 직접 확인했다. 파일명/외관만으로 동일하다고 가정하지 않았다. 새 설치·native 재빌드·앱 build/기기 suite는 실행하지 않았다.

gray 경로는 color LUT 초기화를 호출하지 않지만 double LUT2개의64MiB BSS mapping은 남는다. virtual delta111580KiB/RSS delta128KiB/실사진10행 뒤 RSS147292KiB는 과거 snapshot이며 resident/peak/할당량을 혼동하지 않는다. gray-only 제거 가능성은 소스 조사만 했다. source 변경은 별도 spike/geometry·예외 회귀가 필요해 후속으로 남겼다. 현재 반복 호출의 crash/OOM/소유권 결함은 확인되지 않았고 숫자 메모리 허용값도 미확정이다.

최소 adapter는 I의 canonical geometry와 공통 measured-support 분리, support→geometry→source ID 순위/anchor 고정, M/DetectionGeometry의 quality 없는 순수 toMarker overload와 기존 overload 위임이다. 기존 M/CandidateMerge/R0 quality는 보존한다. I/EdgeDrawingCandidates·CandidateExtractor·DetectionConfig·CompositePipeDetector, M/DetectionGeometry와 geometry/candidate/composite/analysis 회귀, 시험 native 패키징 검사 파일을 후속 목록으로 확정했다. [실행 계획의 계약·회귀·철회 조건](superpowers/plans/2026-10-13-auto-counting-improvement.md#원본-검토와-개발-경로-선택--2026-10-14-original-review-1)을 따른다.

Task7은 S22+ cold/repeat/실제 peak/취소·late result·재시도/흐름·5초, Task8은 독립 세션/묶음·검토된 주석·전체 검수다. 배포 고지는 배포 전에 완성한다. 이를 모두 Task4 착수 전 조건으로 만들지 않는다. 최초 Task7 제품 연결은 현장 인수와 당시 요청 범위 확인 후 별도다.

이번 새 검증은 외부 evaluator의 최대 일대일/이웃/내외곽 fixture, 고정 geometry10행 재평가, Float·hash·기존 수치 대조, R0 대표 재구성, 대응 민감도와 원본 경계 검토다. 과거 JVM57/관련 Android46/build·Spotless·Lint는 검증된 이전 로그이며 이번 통과 숫자로 보고하지 않는다. 코드 변경이 없으므로 graphify update/앱 전체 검증을 반복하지 않는다. 최종 diff·로컬 링크·코드 hash/branch·staged 검사와 fresh review는 ledger에 기록한다. 기존 trace 예산10행/entry25000/point0을 보존하고 관측 JSON에 Mat/Bitmap을 보관하지 않았다. 새 외부 증빙은 PNG450개/전체50MiB 예산 아래인지 검사한다. 원본·정답·좌표·overlay·개인 경로는 저장소 밖에 두며 커밋·push·PR·업로드는 하지 않았다.

마지막 검토에서 새 외부 FP 관계 script의 y 거리도 너비로 환산하는 오류를 발견했다. 실제 gap 식에 가로/세로10px fixture를 적용해 RED(세로13.333px)를 확인하고 각 축의 referenceWidth/Height를 쓰도록 최소 수정했다. 같은 회귀 GREEN 및 FP 관계 재분류를 확인했으며 위43/33/27/13/10 분류 수는 바뀌지 않았다. R0/주석/최대 대응 metric은 바꾸지 않았다. 이 수정은 외부 script에만 있다.

### 사용자 중심·중복 번호 정정과 v6 — 2026-10-14

새 사용자 정정: cap151 전체 중심이 외곽 중앙보다 아래로 치우쳤고, metal37의27/28·30/31·35/36은 같은 단면에 중복 표시됐으며 전경 내부3개 단면에 중심 번호가 없었다. 이전 전체301 중심 승인/국소 질문 대기 상태는 이 범위에서 대체한다. v5와 당시 결과를 보존하고 새 **original-center-correction-v6**를 외부에 작성했다. 확정 개수·총301·별도 묶음 S1/S2의 원인 주석은 유지한다.

cap151의151개를 원본 외곽 기준으로 개별 재검토했다. 초기 황색 connected contour 보조는 접한 이웃 cap을 하나로 연결해 첫 항목에서 실패했으므로 그 결과를 사용하지 않았다. 고정된 원본 방사형 색 경계의 ellipse 제안을 주석 보조로 만든 뒤7개 확대 atlas/151crop을 시각 검토했다. 캡 없는11은 금속 외곽을 수동으로 읽었다. 정답 개수·검출 출력으로 값을 맞추거나 같은 이동량을 일괄 적용하지 않았다. 자동 경계 출력만의 승인/정밀 측정이라고 주장하지 않으며 새 좌표는 AI DRAFT다.

metal37의3개 중복 단면을 원본 눈금으로 다시 읽고 다른 표시 번호28/31/36을 누락된 실제 단면에 재연결했다. 새 번호 배치는 assistant 선택임을 기록했다. 그 단면을 원래 번호사진의 사용자가 그렇게 표기했다고 주장하지 않는다. 새 첨부본과 기존 원본은 hash가 다르며 affine 등록1129 inlier/median 약.72px로 대응을 확인했다. source/reference hash와 치수·변환·잔차를 외부에 보존했다. 새151cap+6metal 좌표는 재검토 초안, 다른144개 중심은 변경하지 않았다. metal6 b는 새 외곽 수동 제안이며 b/u/t·전체 동결은 아직 미완료다.

새 검증: 기존 설치된 APK의 실제 Android `validateEvaluationManifest`와 별도 canonical helper로5sample/301 및10sample/602를 검증했다. 둘 다 frozen=false이고 DRAFT의 frozen=true는 거부됐다. 기존 앱/시험 APK를 새로 빌드·설치하거나 검출하지 않았다. annotation 파일 byte hash와 실제 Android expected.toString() hash를 분리했다.

| sample/scope | v6 R0 final M/FN/FP/correction | 후보 fit→geometry→quality M |
|---|---|---|
| cap151 full |COUNT_LIMIT, 내부406|151→151→151|
| cap151 crop |151/0/124/124|151→151→151|
| metal37 full |17/20/37/57|31→19→17|
| metal37 crop |17/20/35/55|31→19→17|

다른3사진 수치는 그대로다. R0 입력/설정/geometry를 바꾸지 않았으므로 이는 **주석 정정에 따른 평가 변화**다. 새 final 대응/미대응 ID와 단계 원인을 version별로 보존했다. v5 FP126 원인 분류와 FN65/144 원인은 v6에 그대로 적용할 수 없다. 실제 대응 detection 집합이 바뀌므로 v6 FP124의 물리적 범주를 다시 확정하기 전 UNRESOLVED로 둔다.

같은 desktop 공통 A/B의 full/crop correction: cap151 A396/197·B237/166(모두 COUNT_LIMIT 전 진단), metal37 A207/171·B32/32. R0 최종 결과와 혼합하지 않는다. native generation/공통 gate/support/대표 선택은 고정했고 expected/actual normalized 좌표의 Float semantics로 계산했다. 고정 정책·target별 u perturbation 민감도도 같은 새 정답으로 분리 재평가했다. 아직 continuous/joint uncertainty 인수 증거는 아니다.

이전 E-O1 고정 버전을 재평가하자 cap151의M150→148 손실은 사라졌다(full/crop M151). cap151#59/#144를 물리적 누락/오통합 반례로 사용한 판단을 대체한다. v6 B crop correction166→26은 제한된 개발 실험 결과이며 현장 합격이 아니다. cap55의M54→53 손실은 그대로 남아 이 정책을 정식 채택하지 않는다. C0의 center-only 고유25도 그대로이며 ellipse 경계·안정성이 없다는 제한을 유지한다. 신규 검출 변형·사진별 튜닝·정식 통합은 없다.

**조건부 B/Task4 착수 가능** 판단은 유지한다. 새 좌표 사용자 승인·b/u/scope/t 동결과 Task7/8 현장/대표 기기 인수는 구분한다. 이번 변경은 외부 정답/검토 이미지/재평가 결과와 계획·설계·검증 문서·ledger다. 앱 소스·DI·런타임 의존성·저장·커밋·원격 작업은 변경하지 않았다. 최종 무결성·docs diff·code hash 결과는 ledger에 별도 기록한다.

주석 요인 분리: v5 허용치 값을 그대로 두고 중심/ID만 수정한 metal37 R0는 full16/21/38/59·crop16/21/36/57이다. 새 단면의 원본 b 제안과 같은 P0를 반영한 위 v6 표는17/20/37/57·17/20/35/55다. 중심 수정만으로 회수2개가 늘었다고 해석하지 않는다. 공통 B는 두 규약 모두19/18/14/32, 공통 A는 중심만17/20/189/209(full)·17/20/153/173(crop)에서 새 b 반영18/19/188/207·18/19/152/171로 바뀐다. 각각의 변화는 평가 주석/허용치 제안 영향이며 검출 변경이 아니다.

최종 fresh review는 Critical0/Important2/Minor1이었다. 실행 계획의 마지막 v5 JVM/native/graphify 문단이 v6 절에 섞인 문제를 과거 기록으로 명시해 분리했다. 재배정한 target의 crop/사용자 상태/지름 근거가 이전 단면을 가리키는 문제는 실제 새 중심이 crop 밖이라는 검사 RED로 재현하고, 현재301개 crop·현재 상태·source 링크를 재작성해 GREEN으로 확인했다. 이전 지름/상태는 historicalV5Review로 보존했다. 변형/metric 버전 라벨은 재현성에 영향을 줘 함께 수정했다. 숫자 정답이나 검출 결과는 이 검토 수정으로 바뀌지 않았다.

최종 새 검증:301개/37개 ID 유일성·새157개 AI초안/144개 중심 불변, 원본5개/v5주석/canonical hash, 현재301crop의 실제 파일/중심 포함, schema2 Android 검증 로그,10행 같은예측/공통비교와중심-only요인분리,앱소스110hash·branch/HEAD·staged 보존,로컬링크와diff --check 통과. 외부v6증빙 약41.85MB로50MiB예산 이하다. 정밀 b/u·사용자 새좌표승인·FP전체물리원인·연속불확실성·Android후처리동등성·peak·독립인수는 완료하지 않았다.

### Task 4 EdgeDrawing Android 평가 경로 — 2026-10-14

최신 사용자 요청의 Task4만 직접 순차 실행했다. 실제 `feat/auto-counting`/`89bfa63df4d3866200c71d8d683fff23fbb9a8a8`와 기존 변경·미추적 파일을 보존했다. 새 외부 입력/결과는 이번 채팅의 별도 `task4-v1`에 보존했다. 이전 v6/native 증빙381개 hash와 임시·보존 source의 동일성을 확인했고 오래된 source-build index의 derived metadata만으로 동일성을 확정하지 않았다. annotation file bytes와 Android expected canonical hash를 분리했다. 기존 metric/runner/진단·R0예측은 source/input/code hash가 같아 재사용했다.

구현은 `EdgeDrawingCandidates.kt`(canonical/support/simple anchor/JNI 직렬 호출), `EdgeDrawingEvaluationDetector.kt`(PhotoStore 평가 경로·실패/좌표), `EdgeDrawingInstrumentedTest.kt`, `EdgeDrawingEvaluationInstrumentedTest.kt`, 시험용 `cpp/ed` source/bridge/라이선스, `app/build.gradle.kts`의 androidTest generated jniLibs다. main 변경은 `DetectionGeometry.kt`의 순수 `EllipseGeometry`와 toMarker 위임 및 해당 JVM 회귀만이다. R0 EllipseCandidate quality·CandidateMerge·앱 검출기/DI/runtime·PhotoStore·저장/Navigation 형식은 이번 작업에서 변경하지 않았다.

완료 계약은 구현 전에 ledger의 task-4-contract.md에 고정했다. 버전 `edge-drawing-common-android-v1`, 기존 contrib4.12 source와 org.opencv4.12 AAR 한배포본, NDK27.3.13750724/r27d·API21·4ABI다. raw circle radius/ellipse반축·degree→radian·axis swap+π/2·modπ, ROI pixel geometry와 measured support만 분리한다. support median3/Canny40/90/dilate5/72점/floor(p+.5), 밖표본unsupported/분모72;gate b≥7/a≤80/축비≤2.5,quality≥.65. support내림차순→canonical geometry→sourceID,같은rim의center.1min-b/축상대.1/각5°(near-circle≤1.1무시)로고정anchor와출처만합친다. NFA→support 또는 fitError=0 없음. E-O1/새 품질/전이적통합 없음.

JNI는 복사 byte[]/double[]만 전달하고 Mat/Ptr는 호출지역 RAII로 해제한다. 전역native mutex와호출전후취소, late copy폐기를 확인했다. native가중간에즉시중단됐다는검증은아니다. 입력오류/native예외/할당실패cause·진단status를구분하고정상0/COUNT_LIMIT/포화를실패로보존한다. 할당실패는주입된OutOfMemoryError회귀이며실제OOM통과주장이아니다. 신규실행의정상/invalid/할당후native예외20회및재시도에geometry불변;과거spike100회와이번20회를구분한다.

새 검증: 누락 API compilation RED(geometry/B processing/PhotoStore detector/소유권hook) 후 최소 구현 GREEN. 최종 `spotlessCheck assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 통과, **JVM59개(실패0)**. 연결 Gradle에서명시적43개와추가9개(겹침있음)를실행했다. Gradle종료후앱제거로파일회수가실패해같은APK를직접설치/실행했으며최종 **관련Android49개(실패0)** 를따로확인했다. 이숫자를43+9로더하지않는다. 클래스는 EdgeDrawingInstrumentedTest,EdgeDrawingEvaluationInstrumentedTest,AnalysisPhotoInstrumentedTest,CompositeDetectorInstrumentedTest,EvaluationProtocolInstrumentedTest,DetectionDiagnosticsInstrumentedTest다. Hough/무관한전체검출은실행하지않았다.

합성9종 실제B재현: fragmented raw/gate/support/final1;small-blurred2/1/1/1;flat-ellipse2/0/0/0;blank/body-reflection0. thin/widecap내외곽2와접촉4는기존공통후처리그대로이며정확한물체수합격아니다. raw6 invalid/swap/angle,near-circle/이웃/연쇄/입력순열,밖support37/72,tracebudget0/25000의geometry·총량·출처일치,포화실패를검증했다. 기존EXIF1–8landmark/픽셀예산/bitmap예외해제/schema1·metric-v1/primary비교실패·sample실패·취소보존회귀도이번suite에포함했다.

별도synthetic PNG와JPEG95입력에서PhotoStore의JPEG95변환을유지했다. source/import/grayhash는달라지고각경로full2/crop1을재현했다. 2421×1203저장파일의decode1211×602,실제scaleX=1211/2421,scaleY=602/1203로큰rim과작은rim을유지하고crop전전체decode기준의gate를보존했다. pure geometry Float/비등방scale/ROI/반경은독립literal fixture및matrix eigensolver와검증했다. 실제5사진은decode축소없음.

10행의 동일 gray hash·고정 native geometry에서 gate전/후 목록→공통support→대표/출처/order→좌표/radius/Float를 비교했다. 고정geometry/liveJNI/실제PhotoStore경로 모두 desktop공통후처리와일치했다. 최대geometry차이 **8.881784197001252e-16**(사전1e-9이내),support정확일치,최종Float marker차이 **0**(사전1e-7이내). v6P0의matching tPx를이동등성epsilon으로확대하지않았다. 다른ABI는build-only,수치동등성실행은API36 arm64 emulator다.

아래B표는 **기존 Android 생성 geometry+desktop공통후처리와 새AndroidB의동일조건 결과**다. 순서는M/FN/FP/correction. full/crop을합치지않고cap151의COUNT_LIMIT 전 진단을정상final로보고하지않는다.

| sample | full 공통desktopB = AndroidB | crop 공통desktopB = AndroidB | 실제상태 |
|---|---|---|---|
| cap151 |151/0/237/237 (388출력)|151/0/166/166 (317출력)|두primary FAILURE/COUNT_LIMIT,traceOffSKIP|
| cap55 |54/1/39/40|54/1/41/42|두SUCCESS|
| metal40 |37/3/38/41|37/3/33/36|두SUCCESS|
| metal18 |18/0/7/7|18/0/8/8|두SUCCESS|
| metal37 |19/18/14/32|19/18/14/32|두SUCCESS|

**R0최종 결과는 다른quality/merge조건**이며 위공통B와같은조건의우열표로해석하지않는다. 기존예측을같은v6P0로재평가한별도기록은아래와같다. 새R0검출을돌린것이아니다.

| sample | R0 full final | R0 crop final |
|---|---|---|
| cap151 |COUNT_LIMIT (내부406),final없음|151/0/124/124|
| cap55 |45/10/28/38|45/10/20/30|
| metal40 |35/5/69/74|35/5/49/54|
| metal18 |18/0/19/19|18/0/14/14|
| metal37 |17/20/37/57|17/20/35/55|

입력/config/code/annotation/output hash와재현명령을외부provenance에보존했다. 현재 v6 b/P0 결과와center-only fixed-v5-t(특히metal37R0 M16 대 M17)는주석요인이며새B출력변화와분리한다. 주석총301(151/55/40/18/37),151cap+6metal AI DRAFT/144중심불변,b/u/t·frozen=false,다섯사진development/tuning을유지했다. 사용자오류지적을새좌표전체승인으로확대하지않았고excluded는출력을제거하지않는다.

실제main/testAPK의native entries·DEX를검사했다. main에는새bridge/B검출기/OpenCV클래스·native가없다. test에는4ABI당bridge1개와기존libopencv_java4/libc++만있고DT_NEEDED도그한배포본에연결된다. 새다른OpenCV native중복없음. 원본source/hash·ApacheLICENSE보존,배포용전체OpenCV/third-party/libc++고지묶음은배포전조건으로남긴다. 기존colorLUT BSS는수정하지않았다.

원인관측은v6자료로새로했다. B cap151의한실제cap에서중심근처inner(약11px)와outer(약26px)경계가각각유지되는원본대조를보존했다. 이는same-rim정책이내외곽을합치지않는계약이며Android불일치가아니다. v6R0 crop FP124중2개를실제원본·새대응ID와대조해cap내외곽중복사례로기록했고나머지122개는UNRESOLVED로남긴다. v5FP126분류전체를재사용하지않았다. cap151#59/#144의과거E-O1대응손실해석은대체된상태를유지한다. cap55M54→53손실은Task6반례,C0#25는center-only가능성으로유지한다.

**Task4의구현·Android동등성·실패보존·시험격리계약은완료**다. Task5의품질,Task6의정식내외곽통합,Task7의S22+전체peak/5초/취소·흐름,Task8의독립자료·정답동결·전체검수인수는이번에실행하지않았다. 이정도중복/누락으로현장합격·제품제공을선언하지않는다. graphify/diff·freshreview·보존최종검사는ledger의이어지는기록을따른다.


#### Task4 소유권 회귀 수정과 최종 검증 — 2026-10-15

2026-10-14 실행의 마지막 fresh review 중 실제 source 소유권 결함을 확인해 완료 판단을 보류하고 이어서 수정했다. 원래 EdgeDrawing의 Join scratch 연속 할당/후속 vector·matrix 작업 예외는 수동 cleanup을 건너뛰었다. 생성자 부분 할당·ellipse 버퍼·부분 matrix row·NFA 교체에도 같은 소유권 문제가 있었다. bridge의 Mat/Ptr RAII만으로 내부 raw buffer가 해제된다는 앞선 판단은 불충분했다.

원본4.12 source를 별도 보존하고 알고리즘·LUT/BSS 변경 없이 표준 `std::unique_ptr` 소유권(`ownership1`)만 보완했다. raw alias는 기존 계산에 사용하고 실제 owner는 생성자/함수 범위에 둔다. NFA 교체는 이전 객체를 해제하고, 부분 matrix는 초기 null row와 owner deleter로 이미 확보한 행까지 해제한다. 시험 JNI audit는 할당 실패를 결정적으로 주입해 논리적 live delta를 측정한다. 이는 새 품질/gray-only/BSS/정확도 튜닝이 아니다.

실제 RED는 정상 NFA 교체의 미해제2건 및 Join의7번째 할당 실패 후 미해제2건이었다. 같은 회귀 GREEN에서 고정ellipse fixture가 도달한 **1,641개 할당 지점 전체**에 실패를 주입하고 정상/각실패/재시도의잔존할당0을확인했다. OpenCV내부allocator나실제OS OOM/전체peak를모두입증한것은아니다. 초기 generic 할당후예외20회만으로 이내부소유권을검증했다는해석을대체한다.

최종 동일source의4ABI시험build·Spotless·assembleDebug/AndroidTest·Lint·JVM59 통과(기존JVM결과는이번요청내실행,최종native-only수정후up-to-date),명시적 관련Android **50개 통과**. 이전49는수정전기록으로보존한다. 새bridge로9합성/10사진을재실행하고고정geometry/liveJNI/PhotoStore와desktop공통후처리동등성을재확인했다. geometry오차8.881784197001252e-16,Float0,support/대표/출처/order/10행지표와COUNT_LIMIT/FAILURE/SKIP는그대로다. 입력/주석/허용치수정없음.

최종 APK/native/code/config/output hash는소유권수정본으로별도갱신하고원본hash와패치diff를분리했다. 일반APK의Bbridge/OpenCV격리와단일AAR연결을재검사한다. 마지막graphify/diff·보존검사·전체새증빙hash검사는ledger/provenance에기록한다.

Fresh reviewer는코드·기존source보존·실제APK/hash·10행공통후처리·49/59결과를독립대조하고위소유권결함을지적했다. 사용량제한으로완성된최종리뷰보고서를반환하지못했으므로clean fresh-review판정은없다. 확인된Important 소유권문제를RED→수정→같은회귀/관련suiteGREEN으로해결했고수정diff는작성자가별도검토했다. 이리뷰제한과Task4기술계약완료를구분한다. Task5–8/일반앱연결/커밋/원격작업은진행하지않았다.

### Task4 최종 fresh review와 Task5 품질 관측 — 2026-10-15

이 절이 Task4의 이전 **최종 fresh report 미완료** 상태를 대체한다. 새 독립 읽기 전용 검토자가 최종 ownership1 source/diff/증빙을 확인해 Critical0/Important0/Minor0의 완성 보고서를 반환했다. 생성자/NFA교체/scratch/ellipse/부분matrix owner와 alias, JNI예외/직렬/취소/late result/audit비활성, 순수geometry/toMarker, 실제APK격리/단일AAR, 문서/ledger의 주장을 검토했다. 새 확정 결함이 없어 Task4 코드 수정은 없었다. 원본162 artifact bytes/SHA, 최종source snapshot/current code, APK/native SHA 일치를 재확인했다. 기존 JVM59/최종Android50/10gray·9합성 parity와 RED/GREEN은 **과거 실행 증빙 재사용**이며 이번 실행 수가 아니다. 리뷰 보고서와 실행자료는 새 외부 `task5-v1`에 보존, 원래 `task4-v1`과 v6는 읽기 전용이다.

Task5 완료 계약을 구현 전 ledger에 고정했다. 기존 B raw6/geometry/gate(7/80/2.5)/median3/Canny40/90/dilate5/72점/.65와 support→canonical→sourceID, same-rim fixed anchor, decode/ROI/scale/v6P0를 유지했다. R0/C/MSER/union/Hough/refit/내외곽통합은 변경하지 않았다. 새파일은 androidTest `CandidateQuality.kt`, `CandidateQualityInstrumentedTest.kt`, `BQualityEvaluationInstrumentedTest.kt`; 기존 `EdgeDrawingCandidates.kt`에 선택적 관측과 개발 ablation만 연결했다. 기본 null은 기존 평가 경로, OBSERVE는 거절0이다. main/DI/runtime/PhotoStore/schema/native/build설정은 불변이다.

품질 가설은 **총support가같아도 연속 지지공백은 가짜경계와 다를수있다**였다. 원본 metal40 full#19는 실제타원과 어긋난 원형/내부몸체 조합, #67은 내부반사·질감쪽 작은경계로 DRAFT 관측했다. metal18#30은 내부검은disk만으로 실물여부를 단정할수없어 UNRESOLVED다. cap151#25와 cap55#50은 정상 cap외곽, metal18#4는 부분가림의 실제 capless rim이다. 정상내외곽/same-rim/별도묶음 rim을 가짜로 분류하지 않았고 FP 전체 원인분류를 수행한것은 아니다. 원본SHA·후보ID·좌표·crop과무표시/표시카드를외부에보존했다.

측정값은72점의 최대연속미지지수와그값×5°(**이심각**, 물리호길이아님),영상밖 invalid·영상안 unsupported,서로다른반올림pixel수,인접연속좌표최대간격px다. support분모는72고정,영상밖을clamp하거나분모를줄이지않는다. 최대간격>5px 또는중복표본은undersampled로관측한다. invalid/undersampled를자동거절하지않고Mat/Bitmap을증빙에보관하지않는다. fitError/contrast/gradient/법선/NFA품질은미측정이다. 같은영상의edge Mat은기존한번계산/해제하며관측은추가Mat을만들지않는다.

아래는 **같은 고정 B 생성 후보**를 실제 Android 후처리한 값이다. 각칸 M/FN/FP/correction. OBSERVE와gap120은전행기준선과동일하다. gap90/120은실행전에전체공통설정으로고정했고사진별분기는없다. 일반 앱 제공 결과나새native/PhotoStore10회실행이아니다. COUNT_LIMIT의값은상한적용전진단이며정상final로읽지않는다.

| 사진/영역 | 기준선·OBSERVE·gap120 | gap90 실험 | 개수 기준→gap90 |
|---|---|---|---|
| cap151 full |151/0/237/237|151/0/236/236|388→387, COUNT_LIMIT|
| cap151 crop |151/0/166/166|151/0/165/165|317→316, COUNT_LIMIT|
| cap55 full |54/1/39/40|54/1/38/39|93→92|
| cap55 crop |54/1/41/42|54/1/40/41|95→94|
| metal40 full |37/3/38/41|37/3/36/39|75→73|
| metal40 crop |37/3/33/36|37/3/31/34|70→68|
| metal18 full |18/0/7/7|18/0/7/7|25→25|
| metal18 crop |18/0/8/8|18/0/8/8|26→26|
| metal37 full |19/18/14/32|19/18/14/32|33→33|
| metal37 crop |19/18/14/32|19/18/14/32|33→33|

관측만 적용한 결과는 Task4 fixed geometry/전체 observation/대표/source/order와 exact, 저장된 Float marker와 exact였다. 고정 raw를 degree로 되돌려 canonical화한 값과 liveJNI의 차이는 Task4의 사전 고정 1e-9 이내(최대8.88e-16)이며, 새 quality 비교끼리는 같은 raw라 exact 비교했다. v6 b/P0와 center-only fixed-v5t는 별도 해석을 유지하며 t/중심/scope 변경은 없다. R0 최종은 다른 quality/merge이므로 위 표와 섞지 않는다.

품질 거절의 파생 효과도 확인했다. gap90 거절은 cap151 full25/crop24, cap55 full50/crop51, metal40 full/crop19와70이다. 기존 anchor 삭제만 있었고 새 anchor0, 대응 target 손실0·획득0·대응ID 변경0이다. 전체 target의 유지/미대응/근접 후보 stage, 품질 거절ID, 대표 출처, 대응/미대응ID는 외부 JSON에 보존했다. **gap90은 정상 cap 외곽을 떨어뜨려도 내부의 기존 marker가 target을 유지하므로 수치 FP가 준다. Task6의 내외곽을 품질이 낮은 가짜로 제거하는 정책이 되므로 보류한다.** metal40#67/metal18#30도 남아 있고 관측만으로 물리적 정오를 확정할 수 없다. gap120은 개선0으로 보류한다. 관측 수단만 유지하며 거절 policy의 기본 연결은 없다. 후보 부재/metal37 FN18을 품질 필터로 해결했다고 보고하지 않는다.

trace0/3/full 예산에서 geometry/집계/anchor/source 보존을 모든 variant10행에서 assert했다. trace-off의 후처리 전체(edge 준비+support+quality+merge, native/decode/JSON 출력 제외)를 각3회 측정하고 중앙값을 보존했다. OBSERVE는 기준 대비 약1.23~18.41ms 증가, 행별 중앙값 차이의 중앙값은3.10ms였다. 실행 순서가 고정된 짧은 에뮬레이터 측정이며 순수 특징 시간/peak/S22/5초 입증이 아니다. COUNT_LIMIT 행도 상한 전 process를 계측했으므로 과거 primary FAILURE/trace-off SKIP와 구분한다. 실제 primary FAILURE/COUNT_LIMIT2·trace-off SKIP2는 Task4 증빙으로 보존했고 이번 runner의 status 필드는 같은 marker 상한에서 계산한 **투영 상태**다.

새 실행은 CandidateQualityInstrumentedTest5, BQualityEvaluationInstrumentedTest1, EdgeDrawingInstrumentedTest10의 **16 Android PASS**(API36 arm64)다. 미구현 API compile RED→최소 구현GREEN, 원주0도 경계의 공백/짧은 호·분산 edge의 같은 support/invalid/inside unsupported/작은 중복pixel/성긴 회전ellipse/normal0/saturation/trace예산/호출자Mat 보존을 확인했다. 기존B10에는 native입력/할당실패/전체1,641 도달할당의 live0/재시도/직렬cancel/late discard/odd decode·crop·JPEG/anchor가 포함된다. 기존9합성의 gray/raw는 재생성하지 않고 thin rim/wide cap/touching/fragmented/split interior/blank/small blur/flat ellipse/body reflection의 관측 일치와 policy 결과를 실측했다. blank/flat/body-negative는 원래 gate후보0이라 새 quality의 FP 개선 증거가 아니다. 법선 미추가로 법선각도 회귀는 해당하지 않는다.

시험APK build·Spotless·assembleDebug·Lint 성공, JVM task는 UP-TO-DATE로 기존59 결과를 재사용했다(이번59개 새 실행 아님). main/test 실제 APK의 native entry SHA는 모두 Task4와 같고 main에 B/OpenCV/새quality class가 없으며 test에만 기존4ABI 단일OpenCV+ownership1bridge가 있다. 기타 기존source/Gradle 보존, Hough/무관한 전체 검출은 실행하지 않았다. 기존 schema1/metric-v1/JVM/PhotoStore/runner failure 회귀는 source/config/input이 같은 과거 증빙을 재사용했다.

Task5는 최소 관측과 같은 조건 실측·거절 policy 비채택 범위에서 완료다. Task6의 물체 단위 통합, Task7의 S22 전체peak/실제OOM/속도·흐름, Task8의 독립자료/검토된 truth동결/전체 검수는 미실행이다. v6는301(151/55/40/18/37), 새151cap+6metal AI DRAFT, 다른144중심 불변, b/u/t·전체annotation frozen=false, 다섯사진 development/tuning을 유지한다. S1/S2는 별도묶음, excluded는 원인주석뿐이다. 커밋/push/PR/merge/deploy/upload는 실행하지 않았다.

Task5 최종 독립 읽기 전용 검토는 Critical0/Important0/Minor1이다. Minor는 BQualityEvaluationInstrumentedTest의 primaryStatus/traceOffStatus 필드가 실제 detector 실행 상태처럼 읽힐 수 있다는 보고 명칭 문제다. 현재 외부 config와 이 절에서 투영 상태임을 명시했고 실제 후처리는 전행 실행했다. 필드 명칭 변경은 보류 사항으로 남긴다. 기본 B 동작·측정/예산·소유권 계약에 추가 확정 결함은 없었다.


### Task6 내외곽 통합 실험과 보류 — 2026-10-15

기존 B와 Task5 BASELINE/OBSERVE를 고정하고 Task6만 직접 실행했다. source/config/input/annotation/output SHA와 이전162/146/v6 360 artifact 일치를 확인했다. 과거 Task4 Android50/JVM59/최종fresh0/0/0과Task5 Android16/fresh0/0/1을 이번 새 실행 수로 올리지 않는다.

기존 simple same-rim 판정/대표/source는 재사용, 후보10000과별도로 same-rim/object 각각1,000,000pair 예산을 추가했다. object는 제3후보 검사도 예산에 포함한다. 초과는원인구분이있는 CANDIDATE_SATURATION, 부분성공/조용한후보절단없다. 새 BObjectMerge의 명시적 ALL/90만실험했고 기본null·Task5qualitynull을유지한다. band/aperture/exterior 관계만측정하며 새geometry/fit/품질거절/generator/hierarchy는없다. 기존 support/gate/좌표/원본/v6/t/scope불변이다.

각 칸 M/FN/FP/correction. 기준선=same-rim10행은Task5와exact다. cap151기준선은COUNT_LIMIT전진단이며정상final아니다. ALL/90은이번실제PhotoStore/native20행모두SUCCESS,cap151도실제marker를반환했다. 고정replay30행의projected상태와실제detector상태를파일/필드로분리했다.

|사진/영역|기준선=same-rim|ALL|90|개수 기준→ALL→90|
|---|---|---|---|---|
|cap-151-full|151/0/237/237|151/0/149/149|151/0/121/121|388→300→272|
|cap-151-central_rectangle|151/0/166/166|151/0/78/78|151/0/50/50|317→229→201|
|cap-55-full|54/1/39/40|54/1/33/34|54/1/23/24|93→87→77|
|cap-55-central_rectangle|54/1/41/42|54/1/34/35|54/1/24/25|95→88→78|
|metal-40-full|37/3/38/41|37/3/38/41|37/3/38/41|75→75→75|
|metal-40-central_rectangle|37/3/33/36|37/3/33/36|37/3/33/36|70→70→70|
|metal-18-full|18/0/7/7|18/0/7/7|18/0/7/7|25→25→25|
|metal-18-central_rectangle|18/0/8/8|18/0/8/8|18/0/8/8|26→26→26|
|metal-37-full|19/18/14/32|19/18/14/32|19/18/14/32|33→33→33|
|metal-37-central_rectangle|19/18/14/32|19/18/14/32|19/18/14/32|33→33→33|

기존 대표geometry 이동/refit0,sourceunion전행보존이다. 삭제된표시→남은anchor의중심/반경차이는별도존재한다. 대응ID변경 ALL/90:cap151full27/34,crop27/35,cap55full2/4,crop3/5,금속0. matched target손실/획득0. 전체target별유지·미대응·근접stage·대표교체는외부target-reasons/comparison/merged-pairs에보존했다. metal37FN18은그대로다.

원본138고유pair(정책·영역을구분한454관계)를8개무표시/표시atlas에서직접확인했다. 모두같은캡내외곽으로관찰했고이subset이웃오통합은관찰되지않았다. AI원본관찰DRAFT이며전체FP분류/사용자승인은아니다. Task5정상외곽4반례를가짜품질로분류하지않았다. E-O1cap55 target028 innerfull81/crop80→outerfull25/crop26의과거오차5.3387px>고정t5.25는물리누락이아닌대표대응손실이며새정책은inneranchor를보존한다. cap151#59/#144의과거v5손실은현재반례가아니다. 별도묶음과애매한금속/후퇴관계는UNRESOLVED이며출력에서지우지않는다.

**판정은ALL/90보류**다. parent후보와두aperture후보가함께존재하면aperture를보존하되parent도남아3개로,요구한정확히2개를충족하지않는다. parent-only제거/포함만통합으로개수를맞추지않는다. 밝은띠의한정된근거로모든후퇴/별도물체를구분했다는주장도하지않는다. 개발보정량감소/상한해소와정식정책채택/현장정확도를구분한다. 비채택실험·관련코드/연결·회귀완료이며물체통합채택완료아니다.

새실행Android19: BObjectMergeInstrumentedTest6,BDetectorMergeContractInstrumentedTest2,BObjectEvaluationInstrumentedTest1,EdgeDrawingInstrumentedTest10. 6개단계/17개중간실행은최종19와겹쳐더하지않는다. r8/r24,접한annuli,capless,순열/pi/anchorchain,모호/가림/ROI경계,trace0/3/full,callerMat,source와예산소진을확인했다. parent영상에aperture후보2개만있으면2개보존,parent후보도있으면3개라는제한을분리한다. 실제detector0/300/301/COUNT_LIMIT/포화/입력실패/취소late-discard/재시도2시험은고정rawgenerator주입으로native정확도실험과구분한다. 별도20행은실제PhotoStore/native실행이다. SKIP/schema1/metric-v1/R0/runner보존은동일hash의과거검증을재사용했다. Hough/전체무관한검출은실행하지않았다.

새native소유권audit는1,641도달할당실패/정상/재시도잔존0,실제APK4ABI nativeSHA는Task5와모두같다. main에B/merge/OpenCV없음,test의한기존AAR+ownership1bridge를확인했다. realOSOOM/전체peak/다른3ABI실행검증은아니다. 최종build/Spotless/Lint성공,JVMtask는UP-TO-DATE로이전59재사용. main/native/Gradle/DI/PhotoStore/schema변경없음.

trace-off후처리와그안의object처리를3회씩측정했다. 후처리는edge/support/same-rim/object포함,object는graybytecopy/정렬/pair/영상표본포함이며native/decode/PhotoStore/JSON제외다. 행별중앙값/원시는외부comparison/android-output에있다. 에뮬레이터API36arm64·고정실행순서이므로S22/전체5초/전체peak입증이아니다. trace25000에서일부상세spoke값은미보존되며총량/source는모두보존한다. 완전sourceunion으로누락positive직접링크를복원했고미관측값을작성하지않았다.

v6총301·157AI DRAFT/144중심불변,b/u/t전체DRAFT/frozen=false,S1/S2별도,excluded원인주석,다섯development사진을유지한다. center-onlyfixed-v5t/R0final은별도다. Task7은이미확정된S22+/Android16자원/흐름,Task8은독립세션·묶음/정답동결/전체검수이며미실행이다. Task6의다중aperture/후퇴관계는추가원본관계근거와별도최소가설이필요하다. 일반앱ValidationRequiredDetector/DETECTOR_VALIDATED=false,저장계약불변. 커밋·스테이징·원격·PR·배포·외부업로드없음. 최종graphify/diff/독립검토는아래후속확정기록을따른다.


Task6 최종독립읽기전용검토완료: **Critical0/Important0/Minor1**. Minor는외부analyze.py:33이재생성하는merged-pairs의PENDING표시와별도138개AI원본검토기록의상태연결문제다. 근거는보존돼보류하며caseID/검토참조동기화를후속으로남긴다. 확정Critical/Important가없어코드수정/반복검증없음. reviewer는atlas1의18pair를직접열었으며138전체독립시각재판정/전체FP/실기기·현장·truth승인/기존dirty전체검토는제외했다. scoped비채택실험검토완료를제품/정책채택승인으로확대하지않는다.

추가원본대조의경계: 금속source1/8은기존same-rim기하정책에흡수됐으나같은edge반복과얇은금속wall양쪽경계는nativepoint/segment대응이없어UNRESOLVED다. 같은파이프좁은rim관계라는원본관찰과물리same-edge확정을구분한다. 실제cap내외곽도보수적gate에서비병합될수있고정상품질가짜로분류하지않는다.

graphify AST갱신2841nodes/5207edges/146communities와git diff --check통과. label변경경고는보존하고추가LLM호출은하지않았다. 실제Task5source156중154해시불변/허용된2파일만변경,새4평가파일과문서3개로범위를확인했다. branch/HEAD/빈index와원본/v6/기존증빙보존을최종외부감사에남겼다. Task6는**보류정책의실험·평가연결·회귀·기록완료**,물체통합채택완료아니다.

## Native 표본 결함 수정과 격리 비교 — 2026-10-16

이번 명시적 구현 요청에 따라 과거 Task 순서/생성기 고정을 이번 실험의 영구 제약으로 사용하지 않았다. 실제 `feat/auto-counting` / `89bfa63`의 미커밋 구현을 그대로 재사용했다. 현재 평가 버전은 `edge-drawing-common-android-v2-even-sampling`이다. 일반 앱은 `ValidationRequiredDetector` / `DETECTOR_VALIDATED=false`이며 평가 구현의 수정과 현장 인수를 구분한다.

`ValidateCircles`의 홀수 N 읽기와 `ComputeEllipsePoints`의 2*floor(N/2) 쓰기 불일치를 Android arm64 실제 sampler/호출 분기에서 RED로 재현했다. 타원 분기에서 N을 짝수로 내리는 최소 수정으로 표본 위치를 보존하면서 미기록 읽기를 제외했다. `scripts/native_sampling_regression.py`는 현재 소스에서 회귀 C++를 생성한다. 홀/짝·증가/감소·circle 표본→ellipse fit 후 버퍼 재사용을 검사하며 RED exit2→GREEN exit0이다. 마지막 사례는 전체 자동 fallback 실행의 독립 증명이 아니다. 별도 사진 native sidecar는 3,388회 validation 시도에서 미기록25/stale773을 검출했고 수정 후0/0이었다. 미기록 값은 읽거나 int 변환하지 않았으며 수치 sentinel은 사용하지 않았다. ASan/liveDelta를 초기화 증거로 사용하지 않았다.

새 실제 Android PhotoStore/JNI full/crop10행은 기존 raw/canonical 지오메트리와 지표를 유지했다. 개발사진5장의 중복 범위이지 독립사진10장이 아니다. 아래 M/FN/FP/C는 수정 전후 동일하며 C=FN+FP다. cap151 두 행은 COUNT_LIMIT 이전 진단이고 정상 결과가 아니다.

|사진/범위|M|FN|FP|C|실제 상태|
|---|---:|---:|---:|---:|---|
|cap151 full|151|0|237|237|COUNT_LIMIT|
|cap151 crop|151|0|166|166|COUNT_LIMIT|
|cap55 full|54|1|39|40|SUCCESS|
|cap55 crop|54|1|41|42|SUCCESS|
|metal40 full|37|3|38|41|SUCCESS|
|metal40 crop|37|3|33|36|SUCCESS|
|metal18 full|18|0|7|7|SUCCESS|
|metal18 crop|18|0|8|8|SUCCESS|
|metal37 full|19|18|14|32|SUCCESS|
|metal37 crop|19|18|14|32|SUCCESS|

추가한 세 가설은 한 축씩 별도 계약으로 실행했다. H1은 고정 raw의 a<=80만 진단용으로 우회했다. metal37 full/crop에서 각각 U6/L0/A6/R0으로 C32를 유지했고, metal18 full은 A1로 C7→8이 되어 보류했다. 기존 a120의 중립 결과를 보존하면서 상한 우회 전체의 회수/추가 FP를 판별한 실험이다. H2는 기존 ANN90에서 안쪽 aperture 안의 제3중심 veto만 제외했다. cap151 full/crop에서 R1씩, C121→120/50→49였으며 다른 행과 U/L/A는 불변이었다. 새 3경계 합성에서 기대1에 실제2가 남아 실패한 상태로 보류했다. 최초 fixture는 same-rim에서 이미 합쳐져 수정했으며, 이후 기대1을 낮춰 합격시키지 않았다. H1/H2는 Android 고정 raw 재평가이고 상태는 projected이며, 일반 detector 실행 상태나 물리적 물체 정확도가 아니다.

H3는 기존 EllipseFit의 FPF→Bookstein만 바꾼 외부 native 복사본으로 metal37 full/crop과 metal40 full을 비교했다. Android native 실행과 Kotlin baseline10행의 support/대표/metric을 검산한 desktop replay이며 JNI 전체 경로는 아니다. metal37 U/L0, FP+2/+1, C32→34/33, metal40 C41 불변으로 3행에서 중단·불채택했다. 원천 점열 hash와 geometry를 함께 사용하고 이동한 candidate ID만으로 대응하지 않았다. 동일 hash가 반복되는 후보도 일대일로 집계한다. metal37의 나머지12FN은 고정t 안의 pre-NFA 중심도 없으므로 NFA 완화만으로 회수할 근거가 없다. 국소 fit과 DRAFT 중심 불확실성은 미분리이며 물리적 생성 실패12개로 확정하지 않는다.

최종 기본 경로는 FPF·a<=80·qualityPolicy=null·objectPolicy=null이며 native 표본 수정만 채택했다. 실험용 Kotlin4파일은 외부 보존 후 이번 추가분만 제거했다. 기존 truth/t/scope/frozen과 사용자 변경을 보존했고 커밋/원격 작업은 하지 않았다. 코드 정확성 개선, 이번 사진 정확도 불변, 제품 목표 미달을 구분한다.

새 실행은 관련 Android13(사진평가1/native 등10/detector 계약2), 최종 arm64 APK의 관련12 재확인, JVM59 재실행, Spotless/assembleDebug/assembleDebugAndroidTest/Lint 성공이다. 중첩된 실행 횟수를 별도 정확도 표본으로 더하지 않는다. native 소유권은 고정 fixture의 1,641회 도달 할당 실패/정상/재시도에서 liveDelta0이며 전체 allocator/OS-OOM/peak 증명이 아니다. 최종 universal APK 갱신은 에뮬레이터 저장공간 부족으로 실패했다. 이번 복제 실행 파일만 정리하고 같은 DEX/arm64 native를 담은 arm64 한정 로컬 APK를 서명·설치해 최종12를 실행했다. 4 ABI 패키징 검사와 arm64 실행을 구분한다.

독립 코드 검토는 이번 native3행/버전/회귀 생성기 범위에서 Critical0/Important0/Minor0이다. 실행 로그를 읽은 검토이며 별도 Android 재실행·truth/물리 검증·기존 dirty 전체 코드 감사가 아니다. 판독 준비 crop64/변화 overlay32는 자료 생성이며 독립 판독자 실행0/사용자 승인0이다. 사용자는 현재 자료와 에뮬레이터로 마무리하기를 선택했다. S22+ 전체 분석→표시5초·cold/warm·장기 peak/취소 중 native 정지·독립사진·사용자 보정/전체 검수 효용은 미검증이다.

새 증거는 이번 채팅 외부 `native-improvement-v1`의 REPORT/계약/input-hashes/native-chain/transitions/changed-cases/final-verification에 보존했다. 과거 증거는 덮어쓰지 않았다.


## Native 단계 연결 진단 — 2026-10-16

같은 `feat/auto-counting` / `89bfa63` dirty 구현에서 진단을 완료했다. detector 버전 `edge-drawing-common-android-v2-even-sampling`, FPF·a<=80·기존 support/same-rim·quality/object=null을 유지한다. 새 `scripts/native_stage_trace.py`는 현재 native 소스의 외부 복사본에 segment/line/arc/실제 fit 점/NFA/Join/후보 등록 로그만 넣는 재현 도구다. 정답·ROI·target 입력을 받지 않고 일반 앱/JNI 소스에는 계측을 넣지 않았다. 진단 버전과 검출 기준선 버전을 구분한다.

문제4개(metal37 018/028/034, metal40 020)와 크기가 비슷한 matched 대조군2개(metal37 027 b39/033 b23)를 원본부터 확인했다. 원본 및 crop 확대는 관찰용이고 검출 입력은 기존 full/crop gray다. 기존 출력을 본 AI의 관찰이며 독립 맹검·사용자 승인·주석 교정이 아니다.

|사례|실제 연결과 관측|판정 한계|
|---|---|---|
|metal37 018|segment79 폐곡선280점→직접 ellipse→preNFA9→n/k245/235 valid→raw29/b29. 별도 segment33의126점+segment10의105점→JoinArcs3 fit231점→preNFA382→272/204 valid→raw22/b22|두 fit은 원본 rim을 따르지만 DRAFT 중심 거리는29.14/26.85px, t10 밖. 물리적 미생성으로 단정하지 않음. 주석 중심 불확실성/경계 선택을 미분리|
|metal37 028|아래 segment4/5→arc5/6 80/98점(coverage .129/.165), 위 segment6/10→arc7/10 118/136점(.342/.440). Join2는 다른 segment, Join3 위·아래 조합은 실제 `diff > minR`로 거절. 네 arc의 실제 registration added0|위 두 arc끼리는 같은 위쪽의 서로 다른 rim 경계로 관찰됨. overlap .645 거절을 결함으로 확정하지 않음. 최초 확인 병목은 원호 연결·후보 등록이며 원반경 proxy가 다른 짧은 타원호를 결합하지 못함. 완화의 안전성/물체 동일성은 미입증|
|metal37 034|segment19→JoinArcs2 ellipse165점→preNFA184→125/122 valid→raw48/b48, 중심거리5.6266px>t5.5. segment145의104점 외곽 arc는 거리5.24지만 coverage .466<.5로 미등록|중심 허용치를 약.127px 벗어남. fit 선택과 DRAFT 주석 불확실성 사례이며 t/정답을 바꾸지 않음|
|metal40 020|segment89→arc112/preNFA300의127점 원 fit(error .793)→332/122 valid→raw15/b15. 같은 segment의다른152점 arc도 남음|부분 rim의 원 fit이 반대편 rim을 가로지르는 AI 관찰. 국소 오차/NFA 통과는 물체 전체 경계 일치 보장이 아님. fit/모델 선택 제한이며 다른 rim 합치기 반례로 유지|
|matched 027/033|027: segment2의231점 직접 fit→preNFA71→211/177 valid→raw31/b31(거리3.30<t9.75). 033: segment20 폐곡선190점→preNFA3→139/130 valid→raw44/b44(1.58<t5.75)|성공 대조이며 단면 전체 일반화/독립 승인 아님|

처음 full2행 plain/off/on 각3회는 raw 완전 동일, 최종 생성기의 metal37 full/crop·metal40 full3행 on/off도 완전 동일했다. 실제 새 PhotoStore/JNI3행에서 canonical(1e-9), support/대표/source/Float marker/metric/상태가 기존과 같았다. metal37 full/crop M19/FN18/FP14/C32, metal40 full M37/FN3/FP38/C41, 모두SUCCESS이며 각 U/L/A/R=0이다. 검출 변경이 없어 나머지7행 전체 평가를 반복하지 않고 직전10행을 과거 증거로 재사용한다. 개발5장 full/crop은 독립10장이 아니다.

검출 수정/새 baseline/임계값 탐색/RED-GREEN은 없음. 손실 분기는 확인했지만 연결 규칙 완화의 correctness를 입증하지 못했으며, metal40의 다른 경계를 가로지르는 부분 원 fit이 반례다. 단순 coverage·radius·overlap 완화나 FPF 교체를 강요하지 않았다. 다음 최소 판별은 정답을 쓰지 않는 알려진 단일 타원의 상·하 분절 합성에서 동일 rim arc의 원반경 proxy 거절을 재현하고, 인접한 두 rim 반례와 함께 조건을 분리하는 것이다. 이 synthetic oracle와 실제 지지점의 물리적 경계 구분이 결합 정책 변경 판단을 바꿀 증거다.

로그는 실행당200k events/2M points/64MiB로 제한하고 잘림 flag를 기록한다. 최종3행 잘림0, 약4.4–7.9MiB다. `JoinCircles`에서 실제 재fit/병합된 결과는 기존 구현이 support를 NULL/0으로 등록하므로 단일 preNFA 원천에 억지로 연결하지 않는다(metal40 raw0/73/75/76). 이번 선택 사례의 직접 연결은 실제 ordered points+geometry로 확인했다. hash는 보조값이며 동등hash 반복을 물체ID나 일대일 보증으로 쓰지 않는다. fit 실패 시 출력 geometry/오차를 읽지 않으며 안전한 even sampling을 유지했다.

새 검증: 빌드/Spotless/Lint, JVM59 실제 실행 성공, EdgeDrawingInstrumentedTest10+BDetectorMergeContractInstrumentedTest2 성공 및 선택 JNI 시험1 성공. 임시3행 runner의 첫 실행은 기존10행 개수 assertion에 실패했고, 3행 계약·각 단계SUCCESS assertion을 추가해 해당시험만 재실행했다. 이는 검출 실패나 기대 정확도 완화가 아니다. 포맷 위반1건도 임시파일만 수정 후 해결했다. 소유권1641 도달 할당 실패/정상/재시도 liveDelta0, JNI blank/입출력/좌표/예외/실패·재시도/취소late-result 폐기 회귀를 포함한다. 새 arm64 APK의 DEX/arm64 native가 새 universal과 동일함을 확인하고 설치 성공 후 실행했으며 과거 APK 실행을 새 APK로 보고하지 않았다. 다른3ABI는 패키징/빌드이며 실행은arm64만이다.

진단 standalone의 ru_maxrss는 별도 프로세스 RSS high-water이고 앱 native/Java/PSS/OS OOM/peak/S22+ 전체5초가 아니다. 최종 단회 off/on은 각 행 약21.5–52.3/168.9–345.5ms로 진단 overhead가 크다. 초기 full 각3회의 원시값/중앙값과 최종 값은 외부 parity-resource에 보존하며 cold/warm 대표 측정으로 합치지 않는다. native 자체 취소는 동기식이며 late-result 폐기와 다르다.

독립 읽기 전용 코드 검토에서 중요한 새 이슈/UB는 확인되지 않았다. 마지막 이벤트의 byte 예산 초과 가능성을 write 직전 검사로 고쳤고 최종실행에서 parity/잘림0을 확인했다. Join 병합 support 부재는 범위 한계로 보존한다. 검토는 진단 코드/증거 범위이며 정답/현장/사용자 승인과 다르다. 최종 정리의 graphify/diff/소스 보존 결과는 이번 외부 `native-trace-v1`의 REPORT와 final-verification에서 확인한다. truth/b/u/t/scope/frozen=false·앱DI/DETECTOR_VALIDATED=false 보존, commit/push/PR/merge/배포 없음.


## 합성 원호 결합 판별 — 2026-10-16

알려진 타원 합성 초기3개와 사전 계약한 추가영상3개를 현재 v2-even-sampling 기준선의 Android standalone 및 actual JNI에서 실행했다. 초기 분절 입력은 native 타원 기하를 복원하지만 기존72점 support .5833<.65로 최종 탈락해 전체 경로의 GREEN 대조군이 아니다. 닫힌 타원만 최종1을 유지한다.

arc 범위만 바꾼 단일-rim에서 동일 outer stroke의58/55점 원호가 실제 JoinArcs3 `diff100.9798 > minR26.0506`로 거절되고 동등 타원으로 회수되지 않았다. 같은 construction rim의 inner/outer 조합은 별도로 구분했다. 두-rim 입력의 서로 다른 rim도 실제 같은 radius 분기에 도달했지만, 이후 endpoint/fit까지 통과하는 오결합 반례는 아니다. 가까운 두-rim의 혼합 지지점은 판정에서 제외했다. 부분 원은 등록/NFA를 통과하나 JNI support .4583로 최종 탈락한다. geometry-only 복원과 실제 지지점 provenance/물리 경계/제품 성공을 구별한다.

안전한 대체 predicate와 최종 support 보존 근거가 없어 검출 정책 수정·새 detector 버전·수정 RED/GREEN은 시작하지 않았다. radius 최초 검사만 우회해도 endpoint의 diff 비율 ladder가 거부한다는 것은 정적 소스 판단이며 bypass 실행 결과가 아니다. FPF·a<=80·support/same-rim·quality/object=null·DRAFT b/u/t/frozen=false·일반 앱 연결 상태를 유지한다.

새 APK DEX/arm64 native 및 실제 설치본 hash 검산 후 JNI 합성6·기존사진3을 실행했다. 사진3행 raw/support/source/대표/Float marker/상태/metric은 직전과 정확히 같고 U/L/A/R0이다. metal37 full/crop M19/FN18/FP14/보정32,metal40 full M37/FN3/FP38/보정41/SUCCESS. 후보 정책 변화가 없어 다른7행은 과거10행 증거로 재사용한다. full/crop은 독립사진이 아니다.

새 검증은 Spotless/build/Lint, 실제 JVM59/실패0, 명시 Android EdgeDrawingInstrumentedTest10+BDetectorMergeContractInstrumentedTest2+임시 합성/사진2의 고유14개다. 첫 임시 runner 컴파일 오류(runBlocking 누락)와 첫 사진시험 입력파일 누락 실패를 보존하고 해당 부분만 수정·재실행했다. 할당실패/재시도/소유권·JNI 좌표·취소 후 late-result 폐기 회귀를 포함한다. 계측 on/off raw 동일·잘림0. standalone RSS/시간은 앱 peak/S22+5초가 아니다. 읽기 전용 독립 코드 검토는 진단 결론을 지지했으며 독립 정답/현장 승인과 구분한다.

실행/해시/원본·지지점 overlay/상세 분석/최종 verification은 이번 채팅의 외부 증빙 `join-probe-v1`에 보존했다. repo는 이 기록만 추가하고 임시 JNI runner는 제거했다. 다음 결정은 같은 outer boundary와 다른 rim의 거리/turn/fit까지 구별하는 사전 predicate 및 충분한 영상 지지 입력이 바꿀 수 있다. 신규 엔진·여러 threshold/coverage/NFA/merge 수정·commit/원격 작업은 없다.


## 직접 native 원호 지지점 판별 — 2026-10-16

직전 실제 ordered support를 그대로 고정한 두 fixture만 Android arm64 native에서 실행했다. 동일 outer stroke58+55점은 현재 JoinArcs3의 `diff > minR`로 거절되고 outgoing2arc/0circle을 유지한다. 실제 join 밖의 진단용 강제 공동 FPF fit은 오차0.399991px로 기존1.50px 이하이며, construction 대비 중심3.049686px·축0.708163/0.471197px·각도1.678579도 오차로 기존 기하 허용치를 만족한다. 이는 공동 기하 설명의 근거이며 실제 join 성공/전체 영상 개선은 아니다.

다른 알려진 outer 경계58+55점도 실제 radius 거절이며 강제 fit 오차7.388800px다. 이 두 예시는 강제 fit으로 구별되지만 negative endpoint74.330px는 positive20.025px보다 멀고 실제 후보/fit에 도달하지 않는다. 가까운 경계 안전성·radius 검사의 필수성·우회 시 FP를 증명하지 않는다. 별도 native 정적 평가의 overlap0/turn호환/endpoint ladder branch없음을 실제 실행된 후속 거절 사유로 기록하지 않았다. 최초 radius 한 줄 제거만으로 해결되지 않는 것은 여전히 정적 제어 흐름 판단이다.

결합 정책을 바꾸지 않았다. 가까운 다른 경계를 분리할 사전 기하 규칙/endpoint 근거가 없으므로 결과를 보고 임계값을 만들어 넣지 않았고 수정 RED/GREEN·새 검출기 버전·새 영상은 없다. inner/outer와 같은 outer를 구별하고 혼합 지지점을 판정에서 제외했다. FPF·등록/NFA·a<=80·support72/.65·quality/object/merge·DRAFT b/u/t/frozen=false·일반 앱 검증 대기 상태를 유지한다. 다음 결정에는 실제 결합 후보 단계에 도달하는 깨끗한 근접 경계 대조와 사전 단일 기하 판단, metal40 020의 반대 경계 교차 반례 대조가 필요하다. metal37 028 물리 경계 구분은 미확정이다.

새 증빙은 외부 `native-arc-probe-v1`의 계약·fixture/point/source hash·native 로그·지지점/강제fit 관찰이미지·REPORT/final-verification에 보존한다. plain/off/on fixture 결과 동일, 관련 기존 gray2개 on/off 및 직전 raw 동일, 잘림0이다. 검출 source와 입력 hash 변화가 없어 합성6/사진10행을 반복하지 않았다. 사진 수치·marker/상태 parity는 직전 actual JNI 이력의 재사용이며 이번 새 사진 실행으로 표시하지 않는다.

새 검증은 기존 NDK 옵션을 명시한 Spotless/build/testAPK/Lint 성공, 실제 JVM59/실패0/오류0, 설치 APK DEX/arm64 native hash 확인 후 명시 EdgeDrawingInstrumentedTest10+BDetectorMergeContractInstrumentedTest2 성공이다. 첫 NDK 옵션 누락 빌드 실패를 별도 보존했다. 이번 APK build의 대부분 task는 UP-TO-DATE이고 Test만 강제 실행했다. 신규 native 소스 수정/임시 repo runner가 없고 외부 harness만 보존한다. standalone 단회 시간/RSS는 앱 peak·S22+5초 검증이 아니다. 원본 사용자 변경·branch/HEAD·truth/제품/원격 상태를 보존하며 커밋·push·PR·merge·배포는 없다.


## 2026-10-19 cap-151/cap-55 우선 개발 판별 — 개선 미확인

- 기준: 현재 dirty 소스/입력 snapshot과 기존 v6 DRAFT/frozen=false 정답 고정. cap full 주 비교, crop은 같은 사진 보조. 기존 native 수정/FPF/a<=80/support72/.65/quality/object=null·대표 우선순위를 유지했다.
- H1 경계 RMS는 기존 중심 gate 때문에 실제 full5장에 새 관계0개로 사전 중단했다. H2 raw Canny 성분 소유 영상 근거는 Android 합성4개 중 반복 fit1개 RED→4개 GREEN. 붙은 이웃/근접 동심/두 aperture+parent 보존을 확인했다. parent3→2 해결은 주장하지 않는다.
- 새 실제 PhotoStore/native/JNI 실행20회(기존5장×full/crop×baseline/candidate): 전10행 raw/gate/quality/대표/source/marker/M/FN/FP/C 정확히 동일. baseline와candidate actual geometry도 동일. 고정 raw와 실제 JNI의 각도 직렬화 차이는 최대8.89e-16rad로 기존1e-9 대조 허용 내다. 두 cap full은 cap151 M151/FN0/FP237/C237/COUNT_LIMIT, cap55 M54/FN1/FP39/C40/SUCCESS(93개). metal40/18/37도 각각 기존37/3/38,18/0/7,19/18/14 유지.
- 실험 APK 실제 설치 성공과 전체 APK SHA-256 일치 확인 후 명시 Android BSharedRim4+BDetectorMergeContract2+BObjectMerge6+EdgeDrawing10+임시 사진 runner1=23개 통과. 새 사진 runner1 안에 fixed replay20행/actual native20회가 포함되며 독립사진20장이라는 뜻이 아니다. trace0/3 parity·포화·개수 제한·할당실패·취소 후 재시도 회귀를 포함한다. 무관 Hough/전체 계측은 실행하지 않았다.
- Spotless/build/testAPK/Lint 성공. JVM test task는 UP-TO-DATE 재사용이며 이번 새 JVM 실행으로 세지 않는다. 최초 sandbox Gradle lock 실패, 미구현API compile RED, 실제 합성 RED1개는 외부 로그에 보존했다.
- 후처리3회 중앙값 cap151 full35.29→94.48ms, cap55 full7.71→20.57ms. 에뮬레이터 순차 실행의 후처리 비용 관측이며 Android 전체 분석 시간/대표 실기기5초/앱 peak 메모리 근거가 아니다.
- 사진 유지 기준인 FP/C 엄격감소가 없어 H2 미채택. 실험 소스/patch를 외부에 보존한 뒤 이번 변경만 제거했고 기존150개 source/input 영향 파일의 바이트 동일성을 확인했다(이후 위 기록 문서만 추가). 기존 사진·정답·과거 증빙·사용자 작업·branch/HEAD·제품 DI를 보존했다. 독립 코드 검토는 미채택 후보이므로 실행하지 않았다. 새 인터넷3장 위치 주석/실행·독립 검증·대표 기기·현장 인수는 미완료다.


최종 복원 후 첫 회귀18개 중1개가 PHOTO_READ로 실패했다. PhotoStore bounds decode 실패와 앱 시작 finishDeletes/removeOrphans가 등록되지 않은 테스트 JPG와 같은 photos 폴더를 사용함을 확인했다. 실행 시점의 삭제 호출 자체를 추적한 것은 아니므로 경합은 원인 가설로 구분한다. 테스트 fixture만 ContextWrapper의 고유 cache 하위 filesDir로 격리하여 정리 경로 공유를 구조적으로 제거했다. 같은 실패 클래스 순서로 재설치·재실행한18개가 모두 통과했다. 첫 실패 로그를 보존했고 반복 성공을 최초 무실패로 바꾸지 않는다. 제품 PhotoStore/Repository/DI와 검출 로직은 미수정이며 이 테스트 보완은 계수 정확도 개선이 아니다. 최종 포맷/build/testAPK/Lint·diff check와 graphify AST update를 완료했다. JVM task는 여전히 재사용이다.

## cap 객체 영역 소유권 사전 실험 — 2026-10-19

**결과: 가설1개를 사전 검사에서 탈락시켰고 계수 품질 개선은 미달성이다.** 현재 feat/auto-counting / 89bfa63 및 기존 dirty 작업을 재사용했다. 수정 전196파일 snapshot을 외부에 보존하고 직전 최종150source hash와 원본5/gray10/rows가 같은지 확인했다. 직전246 artifact도 기존hash와 일치했다. 따라서 아래 실제 검출 상태는 직전 PhotoStore/native/JNI 실행 증거의 재사용이며 이번 새 Android 실행이 아니다.

| full 사진 | 재사용 M/FN/FP/C | 실제 상태 | 새 H3 외부 사전 probe M/FN/FP/C | H3 상태 |
|---|---|---|---|---|
| cap-151 |151/0/237/237|COUNT_LIMIT, 진단388개·정상반환없음|151/0/225/225|예비376개, 투영COUNT_LIMIT·미채택|
| cap-55 |54/1/39/40|SUCCESS, 실제93개|54/1/39/40|변화0·미채택|
| metal-40 |37/3/38/41|SUCCESS, 실제75개|37/3/38/41|변화0·미채택|
| metal-18 |18/0/7/7|SUCCESS, 실제25개|18/0/7/7|변화0·미채택|
| metal-37 |19/18/14/32|SUCCESS, 실제33개|19/18/14/32|변화0·미채택|

cap151/cap55 실제 C237/40은 PRD 허용7/2보다230/38회 많다. H3의 수치12감소는 실제 정상반환 개선이 아니다. 같은5장 crop은 보조 비교이며 독립10장으로 세지 않는다. cap151 crop 예비317→305, M151/FN0/FP166→154, 나머지crop은불변이다.

H3는 가까운 중첩 기하만으로 합치지 않고 pair 주변의 밝은8-connected face와4-connected hole 소유권을 확인했다. aperture/band 중앙 gray의 중간값으로 분할하고 닫힌face·단일유효hole·inner와outer의대응을 요구했다. 기존 내외곽 spoke 정책을 기본경로에 연결하거나 H1/H2를 반복하지 않았다. 예비 후보 관계는cap151 full/crop각12개, cap55와금속0이다. 기존 metric의정렬/증강매칭을외부에서재현하여저장된기준선10행의M/FN/FP/C/대응ID가정확히같음을확인한뒤예비marker부분집합을평가했다. lost/gained target0, 대표geometry이동0,source합집합관계는외부에보존했다.

사전 고정한 새합성4대조는 단일annulus1/요구1, 붙은두annuli4/요구2, 실제두hole+parent3/요구2, 기존ring+추가후보2/요구2였다. 두번째/세번째의실패로Kotlin구현전에탈락시켰다. 마지막의숫자2는기존영상에두번째hole이그려지지않은상태에서inner+parent가합쳐지고추가후보가남은결과다. 실제두단면을해결했다는증거가아니며기존요구2를바꾸지않았다. 기존Task6의parent+두aperture미해결과이번실제두hole미해결을분리했다. priorANNULUS는붙은두annuli2를통과했으므로H3의4는정책대체시퇴행이지만기본코드에는연결되지않아새제품회귀는없다.

원본은기존16개와추가7개를검토했다(기존#0재확대1개별도). cap15128/166은같은cap외곽/개구부로보이며예비통합되지만, cap553/56의실제내외곽은brightface가patch끝으로이어져미통합됐다. 깊이/돌출·후퇴의정답은사진으로확정하지않았다. cap55 target043근처후보93은개구부에있지만DRAFT중심거리5.5296px>기존t5px다. 물리후보부재로단정하거나허용치/정답을수정하지않았다. 전체12개새관계를전수시각승인한것도아니며필수대조탈락뒤더검토하지않았다. 전체FP원인비율을표본에서추정하지않았다.

새실행은외부OpenCV4.12/NumPy2.2.6의고정gray10행사전검사,4합성대조,저장metric10행동등성검산과원본표본검토다. Kotlin/native/계측변경이없고유지후보도없어서RED→수정→GREEN·새APK설치/hash·Android사진20회·회귀클래스·Spotless/build/JVM/Lint·graphify update는실행하지않았다. 예비실패를GREEN이나AndroidPASS로세지않는다. 기존native안전성/PHOTO_READfixture격리/검출기본정책은source동일성범위에서보존하며이번새안전성시험은아니다. 독립코드/원본전수검토·S22+·현장인수는미실행이다.

인터넷3장의기존manifest와후속사용자count/scope기록을확인했다. 사용자번호범위63/19/265는보존됐지만좌표·범위매핑은미준비,challenge/reference/frozen=false라정량검출에쓰지않았다. 추가347주석이나새사진수집을선행조건으로요구하지않았다.

다음결정을바꿀증거는cap553/56처럼주변으로연결된밝은면에서도두경계를같은단면으로국소대응시키면서인접별개단면과실제두holeparent를구별하는**분리경계증거**다. patch를임의로잘라닫힌face를만들거나기하포함만으로parent를삭제하는것은해결근거가아니다. 같은임계값조정/무변화Android재실행을반복하지않았다. 외부CONTRACT·snapshot/hashes·관계JSON·CSV·원본/overlay·대조영상·로그·REPORT/final-verification에증빙을보존했다. 이번저장소변경은이절과설계문서의영향기록뿐이며commit/push/PR/merge/upload는없다.

## 법선·극성 기반 경계 역할 관측 — 2026-10-19

계수 품질 개선과 새 객체 선택 채택은 달성하지 못했다. 현재 dirty source/입력/주석 동일성을 확인하고 기존 full/crop10행의 raw1,231개에 원본·median3 gray의 타원 외향 법선 방향 밝기차와 Sobel gradient 정렬·관측 각도·부호 충돌을 새로 측정했다. 방사선이 아닌 `R(cosθ/a,sinθ/b)` 단위 법선을 사용한다. 고정 설명 규칙은 ±2px 밝기차12/gradient4/cos30°/동일부호47·반대부호7이며 관측용 APERTURE_LIKE/OUTER_LIKE/UNKNOWN이다. 정답·개수는 역할 판정 입력에 쓰지 않았다.

cap151 #28/#166은 외곽 -67/입구 +69로 역할이 갈리지만 cap55 #3 외곽은 +13/-28 UNKNOWN이고 #56 입구는 +72다. 기존 nested pair의 상보 역할은 cap151 full127/143, cap55 full16/22다. 이 수치는 객체 소유권/안전한 병합 수가 아니다. 정상 capless metal40 #0과 부분 가림 metal18 #8도 UNKNOWN이다. 합성 밝은 내부 대조에서는 실제 안쪽/외곽의 부호가 뒤집혔고, 실제 두hole+parent의 parent는 단일 annulus 외곽과 같은 역할을 가진다. 역할 평균 차이만으로 동일 객체를 확정할 수 없어 객체 선택 구현 진입 조건을 통과하지 못했다. 기존 ring3보존·실제 두hole 최종2와 양쪽 입구 보존 계약을 변경하거나 해결됐다고 보고하지 않았다. 신호 대조7개와 법선 기하 assertions를 실행했으며 최종 객체 계수 테스트7개 PASS를 의미하지 않는다.

새 실패군으로 cap55 full raw13개 입구형 후보가 b5.6478–6.9191px라 기존 b>=7 gate에서 탈락함을 확인했다. 입구형 후보의 위치 coverage는 raw54→quality41이며 외곽 후보가 남아 전체 coverage는54 그대로다. 추가 원본 raw90도 작은 정상 입구로 보이나13개 전수 육안 확정은 아니다. cap55 target043은 기존 입구후보93의 중심거리5.5296px>t5px인 대표/주석 문제로 후보 부재와 구별했다. metal37 raw25→gate19는 큰축 a>80 제한과 연관되고 raw 중심 대응 없는12개와 분리했다. coverage는 중심 최대매칭이지 정상 입구 경계 검증 수가 아니다.

이번 기준선·ANNULUS_90 비교는 동등성 확인된 과거 Android 자료 재사용이다. 전10행 M/FN/FP/C와 대응map exact검산, source/anchor exact·geometry차 최대8.89e-16<1e-9를 확인했다. N1은 관측만 하여 marker/source/대표/lost/gained 변화0이며 새 선택법의 개선 수치가 없다. 현재 cap151 full151/0/237/237 COUNT_LIMIT(388진단·정상반환없음), cap55 full54/1/39/40 SUCCESS93개이고 PRD 허용7/2보다230/38회 초과한다. 보류 ANNULUS_90의151/0/121/121,54/1/23/24는 과거 이득이며 이번 성과가 아니다. 금속 full40/18/37의37/3/38/41,18/0/7/7,19/18/14/32도 유지되는 과거 상태다. full/crop10행 비교와 원본/관측 overlay·모든 입력/source/config SHA는 외부 `normal-role-v1/REPORT.md`, `comparison.csv`, `config.json`, `final-verification.json`에 보존했다.

기존23사례+기존재확대1·새작은입구1의25카드를 원본으로 검토했고 판단은 DRAFT다. 가설1/최대2, Kotlin/NDK 새 구현0이다. 새 source 변경·유지 후보가 없어 Spotless/build/JVM/Lint/계측/APK/PhotoStore/native사진 재실행/graphify update는 미실행했다. 이전 안전성 PASS를 이번 새 실행으로 세지 않는다. 이번 source/입력/정답/제품DI/DETECTOR_VALIDATED/기존 사용자 변경은 보존하고 두 문서에 기록만 추가했다. commit/push/PR/merge/upload 없음. 후속 채택 추천 없음, 독립사진·대표기기·제품 인수 미완료다. 다음 유효 비교는 부호 이상의 경계→입구 소유권 근거와 raw의 작은 입구 seed 보존을 분리해 확인하는 것이다.


## 중심·영상 근거 객체 선택 P2-v2 — 2026-10-19~20

평가용 구현 후 새 APK로 고정 10행의 BASELINE/ANNULUS_90/RADIAL_OBJECTS_V2를 각각 실행했다. 각 조건은 고정 native 후보 후처리 3회와 실제 PhotoStore/native 첫·반복 호출 2회로 구분한다. 실제 detector 호출 총 60회, 결과 JSON 30개다. 입력 gray hash, 실제/고정 후보 대표·출처, Float 중심, 상태와 정답 대응을 검산했다. 기존 BASELINE/ANNULUS 수치·중심·출처를 재현했고 Python 선택 경로와 최종 Android 수치/중심도 일치했다. 이전 JSON과 각도 재직렬화 차이는 최대 8.9e-16 rad이고 중심/축은 정확히 같다. 정답 위치 허용치를 변경한 검산이 아니다.

cap151 기준선 full388/crop317는 COUNT_LIMIT 실패의 진단 개수이며 반환 개수가 아니다. 새 경로는 full259/crop188을 SUCCESS로 반환하지만 정답151이나 제품 합격을 뜻하지 않는다. cap55는 full76/crop77, 금속40은75/70, 금속18은25/26, 금속37은33/33이다. 전체 10행에서 FN/FP 비증가, baseline lost/gained 0, cap FP 감소를 확인했다. 새 full C/허용 C는 cap151 108/7, cap55 23/2, metal40 41/2, metal18 7/1, metal37 32/1로 모두 미달이다.

최종 새 실행은 Spotless, assembleDebug 및 테스트 APK 빌드, 강제 재실행 JVM 59개(실패/오류/skip 0), lintDebug, 명시한 Android 7클래스 29개(실패/skip 0)다. Android 클래스는 BRadialEvaluationInstrumentedTest, BRadialObjectInstrumentedTest, BRadialDetectorContractInstrumentedTest, BObjectMergeInstrumentedTest, BDetectorMergeContractInstrumentedTest, EdgeDrawingInstrumentedTest, AnalysisPhotoInstrumentedTest다. Hough 관련 클래스는 실행하지 않았다. 실제 0/300/301·radial 예산 초과·취소 후 재시도·기존 JNI 소유권/실패·사진 EXIF 회귀를 포함했다.

초기 구현의 합성 RED, 초기 Android parent 탐색 예산 초과, 테스트 APK 설치 공간 부족/캐시 입력 소실, 포맷·빌드 인자 오류, 리뷰에서 추가한 parent 자기 근거 결함의 RED를 구분하여 외부 로그에 보존했다. lossless seed 공간 색인과 contour 직접 자식 탐색으로 예산 실패를 수정했고 예산/선택 기준을 완화하지 않았다. 테스트 데이터는 별도 files 경로로 옮겼다. parent 수정 후 독립 리뷰에서 미해결 finding은 없었으며 최종 소스로 29개를 다시 통과했다.

`graphify update .`로 코드 AST 그래프를 갱신했다(API 호출 없음). 2907 node/5355 edge, 173 community이며 지원하지 않는 파일 29개와 symbol이 없는 코드 14개, 일부 community 이름의 hub 기반 변경 경고를 기록했다. 의미 기반 문서 그래프 재생성은 실행하지 않았다. 실행 전 196개 원본 파일을 기준으로 기존 사용자 변경 보존과 세션 변경을 분리 검토했다. 커밋·push·PR·병합은 실행하지 않았다.

실기기 S22+의 peak 메모리·앱 표시/저장 포함 5초·전체 검수 시간, 독립 실사진 인수, 주석 동결, 수명주기/프로세스 종료의 새 검증은 실행하지 않았다. 기존 검증을 이번 새 실행으로 세지 않는다. 최종 실험 산출물은 저장소 밖 `region-object-v1`의 REPORT/comparison/final-verification/소스 스냅샷에 보존했다.


## 2026-10-20 — 국소 지지 경계 객체 선택 평가

최종 새 APK에서 명시9클래스35검사(실패/skip0), 10행×4방법×4회 실제 detector160회와 별도 fixed 후처리를 실행했다. 각 행 baseline/ANNULUS/P2/local의 좌표·출처·매칭·반환/COUNT_LIMIT를 확인했고 local은 desktop/fixed/native와 일치했다. 11개 추가 병합(full/crop 중복 포함)을 원본 확대에서 확인했고 이웃 target 충돌0·기존 정상 대응 손실0이다.

Spotless, assembleDebug, assembleDebugAndroidTest, JVM59검사 강제 재실행, lintDebug 통과. arm64 API36 에뮬레이터에서 비교 순서를 행마다 순환하고 동시 heavy host 작업 없이 측정했다. local 실제 detector 첫22.5~220.4ms, 반복3회 중앙22.1~204.7ms; P2 반복184.1~2131.4ms 대비 행별80.9~96.0% 감소. OpenCV init 호출은 별도 기록했으며 fixed 후처리가 각 detector 호출보다 먼저 실행돼 프로세스 cold start 측정은 아니다. 최초 JNI library load는 최초 baseline detector에 포함, 별도 분리 측정하지 않았다. 대표 S22+·peak 메모리·화면/저장 포함5초·전체 검수 시간·독립 인수는 미실행이다.

RED는 새 local 함수 미정의 컴파일 실패, GREEN은 구현 후 Android 객체/계약 대조 통과다. 최초 desktop gate/contour replay의 영상 밖 표본 처리 오류를 수정하고 전20설정을 재실행해 분모72를 보존했다. 실패본은 invalid-border로 보존했다. APK 설치 공간 부족은 테스트 패키지만 데이터 보존 코드 교체로 해결했다. 실제 source/정답/t/frozen/제품 연결을 완화하지 않았다. 상세 자료는 별도 pipe-improvement-v1 폴더의 REPORT.md, comparison.json, 실패 설정·비용·hash·재현 명령에 보존한다.


## 2026-10-20 — LOCAL 이후 shared-edge 최종 검증

최종 새 APK의 지정12클래스45개 검사 실패/skip0, 10행×5방법×4호출의 실제 PhotoStore/native/JNI200회와50개 결과JSON을 확보했다. 기준선cap151 full388/crop317은 COUNT_LIMIT 이전 진단값이며 정상 반환으로 세지 않았다. fixed후처리와 실제호출을 구분하고 전체 좌표/출처/일대일매칭을 검산했다. 최적화 전후50조건 대표/표시/지표는 모두 동일했다.

포맷·assembleDebug·assembleDebugAndroidTest·JVM59개(실패/오류/skip0)·lintDebug·graphify AST update·diff check와 별도 읽기전용 리뷰를 수행했다. 리뷰에서 실제 타원 ray거리, method 식별자, prefilter예산fallback을 수정하고 회귀검사를 추가했다. 초기 입력경로 부재 실패와 중간 정상44개 검사를 별도 보존했으며 최종45개로 다시 확인했다. 무관한 Hough 테스트는 선택하지 않았다.

동일 API36 arm64 에뮬레이터에서 방법순서를 행별 순환하고 heavy host 작업 없이 초기화/JNI로드/최초빈native/사진첫호출/반복3회중앙을 구분했다. 최종 SHARED 반복27.8~225.1ms, LOCAL과의 행별 차이-3.1~46.9ms다. cap151 full/crop은 각각+11.1/+5.5ms이며 성능최적화 성과로 보고하지 않는다. 한 행의 소폭 음수차이는 반복3회 에뮬레이터 변동과 구별할 증거가 없다. 대표 S22+·peak메모리·프로세스cold전체지연·UI표시/저장·전체검수·독립사진/정답동결·새OS수명주기 인수는 미실행이다.

기존204파일 스냅샷과 SHA256을 기준으로 사용자 변경·main/PRD/build·HEAD/index/branch를 보존했다. 커밋·push·PR·병합·외부 업로드는 없다. 상세 입력/설정/소스 해시·원본/오버레이·실패/타협 설정·외부 라이선스·재현명령은 저장소 밖 pipe-improvement-v2에 보존했다.

## SHARED 이후 객체 선택: CONTINUOUS_FACES_V1 — 2026-10-20

사용자가 평가 코드/native 필요 변경과 Android 검증을 승인한 범위에서 수행했다. HEAD `89bfa63`, `feat/auto-counting`의 기존 미커밋/미추적465파일을 해시·복사한 후 추가 작업했고 이전 최종 소스와 시작 소스 차이는 없었다. 이번 구현은 androidTest 선택 단계/옵션/검사와 관련 문서다. 일반 앱·DI·런타임/빌드 의존성·정답·허용거리·사진ROI·검증 대기 플래그·Git HEAD/index/branch는 보존한다. 커밋·원격 작업 없음.

- 새 평가 경로: `localObjects=true, sharedEdges=true, continuousFaces=true`. 기존 LOCAL/SHARED 선택 가능. dense path 연속성·구간별 대비의 최소 구현→실사진 비교→외곽 극성 수정→Android 검증을 완료했다. 축A 최초 포함6설정과 새 원본 근거에 따른 추가2설정, 축B 영상 중심 보정4설정을 사전 예산으로 실행했다. 중심 보정은 정상 대응 손실/주 목표 회수 실패로 비채택. 원본 지지 경계 없이 기하만 합치는 ablation은 채택하지 않았다.
- 실제 Android: API36 sdk_gphone64_arm64 `emulator-5554`, 6방법×10행×4호출=240 PhotoStore→native/JNI→최종 Marker 호출. 최종 지정 검사 **OK (55 tests)**, 실패0·skip0. Hough 미선택. native/fixed 결과·입력 gray hash를 매호출 검산했다. raw는 변경 없는 이전 nativeShapes를 재사용했고 버려진 raw 전부를 JNI에서 새 export한 것으로 주장하지 않는다.
- SHARED 대비 M/FN/FP/C: cap151 full `151/0/105/105→151/0/91/91`, crop `151/0/34/34→151/0/21/21`; cap55 full `54/1/19/20→54/1/7/8`, crop `54/1/19/20→54/1/6/7`. 금속6행 동일. 전10행 FN/FP 비증가·기존 정상 대응 손실0·중심 이동0·위치 대응하는 서로 다른 입구 충돌0. 변경 확대를 직접 확인해 이웃 입구 오통합을 발견하지 못했다. full/crop을 중복 세지 않은 개선 대상은14+13곳이다.
- 비용: 같은 에뮬레이터/입력/조건에서 LOCAL/SHARED/새 후보와 과거 비교3방법을 함께 재측정했다. 초기화18.891ms·JNI load0.811ms·최초빈 native2.169ms 별도. 새 후보 반복3회 중앙27.3~249.8ms, SHARED 대비 -15.7~+47.7ms. 10행 중9행 추가비용; metal37crop의 음수 차이는 작은 반복 표본의 변동과 구분하지 못하므로 속도 개선으로 세지 않는다. 실기기·프로세스 cold 전체시간·UI표시/저장·전체 검수시간은 미검증.
- 최종 포맷·앱/테스트 APK 빌드·JVM·Lint 통과, graphify AST-only/API0 갱신, diff 검사와 독립 소스 리뷰 완료. 같은입구 내외곽/별개입구+parent/parent자기근거/외곽-only/붙은이웃·annuli/크기차이/밝은내부·capless·반사·가림/chain·입력순서/기하경계/회전·PI각도/거리변환/예산·명시실패·취소·재시도·late결과/JNI소유권을 지정 검사에서 확인했다. 일반 앱 현장·수명 인수와 구분한다.
- 산출물 `pipe-improvement-v3`: REPORT/PLAN/RESEARCH/selected-config/final-verification, 10행×6방법 표·전체 overlay/변경 확대, 최신 SHARED ledger/단계 출처, 실패·ablation·중심 이동 연구, 실제 시간·검증로그·보존 확인·session-only.patch·재현 코드. 이전 자료는 덮어쓰지 않았다. 초기 RED/포맷 오류와 리뷰 수정 전53검사 결과는 별도 보존했다. cap55target043은 raw 최근접5.5296px/t5 위치오차로 남으며 생성 부재와 구별한다.

판정: 정확도 개선·평가 경로 제한 채택. PRD 보정량은 전10행 미달, 제품 인수 미완료. 독립사진·동결정답·S22+ 실기기·전체 검수시간을 이번 개발 개선의 선행 조건으로 삼지 않았으며 이번 성공을 현장 인수로 확대하지 않는다.


## CONTINUOUS 이후 지지 구간 선택 검증 — 2026-10-20

`EdgeDrawingEvaluationDetector(localObjects=true, sharedEdges=true, supportedFaces=true)`는 SHARED 이후 CONTINUOUS 대신 실제 지지 outer edge 구간의 영상 관계를 선택하는 androidTest 전용 경로다. `continuousFaces`와 상호배타이며 기존 LOCAL/SHARED/CONTINUOUS는 보존한다. 전체72 방향에서 Canny/normal로 독립 추출한 지지 구간 중 연속 band·내측 동일 극성·외측 절대 대비를 함께 만족하는24구간 이상, 최소3분면(각3구간 이상), 지지되지만 연속성이 없는 구간24개 이하를 요구한다. 누락 구간은 분모에서 빼지 않는다. 고정 원래 anchor/source union과 독립 중심 veto를 유지한다.

원본5장 full/고정crop10행 v6 DRAFT/frozen=false 개발 평가에서 CONTINUOUS 대비 cap151 C full91→90/crop21→19, cap55 full8→5/crop7→3. 전체10행 FN/FP 비증가·정상 대응 손실0·새 대응0·중심 이동0이며 금속6행은 같다. 변경10view는 실제6입구(정답 t 내 대응5입구 + cap55target043의 t 밖1입구)다. target043 최근접 raw5.529621px/t5는 그대로이고 중복 제거를 FN 회수로 세지 않는다. 배경 묶음과 시계는 별도 잔여 원인이다.

API36 arm64 에뮬레이터에서7방법×10행, 조건마다 첫 호출+반복3회로 실제 PhotoStore→native/JNI→Marker280회. 새 반복 중앙28.2~271.6ms, CONTINUOUS 대비+0.2~29.7ms. 특징 준비 비용을 포함하며 실기기5초·cold 전체 지연·UI/저장·검수시간·peak memory·현장 인수는 미검증이다. 평가 경로 제한 채택이며 일반 앱 DI/런타임 의존성/DETECTOR_VALIDATED는 변경하지 않는다. 안전 개발 결과를 제품 인수로 확대하지 않는다.

지정 계측 최종 **OK (68 tests)**, 실패/skip0. 기존 same-rim 계약과 실제두입구+parent 최종2개/각 대응, outer-only·밝은내부·capless·반사·가림·크기차이·chain·순서·회전/PI·실제pixel거리·구간분모/공간분포·누락영상·후보/쌍예산·명시실패·취소/재시도/late-result·JNI/Mat 소유권을 구분해 확인했다. 최종 Spotless·앱/테스트APK·JVM task·Lint 성공. JVM task는 UP-TO-DATE이며 기존XML59개 실패/오류/skip0을 검산했으므로 새59개 실행으로 쓰지 않는다. 무관한 Hough 검사는 실행하지 않았다.

초기 새 API 부재의 컴파일RED, Gradle/ADB sandbox 접근 실패, 그리고 평가 테스트 플래그를 빌드 중 수정해 생긴68개 중1개 실패를 산출물에 보존했다. 후자는 소스 고정 후 Kotlin30task 새 실행→APK 재패키징→최종68개 재검사로 해소했고 소스 해시와 설치APK/빌드APK 동일 해시를 대조했다. 독립 읽기전용 최종 소스 리뷰의 material finding0은 실제 평가/계측의 대체가 아니다.

6설정과 단계대체·edge지지/모순/공간분포 ablation을 보존했다. maxBad16의 실제66개 통과/70조건 결과도 보존하며 최종24 결과와 구분한다. 최종24의 단계대체가 결합 경로와 geometry/source까지10행 동일해 선택 단계를 대체했다. 추가 생성기/중심축은 새 근거가 없어 실행하지 않았다. cap151crop19>허용7, cap55crop3>허용2이고 금속도 미달이므로 PRD/제품 인수 미완료다.

저장소 밖 세션 산출물 `pipe-improvement-v4`의 REPORT.md·comparison.json·error-ledger.json·stage-provenance.json·provenance-changes.json·ablation.json·timing.json·selected-config.json·final-verification.json·visuals/·session-only.patch·REPRODUCE.md에 세부 근거가 있다. 원본/ROI/정답/b/u/t는 동일하고 source/input/truth/config hash를 남겼다. 기존479개 dirty/untracked 파일 snapshot과 HEAD/branch/index 보존을 확인한다. 커밋·원격 작업은 하지 않았다.


## SUPPORTED_FACES_V1 앱 내 시험 도입 — 2026-10-20

최신 명시적 승인은 이전 평가 전용 제한을 일반 앱 시험 도입까지 확장했다. OpenCV4.12.0 implementation, main/cpp의 실제 검출 JNI와 공통 계산, SupportedFacesPipeDetector/Koin 연결, 자동 분석/재분석 버튼과 검토 안내를 적용했다. 기존 Repository·ViewModel·보정·확정 저장·영속 계약은 변경하지 않았다. DETECTOR_VALIDATED=false와 v6 DRAFT/frozen=false, PRD 인수 기준을 유지했다. 커밋·원격 작업은 수행하지 않았다.

### 동일성

원본5장×full/기존 고정crop10행에서 **일반 앱 APK의 PhotoStore→libpounter_ed JNI→최종 Marker**를 실행했다. 앱40회(행별첫회+반복3회)와 평가 경로를 비교하고, 앱 native gray 경계의 raw geometry·선택 대표 anchor/source도 비교했다. 이전 완료 자료까지 상태/실패 이유·geometry/source·표시ID/좌표·매칭쌍·gray hash가 정확히 같다. 앱과 평가 Marker 전체 비교에는 반경/manual도 포함했다. lost/gained는 모두0이다.

|사진|full M/FN/FP/C|기존 고정crop M/FN/FP/C|
|---|---|---|
|cap151|151/0/90/90|151/0/19/19|
|cap55|54/1/4/5|54/1/2/3|
|metal40|37/3/38/41|37/3/33/36|
|metal18|18/0/7/7|18/0/8/8|
|metal37|19/18/14/32|19/18/14/32|

추가로 기존7방법×10행의 실제 detector280회를 재실행해 이전 v4 완료 자료와70조건의 상태/반환수·raw/gate/support/same-rim수·대표geometry/source·Marker·매칭 전체가 같다. BASELINE cap151두행의 COUNT_LIMIT 실제 실패도 그대로다. LOCAL·SHARED·CONTINUOUS 비교군을 보존했다. 좋은 raw 입력을 주입한 계약검사와 실제 영상 결과를 분리했다.

### 빌드·런타임·화면

SpotlessCheck, debug/release/test APK, 새 JVM59개(11suite, 실패/오류/skip0), Lint가 통과했다. 최종 지정 Android **94개, 실패0/skip0**: 앱10행1 + 상태/DI/실제UI/Navigation/Repository/사진/LOCAL·SHARED·CONTINUOUS·SUPPORTED/native계약88 + 초기화/전체비용2 +7방법회귀1 + 큰글꼴2. Hough는 실행하지 않았다. 새 비용 검사 추가 후에는 해당 테스트 APK/Lint/Spotless만 갱신했고 의미 없는 전체검사 반복은 하지 않았다.

실제 MainActivity/앱DI/Repository에서 cap55 원본 가져오기→자동58개표시→확대→추가59→되돌리기58→삭제57→되돌리기58→재분석초기화확인취소→Activity재생성→확정저장→목록→기록재열기를 자동 UI로 확인했다. 가져오기는 실제 URI를 Repository.import에 전달했으며 **시스템 사진 선택기 터치/실제 카메라 촬영의 수동 실행은 미검증**이다. 수동 표시 경로도 별도 통과했다. 실패/취소/재시도·0개·301상한·후보포화·저장실패재시도·late-result와 JNI/Mat/Bitmap 소유권은 지정 계약/VM/UI검사에서 확인했다. 주입된 geometry/저장실패 대역으로 실제 사진 성능이나 실제 디스크 고갈을 주장하지 않는다.

API36 arm64/4KB 에뮬레이터에서 실행했으며 S22+는 연결되지 않았다. 다른 API36.1 arm64/4KB 에뮬레이터도 데이터 보존 업데이트를 설치했다. 저장 공간 부족 때문에 범용 설치가 실패했고, 앱을 삭제하지 않고 테스트전용패키지 데이터를 보존해 해제한 뒤 동일 소스의arm64 APK로 업데이트했다. 기존16기록의 모든DB행/사진16개가 바이트/논리값 그대로임을 백업과 대조했다. 강제종료 후 재실행/DB보존을 별도 확인했다. Activity재생성과 OS프로세스종료는 구분하며 OS에 의한 실제 프로세스 종료는 미검증이다.

앱debug/release의4ABI 패키징에 각 라이브러리1개씩 포함되고 평가JNI/정답/비교정책은 없다. 실제 포함된64비트 native 모든 ELF LOAD/ZIP16KB 정렬 및 zipalign 검사가 통과했다. 16KB 실행환경은 없어 실제16KB 실행은 미검증이다. NDK/AGP/OpenCV 버전을 올리지 않았다. 릴리스는 unsigned APK 빌드/패키징 검사이며 릴리스 설치·배포 인수가 아니다. 앱 manifest에 INTERNET 권한이 없고 검출은 로컬 파일/연산만 사용한다. 공식 기준은 [Android16KB문서](https://developer.android.com/guide/practices/page-sizes)를 확인했다.

### 비용·한계

빌드/무거운 비교를 병행하지 않은 새 instrumentation target 프로세스에서 OpenCV초기화58.87ms/JNI로드1.85ms. 별도 새 프로세스 cap55full 사진가져오기87.83ms, 첫분석400.97ms, 반복217.12/299.41/171.89ms(중앙217.12). 첫분석에는 실제 라이브러리초기화·디코드·ROI·특징/호출내캐시·JNI·좌표복원을 포함한다. Activity시작/시스템선택기/사람검수시간은 제외한다. 10행 본측정의 첫cap151full은1599.16ms였으며 이를 숨기거나 모든 첫분석을401ms로 일반화하지 않는다. 행별 반복중앙은30.85~471.52ms. 전후PSS/native할당 스냅샷을 보존했으며 peak나 실기기 메모리 추정은 하지 않았다.

앱통합과 검출결과보존은 확인했으나 사람의검수효용/현장인수는 미완료다. 실제오류·전체검수시간·누적보정/재분석/재촬영/포기를 기록할 외부관찰CSV를 산출물에 준비했다. 기존 createdAt/confirmedAt과 automatic/markers만으로 누적조작·사진선택포함전체시간을 모두 복원할 수는 없다. 자동UI시간이나 무수정확정은 인간효용/오류0의 근거가 아니다. 확대된 실제사진에서 밀집한 번호표시가 겹치는 관측은 후속사용성 과제로 남기며 새검출튜닝으로 돌아가지 않았다.

초기 DI RED1실패, 이동중 컴파일오류, 테스트APK설치공간부족/오래된클래스실행, main/androidTest Kotlin동일파일명 facade충돌을 최종성공과 분리 보존했다. 평가파일명을 EvaluationEdgeDrawingCandidates.kt로 바꿔 facade충돌을 해소했다. 이는 이번통합의문제이며 원래평가알고리즘의 수치결함으로 보고하지 않는다. 새컨텍스트 읽기전용소스리뷰의 중대발견0. graphify AST-only/API0 갱신과 이번변경diff/보존검산을 완료했다. 전체산출물은 이번세션 저장소밖 app-integration 폴더에 두고 원본사진/개인경로를 Git문서에 추가하지 않았다.


## PR #7 현재 작업 게시 검증 — 2026-10-20

사용자의 후속 요청으로 현재 관련 코드·테스트·문서의 목적 단위 커밋, 기존 feat/auto-counting push, PR #7 제목/본문 갱신을 승인했다. 앞 절의 커밋/원격 제외는 앱 시험 도입 당시 범위이며 이번 게시 요청을 막는 제한이 아니다. 병합·브랜치 삭제·배포는 포함하지 않는다.

게시 직전 앱 소스 전체 hash를 앱 시험 도입 최종94개 Android 검사 스냅샷과 비교해 차이0을 확인했다. Android94개는 앞 절의 실행 결과를 재사용하며 이번에 새94개를 실행했다고 쓰지 않는다. 이번에는 SpotlessCheck·debug/release/test APK·Lint를 확인하고 JVM59개를 새 실행했다(실패/오류/skip0). 빌드141task 중7실행/134UP-TO-DATE이며 Lint/기존 compile cache를 새 실행 수로 합산하지 않는다.

샘플링 회귀 생성기의 기본 소스 경로가 이동 전 androidTest/cpp를 가리켜 FileNotFoundError인 것을 재현했다. main/cpp로 고치고 실제 원본에서 C++ 회귀 출력 생성이 성공했다. 단계 trace 생성기도 현재 main 원본에서 plain/trace/해시 출력 생성이 성공했다. 이번에는 생성된 standalone C++를 새 컴파일/실행하지 않았으며 이전 native 실행을 이번 새 실행으로 세지 않는다.

관련83개 텍스트 소스/테스트/문서를 게시 대상으로 확인했다. 현장 사진·번호/오버레이·개인 경로·비밀값·APK는 포함하지 않는다. 기존 로컬 graphify 산출물은 검증 이력의 기존 방침대로 미추적 상태를 유지하며 최신 AST 갱신은 보존한다. .codex 로컬 도구/설정과 무관한 미추적 아이콘 원본도 변경·스테이징하지 않는다. graphify 전체 갱신을 생략하자는 판단이 아니라 기존 공유 범위를 유지하는 것이다.
