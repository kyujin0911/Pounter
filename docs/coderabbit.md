# Pounter CodeRabbit 맞춤 리뷰 규칙

작성일: 2026-10-12. [.coderabbit.yaml](../.coderabbit.yaml)에 한국어 리뷰와 경로별 규칙을 정의한다.
기존 [프로젝트 지침](../AGENTS.md), [PRD](../PRD.md), [자동 계수 설계](auto_counting.md),
[화면 명세](design_spec.md) 및 현재 코드·테스트를 읽고 작성했다. 제품 결정은 해당 원문을 따른다.

## 리뷰의 초점

- 데이터 보존: 실패 보정 재시도, 확정 성공 시점, revision 충돌, 사진/DB 부분 완료와 삭제 복구.
- 상태와 수명: Action/불변 StateFlow, 생명주기 수집, 늦은 결과 차단, entry별 ViewModel, 복원 경계.
- 사진과 계수: EXIF·메모리·좌표 변환, 자동/수동 표시 구분, 인접 단면과 동심 테두리, 검증 게이트.
- 사용성: 입력 취소, 읽기 전용 기록, 오류 복구, 작은 화면·큰 글꼴·TalkBack 대체 조작.
- 구조와 근거: 단일 모듈·생성자 주입 우선, 필요에 근거한 확장, 정적 관찰과 실제 검증 구분.

각 지적은 발생 조건·사용자 영향·코드 근거·최소 수정과 관련 검증을 제시하도록 했다.
경로별 지침은 일반 지침을 보완한다. 현재 검증 대기 검출기와 테스트 전용 OpenCV를
향후 구현 금지 규칙으로 고정하지 않으며 최신 사용자 결정과 PRD를 우선한다.

## 설정과 적용 확인

공식 [경로별 리뷰 지침](https://docs.coderabbit.ai/configuration/path-instructions)과
[설정 참조](https://docs.coderabbit.ai/reference/configuration)에 따라 저장소 루트 설정을 작성했다.
chill 프로필은 균형 있는 피드백용이며, 심각한 데이터·상태 오류를 제외하는 설정은 아니다.
knowledge_base.code_guidelines에 권위 문서를 지정하고 과거 검증 이력은 최신 요구로 등록하지 않았다.

실제 적용에는 해당 저장소의 CodeRabbit 연결·리뷰 권한과 원격 설정 반영이 필요하다.
원격 반영 후 PR 댓글에서 아래 명령으로 유효 설정을 확인하고 경로별 리뷰를 점검한다.
명령의 용도는 공식 [리뷰 명령 참조](https://docs.coderabbit.ai/reference/review-commands#information-and-configuration)를 따른다.
연결/플랜/원격 유효 설정과 실제 리뷰 결과는 이번 로컬 작성으로 확인되지 않는다.

```text
@coderabbitai configuration
```

설정 규칙은 AI 리뷰 지침이며 컴파일러·Lint·테스트·브랜치 보호를 대신하거나 강제하지 않는다.
앱 변경 검증은 [프로젝트 실행 지침](../AGENTS.md#실행검증-방법)을 따르고,
문서/설정만 변경하면 YAML·공식 스키마·로컬 링크·git diff --check를 확인한다.
새 사용자 결정이 생기면 관련 경로의 지침만 함께 수정한다.

## 구조 정리 후 경로 — 2026-10-12

Issue #1의 패키지 이동에 맞춰 기존 UI 리뷰 경로를 feature/navigation으로 갱신하고
[코드 컨벤션](code_conventions.md)을 참조 문서에 추가했다. 새로운 리뷰 서비스/CI는
도입하지 않았으며 원격 설정·실제 PR 리뷰 적용은 미검증이다. 로컬 YAML과 경로 표본만 확인했다.
