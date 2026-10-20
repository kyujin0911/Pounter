# 자동 계수 성능 개선 실행 계획

최신 실행 보충(2026-10-16): native 홀수 표본 수정은 평가 전용으로 채택했고 사진지표는 불변이다. a상한 우회/제3중심veto/Bookstein의 3개 분리 가설은 보류·불채택했다. 이번 요청은 과거 Task 고정을 대체해 원인 우선 실험을 허용했으며 일반 앱 연결·정답 변경·현장 합격은 제외했다. [검증 이력](../../verification.md)의 2026-10-16 절과 외부 native-improvement-v1 증빙이 현재 실행 상태다. 아래 기존 절은 당시 근거로 보존한다.


> **For agentic workers:** `superpowers:executing-plans`로 직접 순차 실행한다. 개정 3 문서 검토 후 별도 사용자 요청으로 Task 1~3의 평가·진단 코드와 판별 실험을 실행했다. 마지막 실행 기록을 따른다. 미체크 항목은 위치/Android 근거까지 완료했다는 뜻이 아니며 이전 요청에서 Task 4~8은 범위 밖이었다. Task 4 구현 요청은 당시 Task 5~8을 제외했다. 2026-10-15 최신 요청은 Task 4 최종 fresh review·확인된 관련 결함 수정과 Task 5의 직접 순차 구현·실측·문서 갱신을 허용한다. 당시 Task 6~8·제품 연결·커밋·원격 변경은 제외했다. 이후 2026-10-15 최신 요청은 Task 6의 직접 구현·평가 연결·회귀·독립 검토·기록을 허용했다. Task 7~8·일반 앱 연결·커밋·원격 변경은 여전히 제외한다.

작성 2026-10-13, **개정 3: 2026-10-14**. 기준 `feat/auto-counting` / `89bfa63df4d3866200c71d8d683fff23fbb9a8a8`. [개선 설계 개정 3](../specs/2026-10-13-auto-counting-improvement-design.md)을 따른다.

**목표:** 기존 다섯 사진의 위치별 실패를 재현하고 후보 생성 대안을 판별한 뒤, 필요한 품질·통합만 구현해 PRD의 보정량·시간·보존 기준으로 검증한다. 기존 설계를 끝까지 구현하는 것이 목표가 아니다.

**구조:** 평가 규약→관찰 가능한 기준선→후보 방식 비교/선택→선택 경로의 Android 구현→최소 품질→경계/물체 통합→기기 자원→독립 인수. A(현재 방식 최소 수정)/B(EdgeDrawing)/C(필요할 때 영역 보완)는 실험 대안이며 세 개를 모두 제품에 넣지 않는다.

## 실행 경계와 증거 상태

- 시작 시 실제 AGENTS/PRD/Git 운영 규칙, branch/HEAD/status를 확인한다. 같은 목적의 미병합 브랜치를 재사용한다. 기존 사용자 파일·사진·로컬 결과를 보존하고 clean tree를 만들려고 지우거나 임의 stash하지 않는다.
- 다섯 sample은 cap-151/55, metal-40/18/37이다. 금속 원본 첨부 순서는40·18·37, 번호사진은40·37·18. 노란색은 캡이며 금속색의 캡 없는 단면도 target이다.
- N·정답 위치·사진별 허용치·sampleId는 평가에만 쓰고 detector 입력/분기/출력 절단에 사용하지 않는다. full/crop·tuning/independent를 구분한다. 필수 crop·색상 필수 마스크·동일 크기/격자·숨은 수 추정으로 범위를 변경하지 않는다.
- R0는 `89bfa63`+`synthetic-v1`의 코드/설정/입력 경로다. 로컬 APK·결과·hash를 보존하고 새 평가 metric에서도 R0를 비교한다. 새 코드의 field 설정으로 합성 회귀를 실행하며 R0만 통과시켜 대신하지 않는다.
- 일반 앱 ValidationRequiredDetector, OpenCV androidTest 범위, Room/JSON/Navigation/보정 저장 계약을 유지한다. 후속 평가 구현이 main의 순수 기하/평가 helper를 수정할 수는 있으나 DI의 제품 검출 연결은 포함하지 않는다.
- 원본/번호사진/좌표 정답/오버레이/개인 경로는 저장소 밖 로컬 자료로 둔다. 공용 저장소에는 알고리즘 코드·합성 fixture·규약·비식별 요약만 포함한다.
- 현재 문서 작업에서는 커밋하지 않는다. 후속 구현에서도 커밋·원격 작업 범위는 당시 실제 요청을 따른다. 이전 단계의 커밋 예시를 자동 실행 권한으로 해석하지 않는다.

**이번에 완료한 검토:** 공식 API/소스·원 논문 조사, 현재 AAR 제공 API 확인, 데스크톱 OpenCV4.12 합성6종/실사진5종 후보 실험, blob·상보 마스크·거리 계산, 이 설계/계획 개정. **미완료:** 위치 정답 동결, Android 대안 구현·비교, 개선 정확도, 독립·대표 실기기 인수. 세부 수치·재현 조건은 설계 §3~6에 있다.

## 개정 2의 7단계와 대응

| 기존 단계 | 새 단계 | 변경 이유 |
|---|---|---|
| 1 위치 평가/runner | 1 | 유지. 정확도 비교의 선행 조건 |
| 2 진단 | 2 | 유지하되 gate 전 후보 회수와 실패 provenance 명확화 |
| 3 M 병합 고정 구현 | 3의 작은 비교, 6의 선택 구현 | 대칭 거리부터 구현하지 않고 단순 수정/표준 API 비교 |
| 4 S 후보/스케일 | 3의 대안 판별, 4의 선택 구현 | whole-contour의 구조적 한계를 후단 구현보다 먼저 확인 |
| 5 Q 법선 특징 묶음 | 5 조건부 최소 품질 | 측정된 FP에 필요한 특징만 남김 |
| 6 O hierarchy/객체 통합 | 6 조건부 물체 통합 | 같은 경계와 다른 경계 계약을 분리하고 hierarchy 의존 완화 |
| 7 평가/자원/인수 | 7 자원, 8 독립 인수 | 기기 비용으로 경로를 되돌릴 수 있게 하고 독립 자료는 마지막에만 사용 |

새 Task 8도 기존 최초 구현 계획의 **Task 7 제품 연결**과 다르다. 현장 인수 없이 제품 연결을 실행하지 않는다.

## 파일·인터페이스 원칙

M=`app/src/main/java/io/github/kyujin0911/pounter`, I=`app/src/androidTest/java/io/github/kyujin0911/pounter`, T=`app/src/test/java/io/github/kyujin0911/pounter`. 아래 파일은 이 접두사 기준이다. 새로운 파일은 해당 분기가 채택됐을 때만 만든다.

- M의 `DetectionGeometry.kt`/`EllipseCandidate`에는 native 타입·정답·사진 ID를 넣지 않는다. 기존 EllipseCandidate는 quality 필드를 필수로 가지므로 **품질 측정 전 원시 geometry를 가짜 score=0으로 채워 정렬하지 않는다.**
- I의 제한된 관측에는 run 내부 `observationId`, generator/mask/source ID, `geometry(cxPx,cyPx,aPx,bPx,angleRadians)`(미적합이면 없음), stage/reason, 측정된 quality, 대표와 원래 출처 ID를 둔다. 이 분리는 fit 이전/품질 이전 실패를 구분하기 위한 것이며 영속 DTO가 아니다.
- source ID는 실행/생성기 범위에서만 유효하다. RETR_TREE parentId도 동일 mask에만 유효하다. NFA/MSER stability를 기존 edgeSupport 값에 그대로 넣지 않는다. 공통 후처리 비교에는 같은 영상 측정을 적용한다.
- 기존 CandidateSource의 CONTOUR/REGION과 생성기 이름은 다른 수준의 출처다. 세부 generator ID는 I 관측에 둔다. 새 생성기에 기존 fitError의 점 집합이 없으면 0으로 채우지 않는다. Task 3에서 공통 측정이 가능한지 판별하고, 불가능하면 평가 경로의 geometry/quality를 분리해 대표 선택·좌표 복원이 임의 점수에 의존하지 않도록 최소 계약을 확정한다. 기존 R0 score 의미는 보존한다.
- 추상 plugin 시스템·다중 detector framework·새 앱 모듈을 미리 만들지 않는다. 평가 함수/파일로 대안을 비교하고 선택한 경로만 정리한다. C/B 전용 native 타입은 I 또는 선택한 시험 bridge 안에 둔다.

## Task 1: 위치 정답·좌표·실패 보존 규약

**목표/선행:** 설계 §7을 구현해 R0와 이후 결과를 같은 기준에서 평가한다. 주석 원본·번호자료에 접근할 수 있어야 실사진 부분을 완료할 수 있다. 접근 불가/검토 미완료일 때도 코드와 합성 평가 계약은 진행한다.

