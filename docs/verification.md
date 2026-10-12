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
