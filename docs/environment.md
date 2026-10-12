# 환경 확인

확인일: 2026-10-12. 아래 런타임 확인은 이전 구현 단계의 이력이다. 문서 저장 단계에서는 Git 상태·파일·빌드 설정·검출기 연결만 읽기 확인했으며 SDK·기기·테스트를 재실행하지 않았다. 이후 Navigation 작업의 현재 확인은 아래 별도 절에 둔다. 로컬 개인 경로·현장 사진 파일명은 공유 문서에 기록하지 않는다.

최신 범위와 준비 상태는 [PRD](../PRD.md), 실행 지침은 [AGENTS](../AGENTS.md)를 따른다.

| 대상·범위 | 상태 | 확인 기능·제약 |
|---|---|---|
|Android Studio|설치 확인|앱 번들 존재. IDE 수동 Sync/Run은 별도 미검증|
|JDK17|검증 완료|Gradle 빌드 실행|
|Android SDK|검증 완료|플랫폼34/35/36, Build Tools34.0.0/36.1.0, ADB·emulator 존재|
|Gradle/Maven|검증 완료|공식 의존성 접근, Wrapper 빌드. 최초 다운로드는 네트워크 필요|
|ADB/AVD|검증 완료|제한 실행에서 조회 실패했으나 권한을 허용한 실행에서는 정상. Small_Phone(API36) 부팅 확인. 에뮬레이터 테스트8개 통과. SDK 재설정 불필요|
|Android 실기기|당시 미확인|이후 대표 기기 사용자 보고는 아래 참조. 실제 카메라·속도 검증 필요|
|Git|검증 완료(이번 읽기 확인)|기존 codex/pipe-counter에서 문서 브랜치 codex/pipe-counting-plan 생성. main/develop·원격 없음. GitHub Flow 채택 근거는 결정 기록 D-08|
|GitHub|미확인|대상 저장소 미지정. 로컬 구현에 필수 아님. push/PR/병합 권한으로 해석하지 않음|
|Figma|미확인|사용하지 않음. 현재 디자인·구현에 필수 아님|
|OpenCV Android|검증 완료(테스트 범위)|4.12.0 네이티브 로딩·합성 원12개 검출. androidTest 전용. 현장 계수 성능은 검증 대기|
|사진 자료|출처 구분 확인(대화)|초기 현장3장과 인터넷 참고2장을 구분. 추가 실제 정면사진·정답 위치·대표 실기기 필요. 이번 작업에서 사진 외부 전송/커밋 없음|

로컬 sdk.dir은 Git 제외 local.properties에 둔다. 비밀값·개인 정보는 저장하지 않는다.

## Navigation 작업의 환경 확인 — 2026-10-12

- 사용자 지정 대표 기기: 갤럭시 S22+, Android 16. 실제 모델/OS 조회·카메라·성능 확인과 구분한다.
- ADB 조회 당시 실기기 연결 없음. 기존 Small_Phone API 36 에뮬레이터를 실행하여 이번 관련 기기 테스트 15개를 수행했다.
- 기존 JDK 17·AGP 8.13.2·Gradle 8.13·Kotlin 2.1.21·compile/target 35·Compose 설정 유지. Navigation Compose 2.9.8, Kotlin serialization 플러그인 2.1.21와 JSON 1.8.1 추가. 불필요한 전체 업그레이드 없음.
- Navigation과 관련된 실제 빌드·테스트 결과는 [검증 이력](verification.md#navigation-화면-분리-검증--2026-10-12) 참조. 자동 검출 실험은 실행하지 않았다.
- 이번 시작점: codex/pipe-counting-plan의 d3ce1f9. 작업 브랜치 codex/navigation-flow. main/develop·원격은 만들지 않았다.