**파일/책임:** M/data/detector/DetectionMetrics.kt, T/DetectionMetricsTest.kt, I/DetectionEvaluationTest.kt, I/EvaluationProtocolInstrumentedTest.kt. 자료 자체는 로컬 외부 저장.

**핵심 계약:** 기존 `matchDetections(expected, actual, normalizedTolerance)`의 v1 결과 보존. `matchDetectionsInReferencePixels(expected, actual, referenceWidth, referenceHeight, tolerancesPx): DetectionMatchResult`를 추가하고 공통 최대 일대일 대응 코어 재사용. 결과는 metrics, expected/actual ID 대응, 미대응 ID를 포함한다. ID 유일성·좌표·양수 치수·finite 양수 tolerance와 정확한 key 집합 검사(N=0 빈 map 허용). 불확실성은 metric 허용치 확대가 아닌 검토 상태로 보고한다.

- [ ] 원본/번호자료 hash·EXIF 기준 치수·sample 대응·scope/full/crop을 확인한다. 원본에서 중심/bPx/uPx를 작성하고 검출 overlay 없이 검토한다. cap-55 위치55개 포함. 불확실한 target은 미검토로 남긴다.
- [ ] tPx 초기안의 적합성을 검토해 truth/metric 버전을 동결한다. 개수를 맞추려 중심이나 tolerance를 조정하지 않는다. session/bundle unknown은 development에만 허용한다.
- [ ] 실패 회귀: 1000×500에서 Δx=.01과 Δy=.02 모두10px, 중복 예측 M1/FN0/FP1, 개수 같지만 다른 위치 FN1/FP1, 최대 대응이 탐욕 대응과 다른 사례, invalid ID/hash/tolerance, N=0, source와 ROI 좌표 혼동.
- [ ] runner를 import/primary/repeat/각 비교 상태로 분리하고 primary부터 보존한다. `comparisonFailurePreservesPrimaryResult`, `badImportDoesNotEraseOtherSamples`, `cancellationPropagates`, `unknownSessionCannotBeIndependent`, `schema1KeepsOriginalMetric`을 검증한다. 실행 시작 기록으로 native crash 뒤 미완료 run도 남긴다.
- [ ] schema2의 실제 source/import hash·치수, truth/annotation 버전과 code/config/metric·device/ABI 버전을 구분한다. v1/v2 동일 예측 비교 후 R0의 full/crop 위치 지표를 계산한다.

**검증/완료:** DetectionMetricsTest와 EvaluationProtocolInstrumentedTest 통과, 허용치·좌표 의미가 모든 비교 경로에서 동일, R0의 정확한 위치 지표 또는 구체적인 미검토 상태 기록. 주석 불확실성 때문에 대응 결과가 바뀌면 인수용 결과를 완료하지 않는다.

**분기/비채택:** v2는 기존 결과를 덮지 않고 병행한다. 자료가 부족하면 합성 계약을 완료하고 Task 2/3의 독립적인 실험을 진행하되 실사진 정확도 순위를 내지 않는다.

## Task 2: 기준선의 후보 회수와 오류 출처 관찰

**목표/선행:** Task 1의 좌표/실패 규약을 사용해 후보 생성 전후와 거절·통합을 구분한다. 기준선을 바꾸지 않는 관측이 먼저다.

**파일/책임:** I/data/detector/DetectionDiagnostics.kt(필요시 신규), CandidateExtractor.kt, CompositePipeDetector.kt, I/CompositeDetectorInstrumentedTest.kt. 병합 관측은 M/data/detector/CandidateMerge.kt에 native 없는 선택적 callback만 검토한다.

**핵심 계약:** 정확한 stage 총량, bounded entry/point 기록, reset/snapshot/truncated. Mat/Bitmap을 보관하지 않고 관측 ID→최종 대표 연결을 유지한다. fit 전 shape 없는 실패는 bbox/제한된 윤곽 예시가 있어야 target과 대응한다.

- [ ] `traceDoesNotChangeGeometry`, `traceBudgetDoesNotTruncateDetections`, `failureKeepsStageTotals`, `nextRunStartsWithEmptyTrace` 회귀. 정상0개/COUNT_LIMIT/포화/취소를 별개로 검증한다.
- [ ] 생성 raw→fit→geometry→quality→같은 경계/물체 통합의 수와 거절 이유를 기록한다. `UNRESOLVED`를 허용하고 fit 이전 원인을 중심만으로 확정하지 않는다.
- [ ] LIST를 보존하며 시작한다. TREE가 필요하면 별도 버전에서 후보 geometry 다중집합/최종 marker를 비교하고 변동을 기록한다. trace on/off 일치와 TREE 효과를 혼동하지 않는다.
- [ ] Canny-only/binary-only/inverse-only/현재 combined의 고유 회수·중복 기여를 비교한다. 마스크 상보성만으로 한 경로를 삭제하지 않는다.
- [ ] 다섯 사진 full/crop의 candidate M과 최종 M/FN/FP·원인표를 작성한다. 실제 단면 후보 부재, geometry/quality 거절, 같은 rim, cap 내외곽, 오통합, 배경 질감, 다른 묶음의 실제 파이프를 분리한다.

**검증/완료:** 관측 테스트 통과, 입력/설정 동일 시 R0 geometry 보존, 각 누락에 관찰된 stage 또는 미확정 사유 존재. 실제 결과를 바꿨으면 새 baseline으로 다시 비교한다.

**분기/비채택:** 후보 FN 하한만으로 PRD 허용량을 넘으면 후단 개선을 먼저 누적하지 않고 Task 3 생성 방식 비교를 우선한다. 상세 trace가 불필요한 부분에는 이미지 저장/대시보드를 만들지 않는다.

## Task 3: A/B/C 판별 실험과 선택 결정

**목표/선행:** Task 2 실패를 해결할 생성기를 정식 구현 전에 선택한다. 이번 데스크톱 재현은 출발 근거이며 이 Task의 Android/위치 검증 완료가 아니다.

**파일/책임:** I/CandidateGenerationInstrumentedTest.kt(채택할 비교 시험), I/data/detector/CandidateExtractor.kt, I/DetectionEvaluationTest.kt, 관련 로컬 실험 script/결과. B의 빌드 가능성은 저장소 밖 별도 spike에서 확인한다. 이 단계에서 app/build.gradle.kts나 main DI를 먼저 교체하지 않는다.

**핵심 계약:** 각 probe는 같은 gray/ROI와 고정 설정으로 `geometry observations + stage totals + success/failure`를 반환한다. 후보 개수/geometry와 최종 Marker 결과를 별도 항목으로 둔다. 공통 gate 전/후를 모두 보아 기존 80px/축비 gate가 새 경로에 준 영향을 분리한다.

- [ ] 설계 §9의 G/R/M/S 카드 중 확인된 원인에 필요한 것부터 작성한다. 입력/정답/metric·구현 버전·설정 목록과 초기 최대4변형의 비교 예산을 고정한다. 이미 본 사진을 independent로 옮기지 않는다.
- [ ] A는 R0와 포함률 최소 수정 variant를 기준으로 둔다. B는 OpenCV4.12 EdgeDrawing NFA/PF 설정·출력6값 변환을 고정한다. cap/단절/접촉/편평 타원/작고 흐린 rim/몸체 원호/blank fixture를 포함한다.
- [ ] 같은 전처리·공통 geometry/최소 후처리에서 A/B의 candidate 회수와 잘못된 결합을 비교한다. 방식별 권장 전처리/설정 소수 비교는 별도로 기록한다. 기존 contour용 fitError 점수를 B/C에 가짜로 채우지 않는다.
- [ ] 선택 경로의 실제 점/영역/영상 근거로 기존 quality 필드를 계산할 수 있는지 확인한다. 같지 않은 score를 비교·정렬하지 않는다. 공통 quality가 불필요하거나 정의되지 않으면 I의 geometry/quality 관측을 이용하는 대표 선택과 순수 좌표 변환 adapter를 결정한다. 필요한 M/data/detector/DetectionGeometry.kt·CandidateMerge.kt 계약 변경과 회귀 범위를 Task 4/6 파일 목록에 확정한 뒤 구현한다.
- [ ] C는 A/B가 놓친 실제 target의 영역이 안정돼 보일 때 C0 blob부터 비교한다. C0의 중심/radius와 ellipse geometry는 다르므로 boundary 변환을 검증한다. C1 MSER는 stability가 추가 회수/FP 감소를 주는지 확인한 뒤 사용한다. C의 고유 회수 없이 합집합을 만들지 않는다.
- [ ] 같은 후보 목록에서 단순 same-rim gate와 표준 getClosestEllipsePoints의 양방향 거리를 비교한다. 원 해석해·회전 타원·각도표기/반축 swap·이웃 원/연쇄를 사용한다. 이 단계의 probe를 제품 custom solver 구현 완료로 보지 않는다.
- [ ] B의 Android4.12 contrib SDK/Java 또는 최소 bridge 중 한 경로를 spike한다. ABI·호출·메모리 소유·license/notice·SDK 크기·재현 build 확인. 현 AAR와 다른 OpenCV native를 중복 포함하지 않는다. Python 호출 성공은 이 항목의 완료가 아니다.
- [ ] 결과표: 후보 고유 회수, 같은 후처리의 사진별 FN/FP/correction, target 종류별 회귀, 포화/실패, 구현비용. 시간이 다른 언어/wrapper로 측정됐으면 순위 근거에서 제외한다.

