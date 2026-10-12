# 프로젝트 작업 지침

## 실행 범위와 판단 기준

현재 작업(2026-10-12)은 Issue #1의 파일·책임·패키지·명명·포맷 정리다. 사용자 승인 범위는 관련 코드·문서·포맷 설정·Issue/작업 브랜치·관련 검증·로컬 커밋까지다. Koin·자동 검출 구현/실험·원격 push·PR 생성/병합은 제외한다. 이후 작업은 실제 사용자 요청 범위를 따른다.

핵심 흐름 완성 > 기능 수, 사용성·정보 전달 > 장식. 구조를 단순하게 유지하고 플랫폼 기능을 우선한다. 데이터를 잃거나 기대값을 약화해 검증을 통과시키지 않는다. 사용자 진술·인터뷰 정정·사진 관찰·공식 설명·추정·실험·성과를 구분한다.

## 문서의 역할과 권위

- [PRD](PRD.md): 최신 사용자 결정·지원 범위·흐름·인수 기준·미결정 입력의 단일 기준.
- [자동 계수 설계](docs/auto_counting.md): 채택한 비AI 검출 접근과 기술 가정·검증 절차.
- [디자인·화면 명세](docs/design_spec.md): 공개 레퍼런스의 확인 범위, 화면 상태·디자인 초안.
- [코드 컨벤션](docs/code_conventions.md): 파일·책임·패키지·명명·포맷팅의 단일 기준.
- [Git 운영 규칙](docs/git_workflow.md): 브랜치·커밋·PR·병합 정책의 단일 기준.
- [결정 기록](docs/decision_log.md): 날짜·상태·대체 관계·선택 이유. 폐기된 제안을 활성 요구사항으로 재사용하지 않음.
- [환경 기록](docs/environment.md), [검증 이력](docs/verification.md): 확인 시점의 사실. 최신 요구사항이나 새 실행 결과를 대신하지 않음.

문서 충돌 시 최신 명시적 사용자 정정과 PRD를 따른다. 새 인터뷰·검증으로 방향이 바뀌면 영향 받는 부분과 결정 상태만 갱신한다. 사소한 작업마다 모든 문서 재독·결정 항목 생성·교차 검증을 강제하지 않는다.

## 코드 작성 기준

[코드 컨벤션](docs/code_conventions.md)을 따른다. 내부 화면 상태는 _uiState, 공개 읽기 전용 상태는 uiState. Job·저장 단계·토큰·ID·단위·좌표계가 역할을 드러내게 한다. 모든 private 변수에 _를 붙이지 않는다. 영속 JSON/key/컬럼·Navigation 인자를 이름 정리로 임의 변경하지 않는다.

화면/주요 ViewModel·Action·UiState는 이름에 맞는 파일, feature/home/count/record에 둔다. 공유 사진 UI는 데이터·callback을 받고 특정 화면 상태/dispatcher를 참조하지 않는다. data/core는 feature/navigation/di에 의존하지 않으며 빈 패키지·Base·불필요한 DTO를 만들지 않는다. 세부 배치/명명은 코드 컨벤션 참조. Koin 후속 작업도 같은 규칙을 따른다.

## Android·MVI 제약

Kotlin·Compose·단일 앱 모듈. UI/데이터 책임 분리, Repository 경유 데이터 접근, SSOT·UDF 유지. 화면 ViewModel은 읽기 전용 StateFlow<UiState>를 노출하고 Compose는 collectAsStateWithLifecycle로 수집한다. Action·명시적 불변 상태·처리 결과에 따른 상태 전환을 유지한다. 파일·DB·검출 부수 효과를 상태 전환과 구분한다.

생성자 주입을 기본으로 하고 별도 Store·MVI 프레임워크·DI 라이브러리·UseCase·모듈은 복잡도에 근거해 도입한다. ViewModel에 Activity나 UI 수명에 종속된 Context를 보관하지 않는다.

