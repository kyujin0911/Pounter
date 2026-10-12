# 프로젝트 작업 지침

## 판단 기준

핵심 흐름 완성 > 기능 수. 사용성과 정보 전달 > 장식. 단순 구조와 플랫폼 기능을 우선한다. 오류를 숨기거나 기대값을 약화해 테스트를 통과시키지 않는다. 사용자 문제 진술·관찰·기술 가설·테스트·성과를 분리한다.

## 아키텍처

Kotlin, Compose, 단일 모듈. UI/데이터 책임 분리. 데이터는 Repository 경유. 화면 단위 ViewModel에서 읽기 전용 StateFlow<UiState>와 Action으로 UDF/MVI 구현. Compose는 collectAsStateWithLifecycle. 파일·DB·검출 부수 효과와 상태 전환 분리. 불변 상태. 확정 기록 읽기 전용, revision 검사로 오래된 쓰기 방지. 중요한 결과는 상태와 DB에 반영한다.

새 모듈·UseCase·DI/MVI 프레임워크는 필요성이 확인될 때만 도입. 생성자 주입 유지. 일시 UI/복원/영속 상태를 구분. 취소와 오래된 비동기 결과, 중복 확정, 사진/DB 저장 실패를 검증한다.

## 검출 적용 게이트

현장 정확도 검증 전 androidTest의 HoughPipeDetector를 앱의 검출기로 옮기거나 활성화하지 않는다. OpenCV는 androidTestImplementation으로만 둔다. 앱은 ValidationRequiredDetector를 사용한다. 사용자는 2026-10-12 기존 정면 조건 유지·추가 사진 검증을 선택했다. 합성 사진 테스트를 현장 성능으로 확대하지 않는다. 모델 학습이나 지원 조건 변경은 사용자와 결정한다. 수동 표시 앱을 자동 계수 MVP 완료로 보고하지 않는다.

## Git

GitHub Flow. main은 검증된 실행 상태. 짧은 codex/ 목적 브랜치에서 변경→관련 검증→리뷰→커밋. 사용자 변경 보존. 이번 작업 파일만 커밋. push·PR·병합·배포는 별도 요청 시 수행. worktree는 격리 도구이지 브랜치 전략이 아니다. 아직 main에 검증된 초기 커밋은 없다.

## 실행·검증

./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest

Android Studio에서 프로젝트 열기→Gradle Sync→app 실행. JDK17, SDK35/Build Tools36.1.0, Gradle8.13. 자동 검출은 실제20장과 대표 실기기 추가 검증 필요. UI 확인은 기기/에뮬레이터와 큰 글꼴·다크 화면을 구분해 기록. 미검증 항목을 확인한 것으로 보고하지 않는다.

## 문서

PRD.md: 요구·디자인·범위·인수 조건. docs/decision_log.md: 중요한 결정·상태. docs/environment.md: 환경 확인 범위. docs/verification.md: 실행한 검증과 미검증. 사소한 수정마다 모든 문서를 재독하거나 결정 항목을 생성하지 않는다.

사진·오버레이·인증정보는 Git에 넣지 않는다. 비밀값 입력을 요구하지 않는다. 정상 설정은 다시 요구하지 말고 새로운 오류가 있을 때만 확인한다. 도구 설치/연결 성공은 원격 실행 권한을 뜻하지 않는다.