**검증/완료:** 가설별 반례 결과와 A 유지/B 교체/C 보완/모두 보류 중 하나의 근거를 기록한다. 생성기 변경은 실제 누락 회수와 최종 위치 오류 개선 가능성이 있어야 다음 단계로 간다. source/합성 회귀와 Android feasibility를 구분한다.

**분기/비채택:** B에 실사진 회수 이득이 없거나 빌드/자원 비용을 감당할 수 없으면 A 유지. C가 후보만 늘리면 제외. 위치 정답/기기 부족이면 조건부 후보만 남기고 최종 승자를 선언하지 않는다. 모두 실패하면 다음 최소 원인 실험을 설계하고 Task 4를 무조건 진행하지 않는다.

## Task 4: 선택한 후보 경로의 Android 구현과 입력 대응

**목표/선행:** Task 3에서 선택한 경로만 평가 검출기에 통합한다. 개발 자료의 개선 가능성, Android 연결 근거와 최소 adapter 계약으로 착수를 판단한다. B를 고른 경우 시험용 native 연결·소유권이 확인돼야 한다. 주석 전체 동결·S22+ 전체 자원/흐름·독립 현장 인수는 Task 7~8의 조건이며 모두 Task 4 전에 완료해야 하는 조건으로 옮기지 않는다. 현재 crash/OOM/소유권 결함이나 감당 불가능한 측정 비용은 착수 판단에 반영한다.

**파일/책임:** I/data/detector/CandidateExtractor.kt, DetectionConfig.kt, CompositePipeDetector.kt; 선택 시 EdgeDrawingCandidates.kt 또는 RegionCandidates.kt; I/CandidateGenerationInstrumentedTest.kt, CompositeDetectorInstrumentedTest.kt, AnalysisPhotoInstrumentedTest.kt. B일 때만 app/build.gradle.kts와 선택한 시험 native build 경로(예: app/src/androidTest/cpp/의 bridge/CMake)를 명시적으로 확정한다. androidTest 소스 경로만으로 native가 제품 APK에서 제외된다고 가정하지 말고 실제 패키징을 검사한다. A/C이면 이 의존성 변경을 만들지 않는다.

**핵심 계약:** 생성기는 분석 픽셀의 canonical geometry와 출처를 반환한다. 같은 의미의 quality를 측정한 경우에만 기존 EllipseCandidate에 대응하며, 그렇지 않으면 Task 3에서 확정한 geometry/quality 분리 adapter를 쓴다. `DetectionConfig`에 선택 경로·단위/임계값·버전을 명시하고 schema1 합성 설정도 읽는다. 일반 앱의 PipeDetector 계약·Marker·저장 형식은 유지한다.

- [x] Task 3 실패 fixture를 Android에서 먼저 재현하고 최소 구현으로 통과시킨다. degrees/radians, 전체축/반축, major/minor swap, 원의 angle, finite/양수 축을 검증한다.
- [x] 같은 decoded pixels와 native 옵션으로 데스크톱/Android geometry를 비교한다. float 차이 허용은 수치 fixture로 정하고 field 매칭 tolerance와 혼용하지 않는다. 불일치는 ABI/옵션/pixel/변환 순으로 조사한다.
- [x] scale 변경은 확인된 원인만 적용한다. 크기 상한은 crop 전 전체 분석 치수 기준을 시험하고 최소 raster 하한 유지. crop만 바뀔 때 gate가 흔들리지 않는 회귀를 둔다.
- [x] 같은 pixels의 무손실/JPEG95, 같은 저장 파일의 full/decode scale을 따로 비교한다. 다섯 기존 입력에는 분석 축소가 없었던 사실을 유지한다. CHAIN_APPROX_SIMPLE/fitAMS·Direct도 선택했다면 point 분포·단절 후보 회수·비용을 따로 비교한다.
- [x] 실제 x/y scale·EXIF 회전/반전·ROI offset·경계 잘림·큰 타원과 작은 돌출 단면 동시 입력을 검증한다. 입력 hash 불변, Mat 정상/예외 해제, native 직렬 소유/취소 후 재시도를 확인한다.

**검증/완료:** 선택 경로에서 합성·좌표·자원 회귀 통과, field 설정에서도 실행, trace-off 결과와 Task 3 가설 일치. B이면 실제 시험 APK에 필요한 ABI만 들어가고 일반 APK의 검증 대기 의존성/DI가 유지되는지 확인한다.

**분기/비채택:** Android에서 재현 안 되거나 dependency 범위를 보존할 수 없으면 Task 3으로 돌아간다. PhotoStore 제품 변경이 필요하다는 증거는 별도 범위 결정으로 남기고 평가 코드에서 사진 저장 정책을 몰래 바꾸지 않는다.

## Task 5: 실제 FP에 필요한 최소 품질만 구현

**목표/선행:** Task 4의 후보 목록·Task 2 원인표를 고정하고 품질 변경의 효과만 측정한다. 개정 2의 여러 특징 묶음을 모두 만드는 단계가 아니다.

**파일/책임:** I/data/detector/CandidateExtractor.kt, DetectionConfig.kt; 필요할 때 CandidateQuality.kt, I/CandidateQualityInstrumentedTest.kt; I/CompositeDetectorInstrumentedTest.kt.

**핵심 계약:** `measureQuality(gray, edges, geometry, policy): QualityEvidence`. Mat은 호출자가 소유하고 evidence는 scalar/제한된 배열만 가진다. 먼저 실제로 사용하는 coverage/잔차만 정의한다. gradient Mat/법선/대비가 채택되면 그때 추가하고 이미지당 한 번 계산·해제한다. native NFA와 영상 support를 같은 척도로 정렬하지 않는다.

- [x] 기존 잔차/지원도와 최소 경계 지지 분포만으로 실제 FP가 분리되는지 먼저 비교한다. 같은 후보와 병합 정책을 고정하고 FN/FP/correction을 보고한다.
- [x] 실패 fixture: 짧은 호·몸체·이웃 rim의 edge로 지지된 가짜 타원, 반사된 금속 내부, 넓은 cap, 식별 가능한 부분 가림, 영상/ROI 경계 표본 부족. 작은/캡 없는 단면을 반드시 포함한다.
- [x] 법선은 이번 최소 공백 측정의 비채택으로 추가하지 않았다. 이후 필요하면 a80/b20/t45°의 방사선과 법선 차이 약61.9°를 검사한다. `normalize(R(cos(t)/a,sin(t)/b))`, bilinear sampling, 각도/회전 및 호 길이 분포를 검증한다. sampling spacing/상한을 명시하고 undersampled를 숨기지 않는다.
- [x] 밖의 표본은 invalid, 영상 안의 edge 없는/가림 추정 표본은 unsupported로 집계한다. clamp 중복·분모 축소로 점수가 상승하지 않는 회귀를 둔다. 대비 양/음·부분 지원을 허용하되 단면 근거 부족과 구분한다.
- [x] 소수 특징 ablation으로 관측 이득·trace-off 비용을 비교한다. 모든 구간 지지·노란색·상수 반사 패턴을 필수로 하지 않는다. 필요하면 생성기×품질의2×2 비교로 상호작용을 확인한다.

**검증/완료:** 해당 실패 회귀와 기존 합성 통과, target별 유지/거절 이유·FN/FP 변화·비용 확인, 채택한 최소 특징과 버전 기록. 비교 결과에 사용하지 않는 metric/정책은 구현하지 않는다.

**분기/비채택:** 단순 품질로 충분하면 법선/gradient/복잡 대비 모델은 생략한다. 거절 정책이 실제 target을 잃거나 correction을 개선하지 않으면 해당 정책을 보류하고 관측만 유지할 수 있다. 후보 자체 부재는 Task 3/4로 되돌린다.

## Task 6: 같은 경계와 단면 내외곽의 조건부 통합

**목표/선행:** Task 3에서 판별한 same-rim 정책과 Task 5 이후 남은 실제 내외곽 중복만 처리한다. 서로 다른 파이프 보존이 우선이다.

**파일/책임:** M/data/detector/CandidateMerge.kt, T/CandidateMergeTest.kt, I/data/detector/DetectionConfig.kt, I/CompositeDetectorInstrumentedTest.kt. native 경계 거리/물체 관계가 필요할 때만 I/data/detector/BoundaryComparison.kt 또는 ContourGrouping.kt와 관련 기기 테스트를 추가한다.