Navigation Compose의 타입 안전 경로로 목록·계수 작업·확정 기록을 분리한다. NavController는 UI 계층, 화면 ViewModel은 해당 back stack entry에 둔다. 경로에는 작업 ID만 전달하며 사진/표시는 Repository에서 조회한다. 사진 확인·분석·보정은 계수 화면 내부 상태다. Navigation 복원과 별도 전역 선택 작업 ID를 중복 관리하지 않는다. 이동 규칙은 [화면 명세](docs/design_spec.md#navigation-목적지와-이동-규칙)를 따른다.

일시 UI·복원·영속 데이터를 구분한다. 중요한 완료·오류 결과를 일회성 이벤트만으로 전달하지 않는다. 오래된 분석 결과·중복 완료·빠른 연속 보정·저장 실패·파일/DB 부분 완료를 검증한다. 전체 개수는 유효 표시에서 계산하며 자동 후보와 수동 보정을 구분한다.

## 자동 계수 선택과 적용 기준

사용자 채택 접근은 OpenCV 기반 원·타원 후보 생성 + 영역 정보 보완 + 단면 검증 + 중복 통합이다. 구현 세부의 기준은 [자동 계수 설계](docs/auto_counting.md). AI 모델·학습·외부 추론은 현재 범위에서 제외한다. 재제안하려면 전제 변화와 사용자 범위 변경을 명시한다.

한 묶음 촬영 조건을 유지한다. 배경 자르기는 선택 기능이며 여러 묶음 선택/분리가 필수라는 이전 제안은 대체됐다. 작은 파이프의 돌출·후퇴·겉보기 크기 차이는 검증 범위에 포함한다. 같은 평면·완벽한 원·규칙 격자를 가정하거나 숨은 개수를 추정하지 않는다.

현재 앱은 검증 대기 검출기를 사용하고 OpenCV는 androidTest 전용인 이전 구현 상태다. 이것은 향후 복합 검출 구현을 영구 금지하는 규칙이 아니다. 별도 구현 요청 후 승인된 범위에서 검출 구현·런타임 의존성을 검토하고, 합격 검증 없이 지원 성능을 확정하지 않는다. 단순 Hough/합성 원 테스트를 현장 성능으로 확대하지 않는다.

## Git 전략

상세 기준은 [Git 운영 규칙](docs/git_workflow.md). GitHub Flow의 main + codex/ 목적 브랜치를 사용한다. Git 운영 규칙 작성 당시 main과 저장된 origin/main은 db5490e로 같았고 origin 등록이 존재했다. 이번 구조 작업 기준은 e30d20c다. 원격 서버 최신 상태·보호 설정은 조회하지 않았다. 기존 D-08의 'main/원격 없음'은 당시 사실이며 이후 상태는 [D-13](docs/decision_log.md#d-13-git-운영-규칙과-템플릿)을 따른다.

- 같은 목적의 미병합 브랜치는 재사용, 완료된 브랜치는 최신 main에서 새 작업으로 시작한다. 이번 작업은 확인한 main e30d20c에서 codex/1-code-structure로 시작했다. Issue #1 생성 성공과 과거 권한 실패를 구분한다.
- Issue는 동일 작업을 먼저 확인하고 목적·범위·완료 조건·검증 계획을 기록한다. 선행 관계를 연결하며 Issue 생성만으로 구현/원격 작업을 허가받은 것으로 간주하지 않는다. 상세는 Git 운영 규칙과 두 Issue 템플릿 참조.
- 영어 type/scope + 한국어 설명의 Conventional Commits. 구체적 변경·실제 검증만 기록한다. 기존 이력을 컨벤션에 맞춰 재작성하지 않는다.
- 변경→관련 검증→diff 리뷰→이번 파일만 스테이징→로컬 커밋. 사용자 변경·사진·개인 경로·비밀값을 포함하지 않는다.
- 작업 브랜치 갱신은 허용된 원격 조회 후 origin/main 병합, PR 병합 기본은 Squash merge. 공유 이력 재작성/force push는 명시적 승인 없이 하지 않는다.
- push·PR 생성·병합·브랜치 삭제·배포는 각각 실제 요청 범위에 따라 수행한다. PR을 실제 생성하면 해당 작업에 링크를 연결한다. 문서/템플릿 작성은 원격 작업 허가가 아니다.

## 실행·검증 방법

문서 작업: 변경된 문서 재독, 로컬 링크·결정 상태·정정 대체·날짜·미검증 표시 확인, git diff --check, 코드 변경 없음과 스테이징 파일 확인. 문서만 바뀌면 앱 빌드·에뮬레이터·설치를 불필요하게 재실행하지 않는다.

구현을 요청받은 이후 관련 검증:

```sh
./gradlew spotlessCheck assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

## Jetpack Compose
For all Compose/Android UI tasks, follow the instructions in
`.compose-skill/skills/compose-expert/SKILL.md` and consult the reference
files in `.compose-skill/skills/compose-expert/references/` before answering.

자동 검출 실험이 제외된 요청에서는 connectedDebugAndroidTest 전체 실행 대신 허용된 테스트 클래스/메서드 목록을 명시해 Hough 프로토타입을 실행하지 않는다.

검출은 PRD의 독립 실사진·정답 위치·대표 실기기로 평가한다. 첫 분석/반복 분석과 전체 검수 시간을 구분한다. 회전/Activity 재생성, OS 프로세스 종료, 강제 종료 재실행을 서로 대신한 검증으로 보고하지 않는다. 미확인 항목은 최소 확인 절차와 함께 남긴다.

기존 빌드 설정은 환경 기록을 참고하고 필요한 시점에 가볍게 확인한다. 정상 설정을 반복 요구하지 않는다. 인증·권한·대상 변경이나 실제 오류가 있을 때만 재설정 안내. 이미지·오버레이·개인 경로·비밀값은 커밋하지 않는다. 선택 도구 연결은 전체 작업의 필수 선행 조건으로 만들지 않는다.
