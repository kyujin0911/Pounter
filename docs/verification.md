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
- Small_Phone API36: AppUiTest, LargeFontUiTest, NavigationUiTest, DiInstrumentedTest, DetectionFlowUiTest, AnalysisMetadataInstrumentedTest, AnalysisPhotoInstrumentedTest, CompositeDetectorInstrumentedTest, EvaluationProtocolInstrumentedTest 총 34개 통과. DetectionEvaluationTest/DetectionAcceptanceTest 각 1개는 외부 manifest/config 미제공으로 skip. skip은 현장 합격이 아니다.
- RepositoryInstrumentedTest: importEditReloadConfirmAndDelete, staleRevisionCannotOverwriteSavedChanges, interruptedDeletionIsCompletedOnStartup, invalidInputDoesNotReplaceExistingRecord, cancellationAfterInsertKeepsReferencedPhoto, legacyVersionOneDatabaseAndPayloadReopenWithoutMigration 6개 통과. 기존 Hough 프로토타입 메서드는 이 회귀 목록에서 실행하지 않았다. Task 6 실제 tuning 입력도 없으므로 현장 Hough 기준선 평가 미실행.
- Task 1 메타데이터/기존 v1 SQL, Task 3 EXIF·입력·자원(4개), Task 4 합성 복합 검출·상한·직렬 취소(5개)는 API36/API36.1 양쪽에서 개별 통과.
- 초기 새 타입 테스트는 미구현 API로 컴파일 실패했고, Task 5 저장 실패·snapshot 회귀 2개는 기존 구현의 assertion 실패를 확인한 뒤 통과했다. 환경/파서 오류는 기능 실패 증거와 구분한다. 초기 연쇄 통합 fixture는 포함 관계가 없던 동률 원 3개를 실제 포함되는 축/점수 차이의 연쇄로 수정했으며 기대 개수/포함 기준은 유지했다.
- Task 4 첫 fixture는 앱 startup orphan 정리와 충돌했고, 테스트 사진을 별도 cache 저장소로 분리했다. OpenCV Gaussian 분리의 float convertTo에서 SIGILL을 확인해 양극성 국소 평균 분리로 바꾼 뒤 동일 테스트를 통과했다. 실제 기기 최적화·속도 증거가 아니다.
- API36.1 UI 13개는 기존 Espresso의 InputManager.getInstance reflection 오류로 실패. 테스트 의존성 전체 업그레이드는 하지 않았고, API36에서 UI 13개를 다시 통과했다. API36.1 UI 호환성은 미검증/실패 상태로 남긴다.

실사진의 정답 위치/개수·독립성·현장 고정 config가 없어 튜닝/독립 인수는 실행하지 않았다. S22+ 연결/성능, 5초 사용자 관찰 지연, peak 메모리, 보정 후 정답, 전체 검수 효용, OS 프로세스 종료와 force-stop은 검증 대기. 기존 Navigation의 StateRestorationTester/entry 동작과 이 현장 검증을 동일시하지 않는다. Task 7은 진행하지 않는다.

### 최종 리뷰 수정 후 재검증

독립 전체 리뷰에서 Important 1건(분석 Bitmap의 IO 반환 전달 직전 취소 시 미해제)을 확인했다. 동일 반환 경계를 보류한 회귀 테스트의 해제 assertion 실패를 확인하고, 반환 컨텍스트 바깥에서 미전달 자원을 닫도록 수정했다. 재현 테스트와 정상 사진/복합 검출 10개 통과. Critical/Minor 지적 없음.

수정된 코드에서 포맷·앱/test APK 빌드·Lint 및 JVM 52개 통과. API36의 앞서 명시한 모든 클래스와 Repository 허용 6메서드를 합친 최종 회귀는 **41개 통과, 외부 현장 평가 2개 skip, 실패 0**이다. API36.1 UI는 기존 호환성 실패 상태로 유지한다. 실제 현장 인수·실기기·프로세스 종료를 이 결과로 대신하지 않는다.