**핵심 계약:** 기존 `mergeCandidates(candidates, policy)`를 보존하고 대표/출처 연결을 반환·관측한다. 거리 API가 필요하면 I에서 pair 관계를 계산하거나 native 없는 callback으로 전달한다. M에 Mat/OpenCV import를 넣지 않는다. 같은 경계 판정과 `groupCrossSectionBoundaries(observations, imageEvidence, policy)`의 물체 판정을 분리한다. deterministic 대표 anchor를 유지하고 평균 이동/전이적 cluster 병합을 하지 않는다.

- [x] same-rim 실패: r20/중심(50,50),(51,50)→1. 접한 r20 중심거리40→2, 이웃 소형/대형→2, angle+π/원의 angle→1, A–B–C에서 대표와 다른 C 유지, 입력 순열의 shape/sources 동일.
- [x] Task 3의 더 단순한 합격 정책을 구현한다. 표준 최근접점이 필요하면 원 해석해·회전 타원·고정밀 reference에 대해 수치 오차/샘플 밀도 검증, cheap gate 뒤 호출, p90/max/정규화 단위 기록. 추가 이득 없는 custom solver는 만들지 않는다.
- [ ] Q/M 뒤 cap 구멍/외곽 중복이 남는 위치를 확인한다. 없으면 객체 통합 부분은 생략한다. 남으면 r8/r24 cap→1, 옆의 캡 없는 단면 포함→2, 접한 annulus2개→2, 복합 parent 아래 aperture2개→2, 중심이 어긋난 내외곽/후퇴한 이웃 파이프 회귀를 둔다.
- [x] hierarchy는 필요할 때만 TREE 비교 후 사용한다. RETR_EXTERNAL 또는 parent만 남기는 정책을 쓰지 않는다. 다른 mask의 parent ID를 공유하지 않는다. 관측된 별도 aperture·모호한 관계는 합치지 않는다.
- [x] 대표는 고정 영상 근거/geometry tie-break로 고르고 중심을 평균 이동시키지 않는다. inner-only target도 충분한 영상 근거가 있으면 유지한다. 더 큰 경계/더 어두운 영역이 항상 정답이라는 규칙을 넣지 않는다.
- [x] 동일 후보로 same-rim 전후와 객체 통합 전후를 각각 평가한다. 필요한 경우 Q×O 2×2를 실행한다. cap 중복 감소와 metal FN/이웃 오통합, 표시 반경·실제 보정 적합성을 함께 확인한다.

**검증/완료:** CandidateMergeTest·geometry·선택한 영상 회귀 통과, 실사진 위치 오류 변화와 pair 비용 기록. candidate FN을 전체 수 감소로 가리지 않는다.

**분기/비채택:** 단순 정책과 표준 거리가 같은 결과면 단순 정책을 채택한다. 내외곽 판단이 이웃을 합치거나 품질 뒤 중복이 없으면 객체 통합을 생략한다. 대칭 거리/hierarchy 적용 자체를 완료 성과로 삼지 않는다.

**2026-10-15 실행 판정:** 같은 rim 재사용/예산·통합 실험/실제 Android 비교는 완료했다. Q/M 뒤 내외곽의 모든 인수 반례(특히 parent+두 aperture의 정확히2개)는 미충족이어서 위 해당 항목은 미체크로 보존한다. ALL/90 모두 보류, 기본null이며 통합 채택 완료로 읽지 않는다. 아래 최신 Task6 기록을 따른다.

## Task 7: 대표 Android 자원·실패·흐름 검증

**목표/선행:** Task 4~6 선택 버전과 입력/설정을 고정한다. 기기 비용을 확인한 뒤 마지막 독립 인수로 넘긴다. 대표 실기기가 없으면 자동 합성·에뮬레이터 범위만 완료한다.

**파일/책임:** I/DetectionEvaluationTest.kt, I/data/detector/CompositePipeDetector.kt, I/CompositeDetectorInstrumentedTest.kt, AnalysisPhotoInstrumentedTest.kt, AnalysisMetadataInstrumentedTest.kt, DetectionFlowUiTest.kt, docs/verification.md. 제품 DI/저장 스키마를 변경하지 않는다.

**핵심 계약:** run별 process identity/최초 여부, 단계별 시간, 분석→표시 지연, trace 상태, 메모리 측정 수단/샘플 주기, 정상/실패/미완료를 보고한다. 함수 시간·추정 buffer 합과 사용자 지연·실제 peak를 구분한다.

- [ ] S22+/Android16에서 프로세스 최초 최소3회, 같은 프로세스 반복 최소10회의 원시값/median/max 측정. detector 새 객체만 만들어 cold라고 부르지 않는다. init/import/decode/native/후처리/대기/저장·표시를 나눈다.
- [ ] profiler 등으로 실제 Java/native/프로세스 peak를 측정한다. 큰 입력·많은 질감·300개 근처·실패 반복을 포함한다. contour/points/arc/regions/pairs·동시 Mat를 원인 지표로 기록한다. findContours 이후 개수 상한을 사전 할당 상한이라고 보고하지 않는다.
- [ ] 0/300/301/포화/읽기·초기화 오류를 분리한다. native 호출 중 취소→종료 후 해제→대기 중 다음 작업 실행을 검사하고 늦은 결과가 현재 표시를 덮지 않도록 한다. 실패·재시도 후 메모리 증가/원본·보정 손실이 없는지 확인한다.
- [ ] 테스트 전용 주입으로 분석→표시·보정·저장 실패 유지·빠른 재시도 흐름을 검증한다. 실제 카메라/Picker와 Activity 재생성·OS 프로세스 종료·force-stop은 각 절차/결과를 구분한다.
- [ ] 아래 공통 검증을 선택된 새 코드 경로에 실행하고 필요한 클래스만 추가한다. Hough는 명시한 별도 비교에서만 실행한다. 코드 변경 후 AGENTS에 따라 graphify update .를 수행한다.

**검증/완료:** PRD5초 지연 목표와 측정 가능한 메모리 범위, 소유권/실패/보존 회귀에 대한 근거. 기기·환경·실행 횟수·미완료를 기록한다. 예상 peak 수치만으로 합격시키지 않는다.

**분기/비채택:** crash/OOM/누수/취소 경합은 채택 불가. 느리면 trace-off 병목과 품질 교환을 측정해 한 요인씩 수정하고 같은 회귀를 반복한다. 목표를 완화하거나 사진 수를 숨기지 않는다. B 비용이 감당 불가면 Task 3으로 돌아간다.

## Task 8: 최종 고정 비교·독립 현장 인수·문서 상태

**목표/선행:** Task 1~7에서 채택한 code/config/metric을 동결하고 개발 자료와 다른 촬영 세션·묶음의 자료로 인수한다. 아직 없는 자료를 다섯 사진의 재압축/crop으로 대체하지 않는다.

**파일/책임:** I/DetectionEvaluationTest.kt, I/DetectionAcceptanceTest.kt, I/EvaluationProtocolInstrumentedTest.kt, docs/verification.md, 이 계획, 기존 계획의 Task 6 상태. 제품 연결 Task 7·AppModule은 이 단계의 변경 대상이 아니다.

**핵심 계약:** `DetectionEvaluationTest#evaluateTuningSet`, `DetectionAcceptanceTest#evaluateIndependentSet`의 schema2/metric-v2 결과. 사진별 primary/비교 실패·full/crop·target 속성별 오류·actual corrections·user review 시간을 유지한다.

- [ ] R0→선택 생성기→최소 품질→same-rim→필요 물체 통합의 채택/보류 버전을 비교한다. 각 사진 FN/FP/correction과 full/crop·실패 행을 남기고 평균으로 실패를 숨기지 않는다.
- [ ] 개발 사진의 허용7/2/2/1/1과 보정 후 정답 일치를 확인한다. 목표 미달이면 다음 최소 실험/현재 한계를 기록하고 완료로 표시하지 않는다.
- [ ] 독립 자료의 검토된 위치 정답·hash·session/bundle·support 조건을 결과 보기 전에 고정한다. 이미 본 자료/unknown 출처는 independent 인수를 거부한다.
- [ ] N>0 사진마다 correction≤max(1,floor(.05N)), N=0 사전 규약, 보정 후 정답, 대표 기기5초·메모리·실패 보존을 확인한다. 독립 결과를 보고 수정하면 새 버전과 새 독립 자료가 필요하다.
- [ ] 촬영·자르기·분석·확대·보정·재촬영의 전체 시간, 최종 오류·포기 여부를 수동 검수와 비교한다. 기억 효과를 고려하고 반복 횟수/필요 개선 폭은 PRD의 첫 사용 실험 후 합의 조건을 유지한다.
- [ ] 전체 관련 diff를 검토하고 지적은 실제 코드/회귀로 확인한다. 수행 명령·실제 결과·보류 사유와 다음 최소 절차만 기록한다. 사용자 파일·사진·개인 경로 포함 여부를 확인한다.

