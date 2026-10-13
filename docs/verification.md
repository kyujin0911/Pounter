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
