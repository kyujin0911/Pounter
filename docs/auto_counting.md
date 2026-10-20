# 비AI 파이프 자동 계수 설계

갱신일: 2026-10-20. 상태: 접근 사용자 채택 / SUPPORTED_FACES_V1 앱 내 시험 도입 / 개발 사진 기준 미달 / 독립 현장 인수 미완료.

사용자·촬영 조건·범위·수치 인수 기준은 [PRD](../PRD.md)를 따른다. 2026-10-13 [자동 계수 상세 설계](superpowers/specs/2026-10-13-auto-counting-design.md) 이후 평가용 구현·실사진 검증을 진행했다. 2026-10-14에는 [개선 설계 개정 3](superpowers/specs/2026-10-13-auto-counting-improvement-design.md)과 [실행 계획](superpowers/plans/2026-10-13-auto-counting-improvement.md)에서 기존 알고리즘·순서를 재검토했다. 당시 2026-10-14 조사 요청의 범위는 조사/격리 재현·문서 개정이었으며 제품 코드·런타임 의존성·커밋·원격 작업 변경은 포함하지 않았다. 과거 실행은 [검증 이력](verification.md)의 시점별 기록과 구분한다. 이후 별도 요청으로 Task4의 EdgeDrawing 평가 경로를 Android에 구현하고 공통후처리 동등성과 실제 APK 격리를 확인했다. 그 Task4 시점에는 일반 앱 검증 대기 검출기·DI·저장 정책을 보존했다([Task4 결과](verification.md#task-4-edgedrawing-android-평가-경로--2026-10-14)). 이후 일반 앱 연결 상태는 아래 [SUPPORTED_FACES_V1 앱 내 시험 도입](#supported_faces_v1-앱-내-시험-도입--2026-10-20) 절을 따른다. 아래 날짜별 구현·평가 기록은 해당 시점의 사실이며 현장 인수 완료를 뜻하지 않는다.

## 선택과 대안 비교

OpenCV는 영상 처리 도구이며 라이브러리 도입만으로 자동 계수가 완성되지 않는다. 사용자가 채택한 비AI 접근은 원·타원 후보 생성 + 영역 정보 보완 + 단면 검증 + 중복 통합이다. 세부 생성기와 단계 구성은 재검토 가능한 기술 선택이다. 최신 개선 설계는 현재 윤곽 방식과 연결 arc 방식(EdgeDrawing)을 먼저 비교하고 영역 보완은 고유한 회수 이득이 있을 때만 추가한다. 아직 최종 알고리즘 교체가 승인·검증된 상태는 아니다.

| 접근 | 장점 | 현재 조건의 위험 | 역할 |
|---|---|---|---|
| Hough 원 검출 | 학습 자료 없이 단순하게 시작 | 이중 테두리·타원·반사·작은 밀집 단면 | 비교 기준·보조 후보 |
| 윤곽 추출 + 원/타원 피팅 | 정면과 약한 기울기의 경계 활용 | 경계 단절·서로 이어진 윤곽·부분 가림 | 주요 후보 생성 제안 |
| 밝기 분리 + 연결 영역 | 테두리가 약해도 내부 영역 활용 가능 | 내부 반사·밝은 입구·어두운 배경 | 보완 후보 제안 |
| 거리 변환 + Watershed | 붙은 내부 영역의 분리 후보 | 시작점 오류로 하나를 여러 개로 분리 | 실패 유형이 요구할 때만 추가 |
| 행·열/격자 규칙 | 일정한 배열의 검토 보조 | 끝이 불규칙하면 오판, 숨은 개수 추정 위험 | 자동 개수 생성에 사용하지 않음 |
| Kotlin 직접 구현/GPU 셰이더 | 필요한 기능만 구성 가능 | 영상 처리·최적화·기기별 검증 부담 | 현재 규모에서 비채택 |
| AI 객체 검출·분할·밀집 모델 | 학습한 특징·개별 위치 또는 마스크 활용 | 모델·학습 자료·비용/라이선스·통합 부담 | 비교 논의 이력. 최신 비AI 선택에서 제외 |

단순 Hough의 이전 실패는 복합 OpenCV 접근 전체의 불가능성을 입증하지 않는다. 보완 기능도 실제로 오류를 줄이는 경우에만 추가한다. 원·타원 피팅은 후보를 만들며 그 자체로 파이프임을 증명하지 않는다.

## 처리 절차 — 세부 구현 제안

1. 사진 방향 보정: EXIF 회전·뒤집기를 반영하고 원본 갤러리는 유지한다.
2. 분석 영역 준비: 한 묶음 전체를 기본으로 한다. 필요하면 사용자가 배경을 자른다. 영역 좌표와 이미지 방향의 기준을 고정한다.
3. 밝기·경계 준비: 그레이스케일·제한적인 잡음 제거를 시험한다. 국소 대비 보정(CLAHE 등)은 반사/바닥 잡음 증가를 함께 비교하고 필수 단계로 고정하지 않는다.
4. 후보 생성: 비교로 선택한 원·타원 생성기의 출처·중심·크기·경계 근거를 유지한다. 밝기 영역 보완은 누락 회수 이득이 있을 때만 추가한다. 여러 마스크에서 나온 후보를 통계적으로 독립된 증거로 간주하지 않는다. 필요하면 Hough와 별도로 비교한다.
5. 단면 검증: 둘레의 경계 지지, 원/타원 적합도, 크기 범위, 주변 대비, 가림 상태, 다른 후보와의 관계를 평가한다. 단면 내부가 언제나 어둡다고 가정하거나 완벽한 경계를 필수 조건으로 삼지 않는다.
6. 중복 통합: 같은 중심의 안/밖 테두리와 중복 처리 결과를 대응한다. 근접 거리만으로 인접 파이프를 합치지 않고 크기·겹침·경계 위치를 함께 확인한다.
7. 좌표 복원: 후보를 방향 보정된 사진의 정규화 좌표로 반환한다. 영역·축소·여백·분할 좌표와 UI 확대/이동 좌표를 구분한다.
8. 검토·보정: 자동 후보를 사진 위에 표시하고 사용자의 추가·제외를 별도로 반영한다. 최종 개수는 유효 표시 수에서 계산한다.

파라미터·가중치·필수 단계는 튜닝 자료로 정해 고정한 뒤 독립 자료로 검증한다. 검증 결과를 보고 다시 조정했다면 해당 자료는 튜닝 자료가 되며 새 독립 자료로 재평가한다. 사진마다 개발자가 임계값을 조절해야 성공하는 결과를 자동 계수 합격으로 취급하지 않는다.

## 해상도·분할·기하학의 제약

- 분석 축소로 가장 작은 단면의 특징을 없애지 않는다. 최소 단면 픽셀 크기·반경/축 비율 범위는 실사진으로 정한다.
- 검토용 사진과 분석 입력을 분리한다. 큰 사진을 여러 Bitmap/Mat으로 복제하지 않고 필요한 영역·조각을 처리하며 네이티브 자원을 해제한다.
- 먼저 단일 영역 분석을 검증한다. 작은 단면의 해상도가 실제 오류 원인일 때만 겹치는 분할 분석을 도입하고 동일 단면 중복·조각 경계 누락·시간 증가를 평가한다. 모든 사진에 분할을 기본 적용하지 않는다.
- 돌출·후퇴로 서로 다른 깊이의 단면이 존재한다. 한 평면의 원근 보정으로 모든 단면을 원으로 복원할 수 있다고 전제하지 않는다.
- 같은 물리 지름이라도 겉보기 크기가 달라질 수 있다. 크기 필터가 들어간 단면을 제거하지 않는지 별도 확인한다.
- 기준 단면 한 개 선택/지름 입력은 기본 사용자 흐름이 아니다. 필요해지면 효과와 추가 조작 비용을 설명하고 사용자와 결정한다.
- 완전히 숨은 단면을 배열 규칙으로 추가하지 않는다. 다른 각도의 여러 사진을 자동 합산하지 않는다.

## 출력·보정·상태 계약

공개 서버 API는 없다. 내부 PipeDetector는 작업의 사진·영역·처리 버전을 받아 위치별 후보·처리 시간·경고/실패를 반환한다. 구체적인 타입은 기존 코드와 호환성 확인 후 결정하며 이 문서는 코드 인터페이스 변경을 수행하지 않는다.

후보는 안정적인 ID, 정규화 중심, 후보 크기/형태, 생성 근거·점수를 설명할 수 있어야 한다. 기하학 점수를 AI 확률이나 '계수 정확도 %'로 표시하지 않는다. 내부 후보 상한에 도달하면 포화 상태를 알리고 300개로 조용히 잘라내지 않는다.

자동 결과와 사용자 추가·제외·되돌리기를 보존한다. 재분석에서는 새 자동 결과가 사용자 보정을 덮어쓸 수 있으므로 PRD의 확인·저장 정책을 따른다. 검출 강도 슬라이더는 미채택 후보이며 화면 표시 모양 변경은 분석 재실행과 구분한다.

## Android 책임과 생명주기

| 구성 | 책임 |
|---|---|
| 검출 구현체 | 전처리·OpenCV 연산·후보 검증/통합·좌표 복원. UI 의존성 없음 |
| CountRepository | 사진 접근·작업/결과/보정 저장·복원·삭제, 파일/DB 부분 완료 복구 |
| 화면 ViewModel | Action 처리·비동기 작업 관리·불변 UiState 전환·오래된 결과 차단 |
| Compose | 생명주기 기반 상태 수집·사진/표시 렌더링·입력 전달 |

상세 MVI 제약은 [AGENTS](../AGENTS.md). 사진 입출력과 연산은 메인 스레드를 막지 않는다. 한 번에 한 분석을 실행하고 작업 ID·영역 revision으로 결과 유효성을 확인한다. 코루틴 취소가 네이티브 연산을 즉시 중단한다고 가정하지 않으며 취소 후 결과 반영을 막는다. 짧은 전경 분석에 불필요한 백그라운드 실행 체계를 도입하지 않는다.

기존 앱/기록을 이어서 수정하게 되는 경우 사진 형식·좌표·DB 버전의 호환성과 기존 기록 보존을 먼저 확인한다. 스키마가 바뀌면 명시적으로 마이그레이션하고 사용자 기록을 임의 초기화하지 않는다.

## 검증 순서와 진행·중단 기준

1. 실사진의 정답 단면과 지원 조건을 먼저 작성한다. 출처 구분과 데이터 분리는 PRD 기준.
2. Hough 기준선과 윤곽/타원 후보를 같은 자료에서 비교한다.
3. 반사·누락·연결 영역·중복 등 실제 오류에 필요한 보완만 추가한다.
4. 고정된 처리로 독립 정렬/불규칙 작은 파이프군을 각각 평가한다.
5. 대표 Android 실기기에서 좌표·메모리·초기화/처리 시간·취소·보정 흐름을 확인한다.
6. 누락+오검출, 보정 후 정답, 전체 검수 시간, 앱 품질을 PRD 인수 조건으로 판정한다.

조건을 통과하면 검증한 지원 범위를 명시한다. 정렬 묶음만 통과했으면 불규칙 작은 파이프까지 완료됐다고 보고하지 않는다. 기준 미충족 시 오류 사례와 개선 가능성을 제시하고 재촬영/조건 변경/다른 접근을 사용자와 결정한다. 수동 보정만 가능한 앱은 자동 계수 MVP 미완료다.

## 비용·근거

OpenCV 4.5 이상은 Apache 2.0 라이선스다. 라이선스 조건을 지키는 전제에서 라이브러리 구매비·사진당 API 요금·학습 GPU·모델 구독을 요구하지 않는다. 개발/튜닝/정답 확인 시간과 기존 장비·전기 비용은 무료 라이브러리와 별개다. 이 계획에 과거 AI 학습용 GPU 예산 제안을 포함하지 않는다.

공식 근거(2026-10-12 조사):

- [OpenCV 라이선스](https://opencv.org/license/)
- [Hough 원 검출](https://docs.opencv.org/4.13.0/d4/d70/tutorial_hough_circle.html)
- [윤곽·타원 등 형태 분석](https://docs.opencv.org/4.13.0/d3/dc0/group__imgproc__shape.html)
- [Watershed](https://docs.opencv.org/4.13.0/d3/db4/tutorial_py_watershed.html)
- [밝기/CLAHE](https://docs.opencv.org/4.13.0/d5/daf/tutorial_py_histogram_equalization.html)

공식 기능의 존재는 파이프 검출 성능의 근거가 아니다.

## 평가 구현과 외부 정답 규약 — 2026-10-13

사용자가 상세 설계와 계획의 직접 실행을 승인했다. `feat/auto-counting`은 계획 커밋 `b1cd809`에서 시작하며 미병합 Koin `478cb50`과 해당 문서 이력에 의존한다. 현재 로컬 main은 `04cd258`, 공통 조상은 `64f4700`이다. 원격 최신 상태는 조회하지 않았다.

당시 2026-10-13의 복합 검출기는 androidTest에만 있었다. 일반 앱의 `ValidationRequiredDetector`, `DETECTOR_VALIDATED=false`, OpenCV 테스트 의존성은 그 시점에 유지했다. 메타데이터·좌표 복원·분석 입력·저장 대기 계약은 main 코드에 구현했다. Room v1과 기존 JSON/복원 key를 유지한다.

평가 처리 버전 `synthetic-v1`은 합성 기하학 검사 전용이다. 분석 긴 변 1600px, 픽셀 예산 4,000,000, 후보 상한 10,000, median 3, Canny 40/90, 국소 평균 block 31/offset 4, 짧은 반축 최소 7px·긴 반축 최대 80px, 축비 2.5, 경계 지지 .65, fit error .15, 대비 최소 0, 통합 정책 .3/.5/.85를 사용했다. 대비 최소 0은 합성 시험의 선택이며 현장 단면 검증값이 아니다. 고정 현장 설정은 아직 없다. API36 ARM 에뮬레이터의 Gaussian adaptiveThreshold 내부 float 변환에서 SIGILL이 발생해 같은 양극성 국소 평균 분리로 변경했다. 실제 S22+ 성능·메모리는 미검증이다.

### 입력과 실행

`DetectionEvaluationTest#evaluateTuningSet`와 `DetectionAcceptanceTest#evaluateIndependentSet`에 instrumentation 인자 `evaluationManifest`, `evaluationConfig`를 전달한다. 둘 다 앱 프로세스가 읽을 수 있는 **기기 로컬** 파일 경로다. 사진 경로는 manifest 폴더 기준 상대 경로이며 폴더 밖으로 이탈하면 거부한다. 실제 전송/권한은 현장 실행 환경에서 확인한다. 사진·정답·설정 원본·오버레이·평가 원본 결과는 Git에 넣지 않는다. 인자가 없으면 테스트는 현장 검증 대기를 이유로 skip한다.

설정 JSON에는 `detectorVersion`, `frozen`과 `DetectionConfig`의 모든 수치 필드 및 `mergePolicy`의 세 필드를 명시한다. 기본 수치를 자동 보충하지 않는다. 파일 바이트의 SHA-256을 manifest의 `configSha256`과 비교한다. 고정 후 파일의 공백 변경도 해시를 바꾸므로 고정 원본 파일을 보존한다. independent에서는 frozen=true와 삼성 SM-S906 계열 / SDK36을 요구한다. 대표 기기 여부는 현장에서도 사용자 제공 모델·OS와 함께 재확인한다.

manifest JSON 규약:

- 최상위: `schemaVersion: 1`, `groundTruthFrozen: true`, `configSha256`, `samples`.
- 각 sample: 비식별 `sampleId`, `sessionId`, `bundleId`, `split`(reference/tuning/independent), `supportGroup` 문자열 배열, `photo` 상대 경로.
- `region: [left, top, right, bottom]`: 방향 보정된 전체 사진의 정규화 좌표. 전체 한 묶음은 [0,0,1,1]. 자르기는 선택이며 사진 배경으로 강제하지 않는다.
- `expected: [{id, x, y}]`: 같은 사진의 정규화 단면 중심. 사진 너비/높이로 각각 나눈 좌표. ID는 사진 안에서 유일. 노란 단면·금속색 단면 모두 포함하고, 돌출/후퇴한 작은 단면을 지원 밖으로 일괄 제외하지 않는다.
- `normalizedTolerance`: 결과 보기 전에 고정한 x/y 정규화 유클리드 거리. 좌표 방향은 가져오기 EXIF 적용 후를 기준으로 정답을 작성한다. 가로/세로 픽셀 거리와 같은 수치로 취급하지 않는다.
- `excluded: [{id, x, y, reason}]`: 주변 묶음·판단 불가능한 가림·잘린 대상 등의 위치와 사유를 기록. 제외 기록은 검출 결과를 임의 삭제하는 필터가 아니다. 선택 영역 안의 비대상 검출은 오검출로 남는다.
- `zeroFalsePositiveAllowance`: 실제 0개 사진의 허용 오검출 수, 평가 전 고정. N>0에서는 `max(1, floor(N*0.05))`를 사용한다. N=10/20/300이면 1/1/15.

촬영 세션 또는 같은 묶음을 reference/tuning과 independent 양쪽에 배치하면 거부한다. 참고 사진 두 장은 reference/tuning으로만 관리한다. 새로운 독립 촬영 자료의 독립성은 담당자가 확인하고 manifest에 기록한다. 단순 ID 변경은 독립성 근거가 아니다.

### 출력과 판정 경계

일대일 대응은 거리·ID 순서의 augmenting path로 최대 대응 수를 구한다. 합계가 같아도 누락/오검출을 별도로 센다. 삭제 후 추가는 2회다. 사진마다 expected/matched/missing/falsePositive/보정량과 반복 결과를 기록한다. 처리 실패는 0개로 바꾸지 않으며 failureReason으로 기록한다. 형태/영역 후보 수는 중복 통합 전의 기여량이며 정확도가 아니다.

첫 검출 함수 호출·반복 호출 시간(호출 잠금 대기 포함), 해당 호출의 초기화 시간, 원본/가져온/분석 입력 치수, native heap 전후 스냅샷을 출력한다. heap 전후값은 peak 메모리가 아니다. tuning에서는 기존 Hough 위치 대응과 픽셀 예산 안의 방향 보정 원본 전체 해상도 비교를 제공한다. 원본이 예산을 넘으면 비교 대기 사유를 남기며 몰래 축소하지 않는다. 고정 임계값을 원본 전체 해상도에도 사용하므로 단면 픽셀 크기 범위 차이까지 함께 해석해야 한다. 사진별 임계값 조정은 하지 않는다.

이 자동 평가만으로 현장 인수를 완료하지 않는다. 저장/화면 반영을 포함한 표시 생성 5초, 대표 기기의 메모리 peak·취소/재시도, 사용자 보정 후 정답 일치, 촬영부터 재촬영까지 전체 검수 시간·최종 오류·포기, 첫 사용 후 합의한 개선 폭/반복 횟수, Activity 재생성/OS 프로세스 종료/force-stop의 별도 검증이 남아 있다. 독립 자료를 보고 튜닝하면 새 처리 버전과 새 독립 자료가 필요하다. 이 근거가 없으므로 Task 7은 진행하지 않는다.

## 2026-10-16 현재 평가 구현

`edge-drawing-common-android-v2-even-sampling`은 native 홀수 표본의 미기록 끝 슬롯 읽기를 수정한 평가 전용 버전이다. 사진10행의 raw/위치 지표는 이전과 같아 제품 목표는 미달이다. a상한 제거·aperture 중심veto 완화·Bookstein 대체fit을 별축으로 비교해 보류/불채택했다. 기본FPF·a<=80·quality/object=null을 유지한다. 과거Task 순서는 향후 병목 검증의 영구 제약이 아니다. 일반 앱 연결·truth/t/frozen 변경·현장 승인은 실행하지 않았다. 세부 새 실행과 재사용 경계는 [검증 이력](verification.md)의 2026-10-16 절을 참조한다.


### Native 원본→segment→arc→fit 진단 — 2026-10-16

문제4개+matched2개를 실제 Android standalone trace와 원본으로 연결했다. metal37 028은 아래rim segment4/5의80/98점 짧은 원호와 위rim segment6/10의118/136점 타원호가 생성되지만, 서로 다른 segment의 JoinArcs3 원반경 proxy 조건 `diff > minR`로 결합되지 않고 각 coverage(.129/.165/.342/.440)로 후보 등록에서 탈락한다. 위 두 원호끼리의 overlap 거절은 실제 지지점도 위쪽에 겹쳐 임의 제거하지 않았다. 018/034는 rim을 따르는 유효 raw fit과 DRAFT 중심 차이를 분리했고 metal40 020은 부분 원 fit이 반대 경계를 가로지르는 반례다. 주석/t 변경과 물리 미생성 확정은 없다.

`scripts/native_stage_trace.py`는 정답 없이 현재 source의 외부 진단 복사본을 생성한다. 앱/native 기준선은 v2-even-sampling FPF·a<=80·quality/object=null을 유지한다. 최종 기존 gray3행 on/off raw 동일, 새 PhotoStore/JNI3행 marker·상태·metric 동일/U/L/A/R0. 검출 정책 수정은 연결 안전성 근거가 부족해 보류했고, 다음 판별은 알려진 단일 타원의 상·하 분절과 인접 두rim 합성에서 원반경 proxy의 오류/정상 거절을 구분하는 것이다.

관련 새 빌드/포맷/Lint·JVM59·Android native/계약12와 선택 JNI시험1, 소유권1641/liveDelta0을 확인했다. 임시 runner 개수 assertion/포맷 실패와 수정 후 재실행을 구분한다. 임시 runner는 외부에 보존하고 repo에서는 제거한다. 독립 코드 검토·Join support 소실 제한·standalone RSS/시간 한계·새/과거 재사용 범위는 [검증 기록](verification.md#native-단계-연결-진단--2026-10-16)과 이번 외부 native-trace-v1 REPORT/final-verification에 남긴다. 제품/정답/현장 인수와 커밋·원격 작업 없음.


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


## 2026-10-19 정면 cap 우선 개발 실험 — 미채택

사용자 촬영 예상에 따라 cap-151/cap-55 full을 주 목표로 고정하고 기존5장 full/crop10행을 유지했다. 축/각도별 차이 대신 경계 RMS를 사용하는 H1은 기존 중심 gate를 넘는 실제 사례에 적용되지 않아 새 관계0개로 구현 전에 중단했다. 동일 Canny 연결 성분의 국소 영상 지지가 있는 반복 fit을 통합하는 H2는 합성 RED1/4→GREEN4/4를 확인했지만 실제 사진10행의 후보·대표·source·marker·지표를 바꾸지 않았다. 내/외곽 통합 Task6 ALL/90은 여전히 보류하며 새 실험과 혼동하지 않는다.

실제 PhotoStore/native/JNI baseline/candidate20회에서 cap151 full M151/FN0/FP237/C237/COUNT_LIMIT, cap55 full M54/FN1/FP39/C40/SUCCESS(93개)가 그대로였다. cap151의388개는 실패 이전 진단 후보이며 정상 반환값이 아니다. metal40 M37/FN3/FP38/C41, metal18 M18/FN0/FP7/C7, metal37 M19/FN18/FP14/C32도 동일했다. 정답/b/u/t는 v6 DRAFT/frozen=false다. 각 cap의 보정량이 PRD 허용7/2회를 초과하므로 개선 완료로 표시하지 않는다.

H2는 효과 없이 후처리 시간이 늘어 미채택했다. 실험 patch/runner/RED·GREEN/원본 연결 진단표본16개/비교표는 외부 `cap-quality-2026-10-19`에 보존하고 이번 검출 소스 변경만 복원했다. 일반 앱 검증 대기 상태를 유지한다. 새 인터넷3장은 위치·범위 매핑 미준비로 실행하지 않았다. 다음 가설은 단일 rim의 반복 fit보다 캡의 실제 내/외곽 객체 관계를 다뤄야 하며 이웃·돌출/후퇴·다중 aperture 반례를 보존해야 한다. 다른 묶음의 실제 작은 단면과 평가 범위 밖 후보도 물리적 가짜로 단정하지 않는다.


최종 복원 후 첫 회귀18개 중1개가 PHOTO_READ로 실패했다. PhotoStore bounds decode 실패와 앱 시작 finishDeletes/removeOrphans가 등록되지 않은 테스트 JPG와 같은 photos 폴더를 사용함을 확인했다. 실행 시점의 삭제 호출 자체를 추적한 것은 아니므로 경합은 원인 가설로 구분한다. 테스트 fixture만 ContextWrapper의 고유 cache 하위 filesDir로 격리하여 정리 경로 공유를 구조적으로 제거했다. 같은 실패 클래스 순서로 재설치·재실행한18개가 모두 통과했다. 첫 실패 로그를 보존했고 반복 성공을 최초 무실패로 바꾸지 않는다. 제품 PhotoStore/Repository/DI와 검출 로직은 미수정이며 이 테스트 보완은 계수 정확도 개선이 아니다. 최종 포맷/build/testAPK/Lint·diff check와 graphify AST update를 완료했다. JVM task는 여전히 재사용이다.

## cap 객체 영역 소유권 사전 실험과 탈락 — 2026-10-19

직전 cap 실험의 소스150개와 원본/gray/rows 해시가 일치해 실제 Android 기준선을 재사용했다. 이번 새 가설은 내·외곽 경계가 같은 닫힌 밝은 face의 외부와 유일한 내부 hole에 속하는지 확인하는 것이다. 기존16표본과 추가7개 원본 사례를 검토했고, 정답을 입력에 쓰지 않는 외부 고정gray 사전 probe에서 cap151 full/crop 각각12개 통합 관계가 생겼다. 예비 위치 매칭은 full M151/FN0/FP225/C225, crop M151/FN0/FP154/C154였으며 cap55/금속은 불변이었다. 이는 새 Android 결과나 채택 성과가 아니다.

붙은 annuli의 bright 영역 연결 때문에4개 표시가 그대로 남아 요구2개를 충족하지 못했고, 실제 두hole+parent도3개로 남았다. 필수 반례를 완화하지 않고 가설을 Kotlin 구현 전에 탈락시켰다. cap151 예비376개도 COUNT_LIMIT이며 일반 검출은 기존 진단388개/FP237 그대로다. cap55는 실제93개/FN1/FP39/C40 기준선을 유지한다. 객체 영역 위상만으로 동일 파이프를 확정하는 정책을 채택하지 않았다. 기존 same-rim 반복과 원호/fit 정책은 변경하지 않았다.

외부 probe 외 검출·테스트 코드 변경이 없어 새 Android/빌드/설치는 실행하지 않았다. 기존 fixture 격리 보완·일반 앱 검증 대기·v6 DRAFT/frozen=false·인수 기준을 보존했다. [상세 검증](verification.md#cap-객체-영역-소유권-사전-실험--2026-10-19)을 따른다.

### 법선·극성 관측의 한계 — 2026-10-19

고정 raw1,231개(full/crop 원본5장)에 외향 타원 법선 방향 밝기차·gradient 정렬·관측 구간을 새로 측정했다. 일부 cap 내외곽은 반대 부호로 구분되지만 반사·부분 가림·capless는 UNKNOWN이고 밝은 내부는 부호가 뒤집힌다. 극성은 물리 입구 역할·파이프 동일성·edge 소유권이 아니다. UNKNOWN/외곽만 있는 정상 단면을 삭제하거나 parent를 포함만으로 지우는 정책으로 승격하지 않았다. 새 객체 선택/계수 개선은 없으며 제품 검증 대기를 유지한다.

cap55 raw의 작은 입구형13후보가 b<7 gate로 사라지는 새 실패군도 확인했다. 위치 coverage raw54→quality41이지만 외곽이 남아 전체 coverage54는 유지된다. 이는 새 중심 생성 이전에 후보 표현/객체 입력에서 작은 입구를 보존할 필요성을 별도 검토할 근거이며 gate 완화 채택은 아니다. 역할 판정과 위치정답은 분리했다. 상세 비교·원본 관측·실행/미실행 범위는 [검증 이력](verification.md#법선극성-기반-경계-역할-관측--2026-10-19)을 따른다.


## 중심·영상 근거 객체 선택 P2-v2 — 2026-10-19~20

명시적 실행 요청에 따라 평가 전용 `radialObjects=true` 경로를 추가했다. 표준 OpenCV SimpleBlobDetector의 다중 threshold 영역 생성 P1은 밝은 내부 보완 후에도 부분 가림 대조에 필요한 중심을 만들지 못했다. 두 번째 생성기 P2는 부호 있는 gradient의 방사 대칭 중심을 만들고, 기존 영상 지지 경계 사이의 면/외부 밝기 표본으로 객체를 연결한다. 최대 두 생성기만 비교했으며 P1과 탈락 설정은 저장소 밖 `region-object-v1` 증빙에 남겼다.

선택된 P2-v2는 공통 response 20, coherent/exterior 47/72, filled 65/72로 고정한다. 기존 경계의 순위와 대표 위치를 유지하며 출처만 통합한다. 사진명·정답·목표 개수는 검출 입력이 아니다. parent는 실제 threshold contour와 대응하고, 서로 다른 threshold 두 개에서 제거 후에도 남는 독립 자식 경계 두 개가 확인될 때만 제거한다. 리뷰에서 parent가 자신의 자식 근거를 공급해 외곽만 유효한 단면을 지우는 결함을 재현한 뒤 수정했다. 기존 same-rim 보존 테스트의 입력·기대값은 그대로다.

실제 PhotoStore/JNI 5개 원본×full/crop에서 기존 대응 target lost/gained 모두 0, 모든 FN/FP 비증가다. full 결과 M/FN/FP/C는 cap151 151/0/108/108, cap55 54/1/22/23, metal40 37/3/38/41, metal18 18/0/7/7, metal37 19/18/14/32다. ANNULUS_90 대비 cap151 full/crop FP 13개씩, cap55 full/crop 1개씩 더 줄었다. 실제 두 입구+parent 합성의 객체 결과는 ANNULUS_90 3개, P2-v2 2개이며 두 입구 각각 대응한다. 합성은 기존 경계 입력으로 객체 선택을 검증한 것이며 작은 경계의 native 생성·크기 gate 통과를 증명하지 않는다.

개발 개선 최소 기준은 통과했고 선택 가능한 평가 경로로 유지할 것을 추천한다. 처리 비용은 증가했다. 최종 에뮬레이터 cap151 full 호출은 첫 3802ms/반복 2704ms, ANNULUS_90 141/157ms였다. 반복 수가 작고 S22+ 실기기나 앱 표시 완료 시간을 측정한 것이 아니다. 이 방법은 standalone 중심 추가나 크기 gate 완화를 하지 않아 metal37의 기존 생성/크기 손실과 cap55 위치 허용치 밖의 대응은 남는다.

제품 인수는 미완료다. v6 DRAFT/frozen=false, 원본 5장이라는 개발 범위와 PRD 보정 허용량을 유지한다. 일반 앱은 ValidationRequiredDetector 및 DETECTOR_VALIDATED=false, OpenCV는 androidTest 전용이다. 기본 평가 옵션도 false다. 전체 10행·원본/전후 오버레이·설정별 실패·소스/입력 해시·실행 명령은 외부 `region-object-v1/REPORT.md`에 기록했다. 새 실행 범위는 [검증 이력](verification.md#중심영상-근거-객체-선택-p2-v2--2026-10-1920)을 따른다.


## 2026-10-20 — 국소 지지 경계 객체 선택 평가

평가 전용 `localObjects=true` 경로를 추가했다. 전역 FRST 대신 기존 지지 경계를 고정 anchor로 묶고 영상의 극성 독립 band/exterior 증거와 기존 독립 자식 parent 검증을 사용한다. P2-v2는 기존 `radialObjects=true`로 유지한다. local 중심 한계 .3×inner b와 축비 .25~.9이며 same-rim 기대값·기본 7/80 gate·support .65·후보/개수 예산은 유지했다. 일반 앱 DI·런타임 의존성·DETECTOR_VALIDATED 변경은 없다.

고정 원본5장×full/crop10행의 실제 PhotoStore/JNI에서 P2 대비 cap151 FP108/37→106/35, cap55 FP22/23→19/19, FN·금속6행·정상 대응 손실0을 확인했다. 26공통 설정의 유한 탐색에서 크기 gate 복구12설정과 Canny/threshold contour+standard/AMS fit8설정은 FP 비증가 조건을 실패해 채택하지 않았다. 원본5장·v6 DRAFT/frozen=false 개발 선택이며 독립 현장 인수가 아니다. PRD 목표는 전10행 미달이다.


## 2026-10-20 — LOCAL 이후 edge 출처 공유 평가

평가 전용 `localObjects=true, sharedEdges=true`를 추가했다. 실제 Canny edge의2px ray거리/30도 normal 지지 출처를 공유하는 fit을 고정 anchor로 통합한다. 기존 LOCAL·same-rim·크기 gate·제품 DI·런타임 의존성·DETECTOR_VALIDATED는 유지한다. 기하상 비교 가능한 후보만 계산하되 prefilter 작업 예산을 소진하면 전체 계산으로 돌아가 기존 실제 병합 실패 예산을 보존한다.

원본5장×full/crop의 새 PhotoStore/JNI 비교에서 LOCAL 대비 cap151 FP106/35→105/34, FN0 유지, 나머지8행 동일·정상 target 손실0·중심이동0이다. 같은 물리적 입구1곳의 중복 제거이며 PRD 목표는 전10행 미달이다. AAMED 저자 구현은 저장소 밖 GPLv2 격리 실행으로40조건을 시험했고, 후속 선택에서metal37 FN18→12를 회수했으나 FP14→16이 남아 안전 경로로 채택하지 않았다. 새 코드는 평가 선택지이며 일반 앱 연결/외부 코드 취득은 제품 배포 승인이 아니다. v6 DRAFT/frozen=false와 독립 인수 미완료를 유지한다. 상세 자료는 저장소 밖 pipe-improvement-v2/REPORT.md에 보존한다.

### SHARED 이후 연속 면 선택 평가 — 2026-10-20

평가 전용 `EdgeDrawingEvaluationDetector(localObjects=true, sharedEdges=true, continuousFaces=true)` / `processBGeometry`에 `CONTINUOUS_FACES_V1`을 추가했다. 기존 SHARED·LOCAL과 기본 false를 보존한다. 내·외곽 후보의 공통 영상 방향에서 경계 사이를 실제 1px 이하 간격으로 샘플링하고, 72구간 중 연속성47·내측 동일 방향 대비47·외측 절대 대비24를 요구한다. 면 전체 밝기 균일성이나 내·외곽의 동일 edge 출처를 요구하지 않는다. 후보 거리/축비는 제안 조건이며 영상 관계 없이 합치지 않는다. 원래 anchor와 중심을 유지하고 sourceIds만 합친다. 독립된 다른 입구의 중심은 소유권을 거부하며, 고정 anchor·단계별 명시적 쌍 예산 실패·취소를 유지한다. 타원 로컬 각도 대신 공통 영상 방향의 정확한 타원 교점을 사용한다.

원본5장×full/고정crop의 v6 DRAFT/frozen=false 개발 평가에서 SHARED 대비 cap151 FN0 유지·FP105→91/34→21, cap55 FN1 유지·FP19→7/19→6을 확인했다. 금속6행은 동일하고 기존 대응 손실0이다. full/crop을 중복 집계하지 않은 개선 입구는 cap151 14곳·cap55 13곳이다. 평가 경로로 채택하지만 PRD 전행 보정량·실기기5초·독립 현장 인수는 미달/미검증이다. 제품 DI·런타임 의존성·`DETECTOR_VALIDATED=false`를 바꾸지 않는다. 상세는 아래 검증 이력의 같은 날짜 절과 세션 산출물 `pipe-improvement-v3/REPORT.md`를 따른다.


### SUPPORTED_FACES_V1 평가 경로 — 2026-10-20

`EdgeDrawingEvaluationDetector(localObjects=true, sharedEdges=true, supportedFaces=true)`는 SHARED 이후 CONTINUOUS 대신 실제 지지 outer edge 구간의 영상 관계를 선택하는 androidTest 전용 경로다. `continuousFaces`와 상호배타이며 기존 LOCAL/SHARED/CONTINUOUS는 보존한다. 전체72 방향에서 Canny/normal로 독립 추출한 지지 구간 중 연속 band·내측 동일 극성·외측 절대 대비를 함께 만족하는24구간 이상, 최소3분면(각3구간 이상), 지지되지만 연속성이 없는 구간24개 이하를 요구한다. 누락 구간은 분모에서 빼지 않는다. 고정 원래 anchor/source union과 독립 중심 veto를 유지한다.

원본5장 full/고정crop10행 v6 DRAFT/frozen=false 개발 평가에서 CONTINUOUS 대비 cap151 C full91→90/crop21→19, cap55 full8→5/crop7→3. 전체10행 FN/FP 비증가·정상 대응 손실0·새 대응0·중심 이동0이며 금속6행은 같다. 변경10view는 실제6입구(정답 t 내 대응5입구 + cap55target043의 t 밖1입구)다. target043 최근접 raw5.529621px/t5는 그대로이고 중복 제거를 FN 회수로 세지 않는다. 배경 묶음과 시계는 별도 잔여 원인이다.

API36 arm64 에뮬레이터에서7방법×10행, 조건마다 첫 호출+반복3회로 실제 PhotoStore→native/JNI→Marker280회. 새 반복 중앙28.2~271.6ms, CONTINUOUS 대비+0.2~29.7ms. 특징 준비 비용을 포함하며 실기기5초·cold 전체 지연·UI/저장·검수시간·peak memory·현장 인수는 미검증이다. 평가 경로 제한 채택이며 일반 앱 DI/런타임 의존성/DETECTOR_VALIDATED는 변경하지 않는다. 안전 개발 결과를 제품 인수로 확대하지 않는다.


## SUPPORTED_FACES_V1 앱 내 시험 도입 — 2026-10-20

이전 평가 전용 제한 이후 최신 사용자 요청으로 일반 앱 통합을 승인했다. `AppModule`은 `SupportedFacesPipeDetector(PhotoStore)`를 singleton으로 연결한다. OpenCV 4.12.0은 일반 implementation, native 원본·라이선스·소유권 helper는 main/cpp/ed로 옮겼다. 단일 앱 모듈·Repository·ViewModel·영속 형식은 유지한다. 정확도 인수 플래그는 false다.

고정 경로는 공통 raw/gate/support/same-rim → LOCAL → SHARED → SUPPORTED이며 CONTINUOUS를 통과시키지 않는다. 임계값·후보10000/쌍1000000 예산·최종300 상한·고정 anchor/source union·ROI 복원을 보존했다. 입력 준비는 이전 synthetic 입력 정책과 같은 최대변1600/디코드4000000px/minROI31px이다. 사진 ID·정답 기반 분기나 임의 절단은 없다. 입력/Mat/Bitmap 해제와 오류 분류는 `analyzeEdgeDrawing`, native 직렬화는 `EdgeDrawingNative`, 화면의 취소/late-result·보정 보존·저장 재시도는 기존 CountViewModel이 담당한다. native 호출 자체를 중간 선점하지 않고 완료 후 취소를 확인한다.

계산은 앱/평가가 공유한다. ANNULUS/RADIAL/CONTINUOUS 비교 정책·품질 ablation·정답·진단 출력·고장 주입 JNI는 androidTest에 남긴다. `EvaluationEdgeDrawingCandidates.kt` 이름으로 앱의 Kotlin file facade와 충돌하지 않게 한다. 평가 JNI는 `EdgeDrawingTestNative`/libpounter_ed_test, 앱 JNI는 `EdgeDrawingNative`/libpounter_ed다. 같은 C++ 원본을 각각 컴파일하며 앱 빌드의 POUNTER_PRODUCTION에서는 감사 상태/고장 주입을 제외한다.

자동 분석/재분석과 검토 필요 안내만 기존 UI에 연결했다. 전체사진 기본, 선택적영역, 재분석/영역변경의 보정 초기화 확인, 수동 표시, 자동/보정 표시 구분, 확정 저장 성공 후 완료 표시를 유지한다. 실제 실패/0개는 별도로 처리한다. 독립 현장 인수·사람의 효용은 이번 앱 실행과 구분한다. 실행 수치/미검증은 [검증 이력](verification.md#supported_faces_v1-앱-내-시험-도입--2026-10-20)을 따른다.