**검증/완료:** 독립 자료·대표 기기·최종 보정/전체 검수 근거가 모두 있어야 현장 인수 완료다. 코드/합성 테스트만 완료됐으면 구현 상태와 현장 검증 대기를 분리한다.

**분기/비채택:** 필요한 근거가 하나라도 없으면 해당 인수를 보류한다. 기존 최초 계획 Task 7 제품 연결은 현장 인수 조건 충족과 당시 승인 범위 확인 후의 별도 작업이다. 이번 설계 검토에서는 실행하지 않는다.

## 후속 구현 때 사용할 검증 명령

다음은 **미래 실행 절차**이며 이번 문서 개정 중 Android 빌드를 수행했다는 뜻이 아니다. 정상 JDK/SDK 설정을 재사용한다. 관련 실패 회귀를 먼저 확인하고 최소 구현 뒤 같은 검사로 재검증한다. 무관한 기존 테스트를 억지로 실패시키지 않는다.

```sh
./gradlew testDebugUnitTest --tests '*DetectionMetricsTest' --tests '*CandidateMergeTest' --tests '*DetectionGeometryTest'
./gradlew spotlessCheck assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.CompositeDetectorInstrumentedTest,io.github.kyujin0911.pounter.AnalysisPhotoInstrumentedTest,io.github.kyujin0911.pounter.EvaluationProtocolInstrumentedTest,io.github.kyujin0911.pounter.AnalysisMetadataInstrumentedTest,io.github.kyujin0911.pounter.DetectionFlowUiTest
```

Task 3/4에서 실제 추가한 CandidateGenerationInstrumentedTest와 Task 5에서 필요한 경우 추가한 CandidateQualityInstrumentedTest를 명시 목록에 넣는다. 단계별로 해당 클래스만 먼저 실행한다. 실사진 평가에는 외부 evaluationManifest/evaluationConfig를 전달하고 synthetic/UI 결과와 구분한다. B 채택 시 시험 native build/ABI/package 검사 명령은 Task 3에서 실제 build 방식에 맞춰 고정한다.

## 문서 검토 완료 기준과 현재 체크 상태

- [x] 기존 설계의 가정·대안·실패 유형·버전/비용을 분리하고 공식 근거와 격리 실험을 반영했다.
- [x] 대칭 거리·법선 특징·hierarchy를 필수 구현에서 조건부 실험으로 변경했다.
- [x] 기존7단계→새8단계 대응, task별 파일·계약·반례·검증·분기·비채택 조건을 명시했다.
- [ ] Task 1~8 후속 구현/평가 완료. 이 항목은 현재 수행하지 않았다.
- [ ] 독립 실사진·S22+·실제 보정/전체 검수 인수. 현재 검증 대기다.

실행 계획의 체크박스와 실제 수행 결과를 일치시킨다. 문서 개정·Python 후보 재현을 Android 성능 개선 달성으로 보고하지 않는다.

## 개정 3 Task 1~3 실행 기록 — 2026-10-14

새 사용자 요청은 개정3 Task 1~3의 평가 코드·진단·판별 실험에 한해 이전 문서 작업 범위를 대체한다. 직접 순차 실행, 기존 feat/auto-counting/89bfa63 재사용, 사용자 문서/미추적 자료 보존, 커밋/원격 작업 없음. Task4~8과 최초 Task7은 실행하지 않는다.

- Task1: 평가 코드·합성 계약 구현/검증. 위치 정답은 metal-18의 수동18개 DRAFT만 작성, 모두 frozen=false. 다른4장(특히 cap-55 위치55개)·주석 검토·허용치 동결·R0 실사진 v1/v2 인수 지표는 대기.
- Task2: R0 geometry를 유지하는 bounded trace와 실제 PhotoStore JPEG95 full/crop10행 진단·세 mask ablation. 개별 target 회수/FP/FN·배타적 물체/배경 원인표는 정답 검토 전 UNRESOLVED. LIST 유지, TREE 변경 없음.
- Task3: G/M/S 반례·공통 후처리·표준 거리 Android API 확인. 결론은 **모두 보류**, A 비교 기준선 유지/B 조건부 추가 실험/C 고유 회수 전 확장 보류. B build는 CMake/NDK/Ninja 부재로 사전 점검만 수행; ABI·자원·크기·배포 라이선스·재현 빌드 미확인. 사진별 튜닝·가짜 fitError·제품 의존성 변경 없음.
- 전체 Task 완료 체크는 실사진 위치/Android 선택 근거까지 확인한 뒤에만 갱신한다. 상세 표·실제 검증·한계·Task4 최소 adapter 파일/진행 조건은 [이번 검증 기록](../../verification.md#개정-3-task-13-직접-실행--2026-10-14)을 따른다.

판단: 기존 checkout 재사용으로 사용자 파일 복사를 피했다. 커밋 금지이므로 skill의 commit/task-done 커밋 구간 대신 로컬 ledger·실행 로그를 보존한다. 위치/Android 근거 부족을 합성 성공으로 대체하지 않는다. 비용: 별도 checkout/커밋으로 구분되는 결과가 아니므로 이번 diff와 기존 문서 수정의 경계를 유지해야 한다.

최종 확인: JVM56·관련 기기38개 통과, 실사진 진단10행, graphify AST 갱신·diff 검사 통과. fresh reviewer Important4건을 RED→GREEN으로 수정했다. Minor2건(original 비교 바깥 status·평가용 생성자 positional 호환)은 검증 기록에 보류를 명시했다. 위치 정답/허용치 동결과 B native 실행은 미완료이며 Task4를 시작하지 않는다.

### 위치 주석 후속 반영 — 2026-10-14

사용자가 제공한 번호사진5장을 원본에 대조해 cap-151/55·metal-40/18/37의 중심301개 초안을 작성했다. 이전의 '다른4장 위치 미작성' 상태는 이 기록으로 대체한다. cap-55 위치55개도 포함한다. 번호는 식별 참고이며 원본 중심과 b/u 추정값을 별도로 작성했고 저장소 밖에 보존했다. schema2 실제 Android 검증5장/301개 통과, DRAFT frozen=true 거부 확인. 개수 확정과 새 좌표 검토를 구분하며 모두 groundTruthFrozen=false다.

남은 것은 중첩된 일부 번호 ID, metal-37의19번 부분 가림, 중심/반축/uPx·허용치 검토 및 동결, 배경 상세 excluded 주석과 동일 R0 위치 지표다. 새로운 검출 실험/Task4/B 설치/제품 연결/커밋/원격 작업은 수행하지 않았다. 상세는 [번호사진·위치 반영 기록](../../verification.md#번호사진-추가와-원본-위치-주석-반영--2026-10-14)을 따른다.

### Task 1–3 후속 실행 상태 — 2026-10-14

이 기록은 앞선 'runner Minor2 보류/B toolchain 부재/위치 지표 미작성' 상태를 대체한다. 작업은 직접 순차 진행했으며 feat/auto-counting/89bfa63과 사용자 파일을 보존했다.

- Task1 코드·계약 보완 완료: original budget/read의 외부 SKIP/FAILURE, 기존 네 번째 positional nativeProbe 호환을 RED→GREEN으로 검증했다. 원본/번호자료 hash·실제 EXIF/import/decode/ROI·JPEG95·source/config/code/APK/canonical truth hash를 기록했다. 마지막 cap151의112 원본 y=765px까지 사용자 확인을 받아 중심301개 검토/0개 대기다. v5 전체 annotation은 모든 b/u/t 미검토로 DRAFT/frozen=false다. 개수301 확정·중심 검토·전체 주석 동결을 구분한다.
- Task2 관측·잠정 진단 완료: 최대10행/25000 geometry entry/0 point의 opt-in export, 상세 파일과 primitive summary 분리, INVALID_FIT 보존을 구현했다. NaN 직렬화·누적 JSON OOM을 각각 재현·수정했다. R0 full/crop10행, 성공9 trace on/off geometry 일치, 동일 예측 v1/v2·fit/geometry/quality/final·mask별 고유 target 지표를 기록했다. raw 부재·물리적 FP/FN 원인은 증거가 없는 부분을 UNRESOLVED로 둔다. 실사진 인수는 미동결 주석 때문에 대기다.
- Task3 판별 완료/최종 채택 대기: 실제 Kotlin R0 replay와 gap-only A1, 동일 실제 gray의 Android A/B generation 및 desktop 공통 comparator, 전역 gate 한 요인 S, C0의 center-only screening과 반례를 기록했다. A1은 보정량 악화로 채택하지 않는다. B는 공식 NDK27와 기존 AAR만 연결한 최소 JNI에서4 ABI build/재현·arm64 호출·예외 자원·6값 변환·크기/약64MiB color LUT BSS를 확인했다. CMake/Ninja는 이 직접 clang 경로에 필요하지 않다. 실제 B `detect()` 통합·대표 기기 peak/취소·배포 고지는 남았다.
- 결론 **모두 보류**: A/R0 기준선 유지, B는 조건부 우선 후보, C는 경계 검토 전 확대하지 않는다. 공통 후처리 cap151 full/crop의 COUNT_LIMIT를 성공이나300으로 바꾸지 않았다. B/C score를 R0 score로 위장하지 않았다. 위치/허용치·같은 기준의 보정량 개선·반례/자원·최소 adapter를 확정하기 전 Task4를 시작하지 않는다.

### 원본 검토와 개발 경로 선택 — 2026-10-14, original-review-1

이 기록은 위의 **모두 보류/Task4 선행에 현장 인수 조건을 혼합한 판단**을 대체한다. 과거 실행·테스트 기록은 보존한다. 이번에는 앱/evaluation detector 코드를 변경하지 않고 기존 예측의 hash 확인·원본 검토·저장소 밖 고정 geometry 비교를 실행했다. **B 교체 경로를 조건부 개발 후보로 선택하며 Task4 착수는 가능하다. 이번에는 Task4를 시작하지 않았다.**

- Task1: 승인301개 중심·확정 개수·capless11/2·기존 ID 포함을 유지했다. 원본 crop301개/atlas15개를 시각 검토하고 모든 항목에 기존 b/u/t, 원본 근거, b/u 범위 제안, 방법·상태·모호성을 기록했다. 7개는 외곽 짧은 지름 endpoints를 직접 읽었다. 나머지는 시각 범위 제안이며 전체 정밀 측정/사용자 승인이 아니다. 국소 중심/ID 불일치 의심은 해당 항목만 재질문했다. b/u/허용치와 전체 annotation은 계속 DRAFT/frozen=false다.
- scope: metal40 왼쪽 가장자리와 metal37 오른쪽 아래의 번호 없는 부분은 사용자 답변으로 **별도 묶음** 확인. 원인 주석만 남기며 예측을 제거하지 않는다. full/crop의 중심 밖/짧은 반경 경계 screen과 원본 프레임 잘림을 별도 기록했다. 회전 외곽 전체의 교차 검증을 완료했다고 주장하지 않는다.
- Task2: v5 기존 t와 동일 R0 예측을 재평가했다. cap151 crop149/2/126/128, full COUNT_LIMIT406 유지. crop FP126의 배타적 원본 검토 분류는 내외곽43/같은경계33/비대상27/질감·몸체·반사13/미확정10이다. 다른 성공행 FP275는 아직 UNRESOLVED이며 원본/대표 대조 절차를 남겼다. 각 최종 FN에 마지막 관측 단계/대표 관계를 기록했다. fit 중심 부재를 raw 부재로 확대하지 않는다.
- 불확실성: 기존 P0 제안을 유지하고 평가 전에 기록한 .20/.30 및 원본 b 범위 민감도를 분리했다. u를 t에 더하지 않았다. target별 u 상한8방향에서 대응 ID·M/FN/FP가 바뀌어 인수는 보류했다. 총M 동일/대응 detection 교체도 기록했으며 이번 sampled probe에서 다른 target으로의 동일 detection 재할당은0이었다. 연속·전체 동시 perturbation의 안정성 증명이 아니다.
- Task3: 신규 검출 변형은 **내외곽 postpass1개**로 제한했다. 기존 simple 대 표준 closest-point를 고정 입력에서 재비교했고 표준 방식의 추가 이득이 없어 custom solver를 만들지 않았다. 내외곽 postpass는 B cap151 crop correction168→32지만 M150→148(#59/#144), cap55도 M54→53이므로 그대로 비채택한다. 원본 반례는 같은 단면의 외곽 대표 이동에 의한 대응 손실이며 이웃 단면 오통합으로 단정하지 않는다. Task6에 대표 중심/관계 계약으로 넘긴다.
- C0#25는 실제 단면 근처의 밝기 분할을 잡으나 circular keypoint를 ellipse 외곽으로 볼 수 없다. 고정 threshold 관측도 전체 경계/안정성을 입증하지 못했다. C/MSER/합집합은 확대하지 않는다.
- B native: source/bridge/4ABI binary와 final logs hash를 확인해 기존 호출·반복·예외 근거를 재사용했다. 오래된 source-build index의 derived metadata5건은 마지막 보존본과 달라 새 current index로 구분했다. 실제 source/binary가 바뀐 것으로 해석하지 않는다. gray-only LUT 경로는 조사만 했고 source 수정/재빌드는 하지 않았다. BSS mapping·RSS snapshot·peak를 구분하며 대표 기기 비용은 Task7에 남긴다.

아래는 **같은 desktop 공통 후처리**의 correction이며 R0 최종 품질 결과와 같은 열에서 비교하지 않는다. cap151 값은 출력 제한 전 진단이다.

| sample | 공통 A full/crop | 공통 B full/crop |
|---|---:|---:|
| cap151 |398/199 (둘 다 COUNT_LIMIT)|239/168 (둘 다 COUNT_LIMIT)|
| cap55 |102/77|40/42|
| metal40 |222/173|41/36|
| metal18 |29/16|7/8|
| metal37 |211/175|36/36|

모든 고정 정책 민감도에서도 이 공통 비교의 B 개선 방향은 유지됐다. cap55 geometry 회수46→54와 후보 부담 감소, 기존 JNI feasibility는 **개발 착수 근거**다. B raw metal37 회수23/37(A30/37), 전체 미동결 주석과 cap151 중복은 남은 위험이다. B가 R0 완제품보다 우수하거나 현장 합격이라고 선언하지 않는다.

**Task4 최소 계약/후속 파일:** I/EdgeDrawingCandidates.kt는 canonical geometry와 출처를 반환하고 동일 gray의 measured support를 따로 관찰한다. 없는 fitError는 미측정이며 NFA를 edgeSupport로 넣지 않는다. support 내림차순→canonical geometry→source ID로 대표 anchor를 고정한다. 기존 simple gate를 재현하되 내외곽 postpass는 연결하지 않는다. I/CandidateExtractor.kt·DetectionConfig.kt·CompositePipeDetector.kt에 선택 경로/버전을 두고 M/DetectionGeometry.kt에 geometry 수치만 받는 순수 toMarker overload를 추가해 기존 EllipseCandidate overload가 같은 계산에 위임하도록 한다. M/CandidateMerge.kt의 R0 순위는 보존한다. 시험 native/Gradle 패키징 위치는 후속 요청에서 확정한다. 이 변경을 이번에 구현하지 않았다.

회귀:6값/축swap/각도/invalid, 실제scale·EXIF·ROI·Float·radius, 같은rim/이웃/연쇄/capless/작고흐린/단절/편평/몸체/blank, desktop↔Android 공통 gate/support/order, trace on/off와 실패 보존, JNI 반복/예외 해제·late result·직렬 소유, 제품 APK native 포함 여부. Task7은 S22+ cold/repeat/peak/취소·흐름·5초, Task8은 독립 세션/묶음·검토된 주석·전체 검수다. 배포 고지는 배포 전 완성한다. 이들을 모두 Task4 선행으로 추가하지 않는다.

착수 판단을 바꾸는 조건: 검토된 주석에서 B의 공통 이득 소멸, metal37의 실제 경계 생성 손실이 제한된 원인 교정으로 해결 불가, 설명되지 않는 capless/작은 대상 회귀, Android parity 실패, 실제 crash/OOM/소유권 결함·감당 불가능한 비용. 다음 단계는 명시적 후속 요청으로 시작하며 이번에는 커밋·원격 작업·정식 통합을 하지 않는다. [이번 검증과 증거 상태](../../verification.md#원본-주석-검토와-task4-착수-판단--2026-10-14)를 따른다.

### 사용자 중심·번호 정정에 따른 v6 재평가 — 2026-10-14

사용자가 cap151 전체 중심의 하향 치우침과 metal37의27/28·30/31·35/36 중복, 번호 없는 실제 단면3곳을 정정했다. 이 정정은 v5의 전체301 중심 승인과 위 국소 확인 대기 상태를 **해당 범위에서 대체**한다. v5 원본/주석/결과는 보존하고 v6 초안을 별도로 작성했다.

cap151은 일괄 평행 이동하지 않았다. 검출 결과를 보지 않고 원본 황색 외곽의 방사형 경계/ellipse 주석 보조 제안을 만든 뒤151개 확대 crop을 개별 시각 검토했다. 캡 없는11은 금속 외곽을 수동으로 읽었다. 이는 annotation aid이며 검출 변경·색상 필수 mask 도입이 아니다. 자동 출력만으로 검토 완료/사용자 승인을 선언하지 않는다. metal37은 원본 픽셀에서 중복 쌍의 실제 단면3개를 다시 읽고 다른 번호3개를 번호 없는 단면3곳에 재연결했다. 표시 번호28/31/36은 새 연결 선택이며 원래 번호사진이 그 위치를 표시했다고 가장하지 않는다. 확정151/55/40/18/37·총301개를 유지했다.

새 첨부본은 별도 hash로 보존하고 원본과1129 inlier/중앙 잔차 약.72px의 affine 대응을 확인했다. 새 좌표는 주석 없는 원본에서 읽었다. 이전 별도 묶음 S1/S2 제외 원인과 이번 전경 내부3단면을 혼동하지 않는다. 새151cap+6metal 좌표는 **AI 재검토 DRAFT**이며 사용자 수정 지적을 새 수치 전체 승인으로 확대하지 않는다. 다른144개 중심은 그대로다. metal6의 b는 원본 외곽의 새 수동 제안으로 기록하고 같은 P0 규칙을 적용했다. b/u/허용치·전체 동결은 미완료다.

실제 기존 Android schema2 함수로5sample/301과full/crop10sample/602를 검증하고 DRAFT의 frozen=true 거부를 확인했다. 같은 R0/Android A/B 생성 geometry와 고정 공통 desktop 후처리를 v6으로 재평가했다. 새 검출 실행·후처리 튜닝·신규 변형은 없다.

| sample/scope | v6 R0 final M/FN/FP/correction | 공통 A/B correction (R0와 별도) |
|---|---|---|
| cap151 full |COUNT_LIMIT406; fit/geometry/quality151|396/237 (둘 다 제한 전 진단)|
| cap151 crop |151/0/124/124; 각 후보단계151|197/166 (둘 다 제한 전 진단)|
| metal37 full |17/20/37/57; fit31→geometry19→quality17|207/32|
| metal37 crop |17/20/35/55; fit31→geometry19→quality17|171/32|

다른3사진 결과는 그대로다. 값의 변화는 **주석 정정**이며 검출 성능 개선이 아니다. 새 대응·FN/FP ID를 외부 버전별로 보존했다. v5 FP126 분류43/33/27/13/10과 FN 원인은 역사적 자료이며 v6의 FP124에 합계를 그대로 적용하지 않는다. center 정정으로 실제 대응 detection 집합도 바뀌었다.

고정 E-O1의 cap151 M 손실150→148/#59/#144는 v6에서 사라졌다(full/crop 모두M151). 따라서 그 둘을 검출의 물리적 누락/오통합 반례로 사용하는 판단을 대체한다. B crop의 같은 postpass는 v6 correction166→26이며 cap151 개발 가능성은 남지만 cap55 M54→53 손실은 여전해 정책을 그대로 채택하지 않는다. Task6에 넘길 반례는 cap55와 대표 중심 계약이다. C0 center-only 고유 대상25는 그대로이며 경계·안정성 조건도 그대로다.

공통 B의 개발 이득과 기존 Android 연결 근거로 **조건부 B/Task4 착수 가능** 판단을 유지한다. 정답 전체 동결·현장 인수나 B 정식 구현 완료는 아니다. Task4~8·제품 연결·커밋·원격 작업은 실행하지 않았다.
#### 보존한 v5 과거 실행 기록 — 이번 v6 재실행 결과 아님

- 이전 v5 실제 검증: JVM57 재실행/실패0, 관련 Android46 통과, schema2 검증5장/301개·10행/602개 및 frozen 거부, R0 평가10행, native B의 gray10행/합성9종. 마지막 fresh review의 Important1(mask 상세 저장 실패 시 다음 sample 중단)을 RED→GREEN으로 수정하고 같은 suite를 확인했다. 코드 후 graphify update는2319 nodes/4140 edges/136 communities, git diff --check 통과. Hough·무관한 전체 실험·Task4~8·제품 연결·커밋·원격 작업 없음.

파일·정확한 수치·언어/실행 경계·미검토 항목과 Task4 최소 변경 후보는 [후속 검증 기록](../../verification.md#개정-3-task-13-후속-실행--2026-10-14)을 따른다. 부분 증빙을 전체 Task 체크박스의 인수 완료로 올리지 않는다.

#### 이번 v6 요인 분리 결과

metal37은 v5 t 고정/중심·ID만 수정하면 R0 M16(full/crop), 새 외곽 b 제안/P0까지 반영하면 M17이다. 공통 B는 둘 다M19/correction32다. 따라서 중심 수정 효과와 새 b/t 제안 효과를 각각 기록하며 M15→17을 중심 수정만의 효과로 보고하지 않는다.


## Task 4 Android B 실행 — 2026-10-14

최신 사용자 요청으로 Task4를 직접 순차 실행했다. 기존 feat/auto-counting/89bfa63와 사용자 변경을 유지했고 커밋하지 않았다. **Task4 구현·Android 동등성·시험 APK 격리 검증 완료**, 정확도 개선 튜닝/제품 인수는 아니다. 별도 `EdgeDrawingEvaluationDetector`가 기존 PhotoStore/JPEG95/gray/ROI/pixel budget을 재사용하고 R0 detector/quality/merge를 보존한다. `EdgeDrawingCandidates`의 measured support·anchor는 androidTest에 두고 main은 geometry-only `EllipseGeometry`/toMarker 위임만 추가했다. Native source/bridge와 Gradle generated jniLibs는 시험 APK에만 연결했다.

Task4 체크 결과: raw6 반축/degree/swap/modπ/invalid, geometry/quality 분리, support72점/밖unsupported, 고정 anchor·출처·연쇄 보존, per-axis scale/ROI/radius/Float, 기존 EXIF8방향, native 반복/할당후예외/재시도/전후취소·late copy 폐기, bounded trace·schema1/metric-v1·runner실패 보존 통과. B로 합성9종을 실제 실행해 기존 Android 생성 geometry를 재현했다. flat ellipse는 raw2→gate0, 작은blur는raw2→gate1, fragmented는raw1→final1,blank/body arc는0이며 합성 성공을 현장 성능으로 확대하지 않는다.

동일hash gray10행의 고정 geometry→native실행→PhotoStore실행을 비교했다. gate 전/후 목록, support, 대표/source/order 일치. 최대geometry오차8.881784197001252e-16(<사전1e-9),Float marker 차이0(<사전1e-7). 예측 matching tPx는 이 오차와 별개인 v6 P0 고정값이다. JPEG95 import/ROI pixel hash가 기존과 같고 다섯 입력은 축소없음. 무손실 gray probe와 실제 JPEG95 경로를 별도로 검증했다. main/test APK 실제native·DEX검사에서 새 B/bridge/OpenCV는 test에만 있고 기존 AAR의4ABI 한배포본만 연결됐다(arm64 실행,나머지build-only). 원본소스/LUT/BSS 최적화 없음.

v6 common desktop B와 Android B의10행 M/FN/FP/correction은 같다. cap151 full388/crop317은 COUNT_LIMIT,정상final 없음;제한전 진단만237/166 correction이다. 다른8행SUCCESS,trace-off marker/총량일치. R0 final은 다른quality/merge 조건이므로 우열표로 섞지 않고 별도로 재사용했다. FAIL/COUNT_LIMIT2행과traceOffSKIP2행을 보존했다. 모든 결과는development/tuning,151cap+6metal AI DRAFT/144중심불변/301개/b/u/t미동결이며 excluded로 출력을 제거하지 않았다.

후속소관: Task5 새로운품질특징,Task6 내외곽(E-O1cap55M54→53),Task7 S22+ peak/속도/흐름,Task8 독립정답/전체검수인수. cap151#59/#144의과거손실은v6에서사라져반례로재사용하지않는다. C0#25는center-only로유지하며C/MSER/union추가없다. v6 FP124는v5FP126분류를재사용하지않고UNRESOLVED로둔다. 완료증빙·회귀수·현재제한은 [검증기록](../../verification.md#task-4-edgedrawing-android-평가-경로--2026-10-14)을 따른다.


Task4 완료 체크는 최신 사용자 고정 계약의 세 비교(R0 final / 기존 Android 생성+desktop 공통 / 새 Android B)에 대한 것이다. 별도 desktop native 생성기의 모든 플랫폼/ABI 동등성을 확대해 주장하지 않는다. 시험 ABI는 기존 AAR와 같은4개로 고정했으며 실제 실행은arm64만 확인했다. 새 source/실제 config/input/annotation/output hash와 재현명령을 외부증빙과ledger에보존했다.


### Task4 소유권 후속 수정 — 2026-10-15

Fresh review가원본native 내부 raw 소유권 예외와NFA교체누수를확인해Task4완료선언을일시보류했다. 동일경로 실제RED(정상/후속할당실패각live delta2)→최소std::unique_ptr소유권패치→고정fixture1,641개할당실패전체/live delta0·관련Android50 GREEN으로해결했다. 일반소스/격리입력/주석/geometry정책/LUT/BSS는유지하며9합성·10행B재실행동등성은불변이다. 최종source는upstream4.12+ownership1이며원본과패치/새hash를분리한다. 새reviewer의최종보고서는사용량제한으로미완료,작성자수정diff검토를따로기록한다. 핵심Task4계약과이검토제한을구분하고후속5–8로자동진행하지않는다. [소유권후속검증](../../verification.md#task4-소유권-회귀-수정과-최종-검증--2026-10-15)을따른다.


### Task4 최종 독립 검토와 Task5 품질 관측 — 2026-10-15

Task4 최종 ownership1 코드를 새 읽기 전용 검토자가 실제 source/diff/hash/APK/로그에서 독립 검토했다. Critical0/Important0/Minor0, 완성 보고서를 새 외부 task5-v1에 보존했다. 이전 reviewer 중단 기록은 역사로 유지한다. 기존162 artifact/current source hash 일치와50 Android/59 JVM의 과거 실행 범위를 확인했으며 이숫자를 이번 새 실행으로 올리지 않는다.

Task5는 B 전용 androidTest CandidateQuality와 고정10행/9합성 평가 runner를 추가했다. generator/geometry/gate/72점support/anchor/order/same-rim/입력/주석은 고정했다. longest unsupported eccentric-angle gap, invalid/inside unsupported/distinct raster sample/maxstep을 관측하며 법선·gradient·잔차·NFA 대체점수는 추가하지 않았다. 기본 policy null은 기존 경로이고 관측·gap90/gap120은 명시적 개발 ablation만이다. 새 policy는 invalid/undersampled를 거절하지 않는다.

고정 B의10행 전체에서 관측만의 geometry/집계/대표/source/Float/대응은 Task4와 같다. gap90은 cap151 full/crop correction237/166→236/165,cap55 40/42→39/41,metal40 41/36→39/34,나머지불변,새 target 손실0·대응변경0이다. 그러나 cap151#25/crop24·cap55#50/crop51은 원본의 정상 cap외곽이다. 내부 rim이 이미 target을 담당해 수치상FP가줄었을뿐,정상내외곽을가짜품질로제거하는정책은채택하지않는다. gap120은전행불변이다. **두거절정책보류,관측도구만유지**. 기존cap151388/317COUNT_LIMIT는gap90에서도387/316으로유지,정상final없다. 전체표·비용·한계는[이번검증](../../verification.md#task4-최종-fresh-review와-task5-품질-관측--2026-10-15)을따른다.

새실행은명시3클래스16 Android PASS(품질5+고정평가1+B기존10,소유권1,641/취소/실패/재시도포함),시험APKbuild·Spotless·Lint/앱build성공,JVMtask는UP-TO-DATE로기존59재사용이다. 원본native/main/Gradle불변과실제main/testAPK4ABI nativeSHA·격리재확인. Task5의관측/비채택실험완료이며정확도합격이나품질정책통합완료가아니다. Task6의물체통합,7의S22전체자원/흐름,8의독립인수로자동진행하지않는다.


### Task6 조건부 내외곽 실험과 보류 — 2026-10-15

최신 사용자 승인으로 Task6만 직접 순차 실행했다. 같은 feat/auto-counting/89bfa63과 기존 dirty 변경을 보존하고 새 브랜치/커밋/원격 작업을 하지 않았다. Task4 최종 fresh0/0/0·Task5최종0/0/1은 완료된 과거 기록으로 유지한다. 기존162/146/v6 360 artifact SHA를 확인해 재사용했다.

기존 B same-rim .1/.1/5deg/near-circle1.1·support→geometry→ID·anchor/source를 그대로 재사용했다. unbounded O(n²) 탐색에 각 단계1,000,000pair 예산을 추가했고 초과는 CANDIDATE_SATURATION이다. B전용 androidTest BObjectMerge와 선택적 평가 detector연결·3개시험파일만 추가/변경했다. main/native/Gradle/R0/DI/PhotoStore/schema는 불변이다. Task5거절은 비활성이다.

두 공통 영상 관계 변형 ALL72/72·90은65/72 coherent annular spokes를 시험했다. 포함/중심만으로 통합하지 않으며 제3후보/영상·ROI밖/불충분한 영상은 비병합, 대표는 기존 후보순서와 geometry를 보존한다. 추가변형/사진별분기/refit/새generator/hierarchy는 없다. 원본138고유pair를8atlas에서 같은캡 내외곽으로 관찰했고 이subset 이웃오통합0·matched target손실0, provenance를 모두 보존했다. 과거 E-O1 cap55 target028의 outer대표 대응손실은 inner대표를 유지해 발생하지 않았다. cap151#59/#144의 v5 해석은 되살리지 않았다.

고정 후보10행×3정책=30비교, 새 실제 PhotoStore/native10행×2정책=20실행 모두SUCCESS. cap151 기준388/317COUNT_LIMIT에서ALL300/229,90은272/201이 되어 실제marker반환을 확인했다. correction은cap151237/166→149/78→121/50,cap5540/42→34/35→24/25,금속6행불변이다. 중심/허용치/scope는 고정했고표시대응ID/반경변화를 별도 기록했다. 전체 M/FN/FP/correction·개수·상태는 [Task6 검증](../../verification.md#task6-내외곽-통합-실험과-보류--2026-10-15)을 따른다.

**두 물체통합 정책은 보류한다.** parent+두 aperture가 후보3개면 둘을 보존하지만 parent도남아3개이며 요구한 정확히2개를충족하지 않는다. 밝은띠와생성된후보만으로 모든 후퇴/별도aperture의 물체ID를보장하지 않는다. 데이터를잃는 parent-only 정책으로 통과시키거나 새특징을누적하지 않았다. 이는 Task6비채택 실험 완료이며 정책채택/제품성능개선완료가 아니다. 기본objectPolicy=null/qualityPolicy=null을 유지한다.

새Android19 PASS(물체6+실제detector고정geometry계약2+고정/native비교1+기존B10), build/Spotless/Lint성공. JVMtaskUP-TO-DATE는과거59재사용. 새소유권audit1641/잔존0과실제main/test APK4ABI nativeSHA격리/단일AAR를확인했다. trace0/3/full의geometry·집계·출처는보존, 일부상세trace는예산으로미보존돼완전sourceunion의직접링크로보완하고spoke값을만들지않았다. 후처리/object시간3회중앙값은emulator측정이며S22/전체peak/5초입증아니다. 최종독립검토와graphify결과는검증이력/ledger최신기록에확정한다. Task7~8/제품연결/커밋/원격/업로드미실행. v6 DRAFT/frozen=false·개발5사진의한계불변.

Task6 최종독립freshreview완료 Critical0/Important0/Minor1. Minor는외부pair증빙의검토상태동기화로보류하며확정결함수정없다. graphify AST2841/5207/146·diff검사통과. parent+두aperture미충족과정책보류를유지한비채택실험완료다. 같은rim의기하분류와물리same-edge확정은구분하며금속좁은wall의정확한경계정체성은UNRESOLVED로남긴다.


### Native 원본→segment→arc→fit 진단 — 2026-10-16

문제4개+matched2개를 실제 Android standalone trace와 원본으로 연결했다. metal37 028은 아래rim segment4/5의80/98점 짧은 원호와 위rim segment6/10의118/136점 타원호가 생성되지만, 서로 다른 segment의 JoinArcs3 원반경 proxy 조건 `diff > minR`로 결합되지 않고 각 coverage(.129/.165/.342/.440)로 후보 등록에서 탈락한다. 위 두 원호끼리의 overlap 거절은 실제 지지점도 위쪽에 겹쳐 임의 제거하지 않았다. 018/034는 rim을 따르는 유효 raw fit과 DRAFT 중심 차이를 분리했고 metal40 020은 부분 원 fit이 반대 경계를 가로지르는 반례다. 주석/t 변경과 물리 미생성 확정은 없다.

`scripts/native_stage_trace.py`는 정답 없이 현재 source의 외부 진단 복사본을 생성한다. 앱/native 기준선은 v2-even-sampling FPF·a<=80·quality/object=null을 유지한다. 최종 기존 gray3행 on/off raw 동일, 새 PhotoStore/JNI3행 marker·상태·metric 동일/U/L/A/R0. 검출 정책 수정은 연결 안전성 근거가 부족해 보류했고, 다음 판별은 알려진 단일 타원의 상·하 분절과 인접 두rim 합성에서 원반경 proxy의 오류/정상 거절을 구분하는 것이다.

관련 새 빌드/포맷/Lint·JVM59·Android native/계약12와 선택 JNI시험1, 소유권1641/liveDelta0을 확인했다. 임시 runner 개수 assertion/포맷 실패와 수정 후 재실행을 구분한다. 임시 runner는 외부에 보존하고 repo에서는 제거한다. 독립 코드 검토·Join support 소실 제한·standalone RSS/시간 한계·새/과거 재사용 범위는 [검증 기록](../../verification.md#native-단계-연결-진단--2026-10-16)과 이번 외부 native-trace-v1 REPORT/final-verification에 남긴다. 제품/정답/현장 인수와 커밋·원격 작업 없음.


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
