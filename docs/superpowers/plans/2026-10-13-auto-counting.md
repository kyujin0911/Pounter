# 비AI 자동 계수 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인터뷰에서 확정한 한 묶음 촬영 조건에서 비AI 자동 계수를 검증하고 기존 보정·저장 흐름에 안전하게 연결한다.

**Architecture:** 기존 단일 app 모듈·PipeDetector·Repository·entry ViewModel·Koin 연결을 재사용한다. 순수 Kotlin의 좌표·통합·영속 계약과 androidTest의 평가용 OpenCV 검출을 먼저 구현하며, 현장 인수 전 일반 앱은 검증 대기 구현체를 유지한다. 결과 저장은 기존 편집 저장 대기 경로로 통합한다.

**Tech Stack:** Kotlin, Compose, Room, Navigation Compose, Koin 4.1.1, coroutines, JUnit4, OpenCV 4.12.0(androidTest 우선). 의존성 전체 업그레이드 없음.

**Spec:** [승인된 자동 계수 상세 설계](../specs/2026-10-13-auto-counting-design.md). 사용자 문서 승인일: 2026-10-13. 사진은 참고 자료이며 인터뷰 확정 사항이 우선한다.

## Global Constraints

- 정면에 가까운 한 묶음 촬영, 돌출/후퇴한 식별 가능한 작은 단면 포함. 한 평면·동일 픽셀 크기·규칙 배열을 전제하지 않음.
- OpenCV 원·타원 후보 + 영역 보완 + 단면 검증 + 중복 통합. AI·외부 추론·숨은 개수 추정 제외.
- 배경 자르기 선택. 사진 배경만으로 필수 영역 선택·여러 묶음 분리·색상 조건을 추가하지 않음.
- 노란 단면과 금속색 단면 모두 포함. 같은 단면의 안/밖 경계는 표시 하나.
- N > 0인 사진별 누락 추가 + 오검출 삭제 ≤ `max(1, floor(N × 0.05))`. 보정 후 정답 일치. 300개 지원 목표·표시 생성까지 5초 목표는 미달성 상태.
- 후보 포화·300개 초과를 정상 300개로 잘라내지 않음. 실제 0개와 처리 실패 구분.
- 사진 원본·오버레이·개인 경로·민감값은 Git에 포함하지 않음. 기존 기록 초기화·파괴적 마이그레이션 없음.
- 앱/DB 식별자·기존 JSON key·Navigation 인자·복원 key 유지. 새 메타데이터만 선택 필드로 추가.
- 계획 작성 승인은 구현 실행 허가가 아님. 사용자 계획 검토와 실행 방법 선택 후 구현. push·PR·병합은 별도 범위.

## Review Focus

1. 저장 성공 직전 취소/화면 종료: 완료된 DB 쓰기를 거짓 실패로 취급하거나 같은 결과를 중복 저장하지 않음 → Task 5.
2. 이전 저장 대기 스냅샷: 메타데이터 없이도 복원되고 새 검출 결과로 오인되지 않음 → Task 1·5.
3. 이중 테두리와 밀집한 이웃 단면: 같은 단면은 통합하되 이웃 단면은 유지 → Task 2·4.
4. 사진 방향·부분 영역·축소가 함께 적용된 입력: 표시가 같은 단면 중심에 복원됨 → Task 2·3.
5. 취소된 네이티브 처리 뒤 즉시 재분석: 동시 실행·자원 누수·오래된 표시 반영 없음 → Task 4·5.

## 작업 상태·분기·실행 경계

작성 브랜치는 기존 동일 목적의 `docs/auto-counting-design`을 재사용한다. 기준 코드는 상세 설계의 `478cb50`이며 문서/사진 정정은 `58d4f4c`까지 포함한다. 현재 미추적 자산·graphify 파일은 사용자 작업으로 보존한다.

구현 실행 시 `using-git-worktrees`로 격리 여부를 확인하고 승인 계획이 포함된 시작 SHA에서 `feat/auto-counting` 목적 브랜치로 분리한다. 실제 Issue가 있으면 조회해 번호를 포함하며 임의 번호를 만들지 않는다. 원격 Issue 생성·조회·push는 요청 범위에 따라 수행한다. 선행 구조/Koin 변경이 main에 반영됐는지 실행 시 확인하고 미병합 의존을 기록한다.

Task 1~5는 실제 정답 자료가 미확보여도 대역·합성 자료로 개발 가능하다. Task 6의 튜닝·현장 인수는 정답과 세션 구분이 필요하다. Task 7은 Task 6 합격 근거 없이는 실행하지 않는다. 초기 자료 수만으로 독립 현장 인수를 대신하지 않는다.

모든 실행 Task: 실패 테스트 → 최소 구현 → 관련 테스트 통과 → `spotlessCheck`·diff 리뷰 → 해당 파일만 로컬 커밋. 코드 변경 후 `graphify update .`로 갱신하고 기존 graph 산출물의 저장소 정책을 따른다. 아래 예상 결과는 계획이며 실행 기록이 아니다.

## 파일과 인터페이스 배치

이하 M=`app/src/main/java/io/github/kyujin0911/pounter`, U=`app/src/test/java/io/github/kyujin0911/pounter`, I=`app/src/androidTest/java/io/github/kyujin0911/pounter`. 파일 경로는 해당 접두사 기준이다.

| 파일 | 변경·책임 |
|---|---|
| M/core/model/AnalysisMetadata.kt (신규) | 직렬화 가능한 처리 버전 |
| M/core/model/CountTask.kt, M/data/local/database/TaskCodec.kt | 선택적 메타데이터·과거 형식 읽기 |
| M/data/detector/Detection.kt, DetectionFailure.kt (신규) | 성공/실패 계약 |
| M/data/detector/DetectionGeometry.kt, CandidateMerge.kt (신규) | 순수 Kotlin 후보 형상·역변환·통합 |
| M/data/local/photo/PhotoStore.kt, AnalysisPhoto.kt (신규) | 분석 입력의 실제 크기·자원 소유권 |
| I/data/detector/CompositePipeDetector.kt, CandidateExtractor.kt, DetectionConfig.kt (신규) | 평가용 OpenCV 구현·후보 추출·튜닝 구성 |
| M/feature/count/CountViewModel.kt, CountUiState.kt, CountWorkScreen.kt | 유효성·저장 대기·실패·대기 문구 |
| I/DetectionEvaluationTest.kt, DetectionAcceptanceTest.kt (신규) | 실사진 실행·판정. 원본/정답 파일은 외부 입력 |
| docs/auto_counting.md, docs/verification.md | 실제 선택 수치·실행 결과·미검증 기록 |
| M/di/AppModule.kt, M/feature/shared/ValidationNotice.kt, app/build.gradle.kts | Task 7 합격 이후의 일반 앱 제공 |

신규 타입은 각 Task에서 정의한다. OpenCV 타입과 평가 설정은 Task 7 전까지 main에 유입시키지 않는다. 새로운 공통 프레임워크·단계별 인터페이스를 만들지 않는다.

### Task 1: 성공 결과·실패·기록 호환성

**Files:** 위 `AnalysisMetadata`, `CountTask`, `TaskCodec`, `Detection`, `DetectionFailure`; U/ModelTest.kt; I/AnalysisMetadataInstrumentedTest.kt(신규).

**Interfaces:** `@Serializable data class AnalysisMetadata(val detectorVersion: String)`; CountTask 끝에 `analysisMetadata: AnalysisMetadata? = null`; Detection 끝에 같은 nullable 필드 추가. `enum class DetectionFailureReason { PHOTO_READ, INITIALIZATION, INVALID_INPUT, PROCESSING, CANDIDATE_SATURATION, COUNT_LIMIT }`; `class DetectionFailure(val reason: DetectionFailureReason, cause: Throwable? = null) : Exception`.

- [x] 실패 테스트 작성: `legacyPendingSnapshotHasNoMetadata`는 기존 JSON을 `Json.decodeFromString<CountTask>`로 읽고 `assertNull(task.analysisMetadata)`. `resetClearsAnalysisMetadata`는 `assertNull(task.reset(Region()).analysisMetadata)`. `metadataRoundTripsWithoutChangingMarkerIds`는 재열기 후 ID·automatic·markers·버전 동일, 기존 payload는 null. 기존 v1 SQL fixture 재사용.
- [x] `./gradlew testDebugUnitTest --tests '*ModelTest'` 실행. 새 속성/규칙 부재로 실패 확인; 환경 실패는 기능 실패로 보고하지 않음.
- [x] 위 계약 구현. TaskCodec은 새 `analysisMetadata` key 누락/null을 허용하고 기존 필드·Room v1 유지. Detection의 default null로 기존 대역 호환. 예외 메시지는 UI에서 reason으로 매핑.
- [x] 같은 단위 테스트와 `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.AnalysisMetadataInstrumentedTest'` 실행. 실패 0, 기존 SQL/JSON 호환 확인.
- [x] diff/스테이징 검토 후 `feat(count): 분석 결과 메타데이터와 이전 기록 호환 추가` 로컬 커밋.

### Task 2: 후보 통합과 사진 좌표 복원

**Files:** M/data/detector/DetectionGeometry.kt, CandidateMerge.kt; U/DetectionGeometryTest.kt, CandidateMergeTest.kt(신규). 기존 MarkerDeduplication은 Hough 비교용으로 유지.

**Interfaces:** `CandidateSource { CONTOUR, REGION }`; `EllipseCandidate(centerXPx: Double, centerYPx: Double, semiMajorPx: Double, semiMinorPx: Double, angleRadians: Double, sources: Set<CandidateSource>, edgeSupport: Double, fitError: Double, contrast: Double)`; `AnalysisTransform(photoWidthPx: Int, photoHeightPx: Int, cropLeftPx: Int, cropTopPx: Int, scaleX: Double, scaleY: Double)`(scale은 분석 px/사진 px).

`toMarker(candidate: EllipseCandidate, transform: AnalysisTransform, markerId: String): Marker`; `mergeCandidates(candidates: List<EllipseCandidate>, policy: MergePolicy): List<EllipseCandidate>`; `MergePolicy(centerToleranceRatio: Double, maxBoundaryGapRatio: Double, minContainmentFraction: Double)`. 테스트 구성은 합성 검증용이며 현장 기본값으로 승격하지 않음.

- [x] 실패 테스트 작성: `cropAndScaleRestoreCenter`는 1000×800 사진, crop (100,80), 배율 (0.5,0.5), 분석 중심 (50,40) → `assertEquals(0.2f, marker.x, 0.0001f)`·y=0.2. `nestedRimsMergeOnce`는 동심 안/밖 후보 → size=1. `neighboringPipesRemainSeparate`는 가까워도 포함 관계 없는 두 단면 → size=2. `invalidGeometryIsRejected`는 NaN/음수 축/잘못된 배율 거부.
- [x] `./gradlew testDebugUnitTest --tests '*DetectionGeometryTest' --tests '*CandidateMergeTest'`로 실패 확인.
- [x] 계약 구현. 순서는 경계 포함 비율 + 상대 중심 거리 + 경계 차이로 동일 단면 판정, 경계 지지가 높은 대표 후보를 선택하고 source 집합 통합. 중심 거리만으로 합치지 않음. 전이적 연쇄로 이웃이 합쳐지지 않도록 새 후보를 실제 대표 경계와 비교. 비등방 배율에서는 타원의 회전/축 행렬을 x/y 역배율로 변환한 뒤 짧은 반축을 구하고 사진 너비로 정규화; 중심과 반경 모두 비등방 배율 테스트 포함.
- [x] 같은 명령으로 모든 assertion 통과. 입력 순서 변경 시 통합 개수·대표 선택의 결정성도 확인.
- [x] 관련 파일만 `feat(count): 후보 중복 통합과 전체 사진 좌표 복원` 로컬 커밋.

### Task 3: 분석용 사진과 입력 손실 검증

**Files:** M/data/local/photo/PhotoStore.kt, AnalysisPhoto.kt; I/AnalysisPhotoInstrumentedTest.kt(신규).

**Interfaces:** `AnalysisPhoto(bitmap: Bitmap, photoWidthPx: Int, photoHeightPx: Int)`는 `AutoCloseable`, close가 소유 Bitmap 해제. `suspend fun PhotoStore.analysisPhoto(name: String, maxSidePx: Int, maxDecodedPixels: Long): AnalysisPhoto`. 분석용 함수는 preview와 별도이며 실제 decode 치수를 반환. 변환은 Task 2 AnalysisTransform 사용.

- [x] 실패 테스트 작성: `allExifOrientationsPreserveLandmarks`는 합성 표식 사진의 EXIF 1~8을 가져온 뒤 예상 위치/사진 크기 일치. `analysisUsesOwnResolution`는 preview와 별도 maxSide로 읽은 실제 치수 확인. `oversizedInputIsExplicitFailure`는 픽셀 예산 초과 → INVALID_INPUT. `analysisPhotoReleasesOwnedBitmap`는 close 후 해제 확인.
- [x] `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.AnalysisPhotoInstrumentedTest'` 실행해 실패 확인.
- [x] 인터페이스 구현. bounds를 먼저 읽고 축소/예산 검증 후 IO에서 decode. 방향 보정은 기존 import에서 한 번 수행하며 재적용하지 않음. 기존 import 정책은 우선 유지. 사진 크기·샘플 배율은 원본과 수신 첨부를 구분.
- [x] 같은 클래스 통과. Task 6에서 가져오기 손실이 확인되면 이 Task의 기존 테스트를 유지한 채 import 정책을 최소 수정하고 메모리/EXIF/기록 재열기를 다시 검증. 기존 저해상도 사진은 원정보 복원으로 취급하지 않음.
- [x] `feat(photo): 미리보기와 구분된 분석 입력 제공` 로컬 커밋.

### Task 4: 평가용 복합 검출과 취소 직렬화

**Files:** I/data/detector/CompositePipeDetector.kt, CandidateExtractor.kt, DetectionConfig.kt; I/CompositeDetectorInstrumentedTest.kt(신규). OpenCV 의존성과 일반 AppModule 등록은 그대로 유지.

**Interfaces:** `CompositePipeDetector(photos: PhotoStore, config: DetectionConfig) : PipeDetector`; `override suspend fun detect(task: CountTask): Detection`. `object CandidateExtractor`의 `extract(gray: Mat, config: DetectionConfig): List<EllipseCandidate>`는 Task 2 후보를 반환하고 경계 점수/fitError/contrast 기록.

`data class DetectionConfig` 필드: `detectorVersion: String`, `analysisMaxSidePx: Int`, `maxDecodedPixels: Long`, `maxCandidates: Int`, `medianKernelSize: Int`, `cannyLow: Double`, `cannyHigh: Double`, `adaptiveBlockSize: Int`, `adaptiveOffset: Double`, `minSemiAxisPx: Double`, `maxSemiAxisPx: Double`, `maxAxisRatio: Double`, `minEdgeSupport: Double`, `maxFitError: Double`, `minContrast: Double`, `mergePolicy: MergePolicy`. 홀수 커널·유한값·양수 범위 검증. 합성 시험값과 Task 6의 고정 현장 구성은 분리하며 검증되지 않은 값을 제품 기본값으로 쓰지 않음.

- [x] 실패 테스트 작성: `ringsAndMetalEllipsesProduceOneMarkerEach`는 노란/무채색 합성 타원·이중 경계가 각각 표시 하나. `emptyImageReturnsZero`는 markers 비어 있음. `saturationAndCountOverflowAreDistinct`는 후보 상한/301개 최종 후보가 각각 CANDIDATE_SATURATION/COUNT_LIMIT. `cancelThenRestartHasOneNativeOwner`는 네이티브 단계 테스트 훅의 activeCount 최대=1, 이전 결과 미반영, finally 실행 후 다음 실행. `initializationFailureIsNotZero`는 초기화 실패 사유 확인.
- [x] `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.CompositeDetectorInstrumentedTest'` 실행해 실패 확인. 합성 성공을 실사진 성능으로 보고하지 않음.
- [x] 구현: Default dispatcher + singleton 내부 Mutex, 단계별 ensureActive, IO 사진 읽기, gray/median/Canny/findContours/fitEllipse 형태 후보, adaptiveThreshold의 양 극성/연결 영역 보완 후보. fitEllipse의 입력 점 부족·범위 밖 결과는 개별 후보 탈락으로 기록. edgeSupport는 타원 둘레 표본 중 인근 경계가 있는 비율(0~1), fitError는 피팅 점의 정규화 타원 반경과 1의 평균 절대 차이, contrast는 경계 안/밖 밝기 평균 차이의 절댓값(0~255)으로 정의. 이 근거로 검증 후 Task 2 통합. 내부가 어둡다는 단일 조건·노란색 필수 조건 없음. 후보 상한에 도달하면 처리 중단; 300 초과면 잘라내지 않고 실패. 첫 초기화와 전체 검출 시간 별도 측정, 모든 Mat/Bitmap finally 해제.
- [x] 같은 기기 테스트 통과. Hough는 기존 클래스에서 기준선으로만 유지. CLAHE/Watershed/분할/색상 보완은 Task 6의 실제 오류 근거가 있을 때 별도 작은 시험으로 추가하고 고정 버전에 기록.
- [x] `feat(count): 평가용 복합 검출과 취소 직렬 실행 추가` 로컬 커밋.

사용 API는 [OpenCV 4.12.0 Java Imgproc 공식 문서](https://docs.opencv.org/4.12.0/javadoc/org/opencv/imgproc/Imgproc.html)와 [밝기 분리 공식 문서](https://docs.opencv.org/4.12.0/d7/d1b/group__imgproc__misc.html) 기준. API 존재는 파이프 검출 성능 근거가 아님.

### Task 5: 분석 결과 저장 대기와 화면 상태

**Files:** M/feature/count/CountViewModel.kt, CountUiState.kt, CountWorkScreen.kt; M/data/detector/AnalysisStage.kt(신규), PipeDetector.kt; M/data/repository/CountRepository.kt, LocalCountRepository.kt; I/data/detector/CompositePipeDetector.kt; U/CountViewModelTest.kt; I/DetectionFlowUiTest.kt(신규), I/NavigationUiTest.kt.

**Interfaces:** `CountUiState.analysisWaiting: Boolean = false`; 성공 Detection의 `analysisMetadata`를 CountTask에 매핑해 기존 `persist(editedTask)` 사용. 평가용 검출기는 테스트 Koin 컨테이너에서만 교체; 제품 AppModule 변경 없음. 이 Task에서 상태 관찰 계약을 `enum class AnalysisStage { WAITING, PROCESSING }`와 `suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit): Detection` overload로 추가한다. PipeDetector의 기본 overload는 PROCESSING callback 후 기존 `detect(task)`에 위임해 이전 구현 호환. Repository에도 동일 overload를 기본 구현으로 추가하며 LocalCountRepository는 검출기에 위임한다. CompositePipeDetector는 overload에서 잠금 획득 전 WAITING·획득 후 PROCESSING을 알리고 기존 1인자 함수는 빈 callback으로 새 overload에 위임한다. 다른 dispatcher의 callback은 viewModelScope의 Main 실행으로 전달해 현재 analysisGeneration/operation을 확인한 뒤에만 상태에 반영한다.

- [x] 실패 테스트 작성: `analysisSaveFailureKeepsNewResultForRetry`는 새 markers/automatic/metadata 유지·unsavedChanges=true·RetrySave 후 동일 결과 1회 저장. `lateResultWithChangedSnapshotIsIgnored`는 id/revision/region/photo 불일치 각각 미반영. `failedReanalysisKeepsCorrections`는 기존 markers/undo 유지. `oldPendingSnapshotRestoresWithoutMetadata`는 기존 snapshot 읽기. `savedResultAtCancellationBoundaryIsReconciled`는 DB 이미 저장 상태를 재열기하면 대기값 제거·중복 저장 없음. 기존 0개/중복 완료 테스트 유지.
- [x] `./gradlew testDebugUnitTest --tests '*CountViewModelTest'`로 실패 확인.
- [x] 별도 직접 분석 저장을 persist로 통합. 취소/작업 스냅샷 확인은 저장 대기 진입 전 수행. 저장 실패 시 재분석/편집/확정 차단, 기존 pending key 유지. 오류 reason을 구체적인 안내로 매핑. 대기 상태 문구는 "이전 분석을 정리하고 있습니다."; 분석 시작/취소/완료 시 상태 초기화. 성공 0개는 기존 문구·명시적 확인 유지. 재분석 확인 대화상자와 저장 중 취소 차단을 재사용.
- [x] 단위 테스트 통과 후 `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.DetectionFlowUiTest,io.github.kyujin0911.pounter.NavigationUiTest'` 실행: 새 결과 미저장 문구·재시도 버튼·재분석 확인·취소 후 이전 표시·0개 확정 방지 확인. 평가용 detector 연결에서 WAITING→PROCESSING 전달과 오래된 callback 차단 테스트 포함.
- [x] `fix(count): 자동 분석 결과의 저장 재시도와 상태 보존` 로컬 커밋.

### Task 6: 정답 규약·고정 구성·독립 현장 평가

**Files:** I/DetectionEvaluationTest.kt, DetectionAcceptanceTest.kt; U/DetectionMetricsTest.kt; M/data/detector/DetectionMetrics.kt(신규, 순수 위치 대응); docs/auto_counting.md, docs/verification.md. 실사진·정답 manifest·오버레이·결과 원본은 기기/로컬 외부 자료이며 Git 미포함.

**Interfaces:** `matchDetections(expected: List<Marker>, actual: List<Marker>, normalizedTolerance: Double): DetectionMetrics`; `DetectionMetrics(matched: Int, missing: Int, falsePositive: Int)`와 `correctionCount = missing + falsePositive`. x/y 각각 0~1인 정규화 좌표의 유클리드 허용거리 내 최대 일대일 대응을 구함; 중복 대응 금지. 거리·ID로 인접 후보 순서를 고정한 이분 그래프 augmenting-path 대응을 사용하고 단순 greedy로 계산하지 않음. 정답 manifest 항목: sampleId·sessionId·bundleId·split·supportGroup·대상 중심/허용 거리·제외 대상·실제 0개 허용값. N=0 허용값은 결과 보기 전에 고정.

- [x] 실패 테스트 작성: 동일 정답에 두 표시 → matched=1/falsePositive=1. 개수 같고 위치 틀림 → missing=1/falsePositive=1. N=10·20·300의 보정 허용량 각각 1·1·15. greedy 최근접이 대응 하나를 놓치는 배치에서도 최대 대응 수 보존. `DetectionMetricsTest`에서 assertion 코드로 고정하고 `testDebugUnitTest --tests '*DetectionMetricsTest'` 실행해 실패 확인.
- [x] 최소 대응/집계 구현 후 동일 테스트 통과. 지원 조건·위치 허용거리·split을 결과 확인 전에 고정하는 평가 규약 작성. 인터뷰 조건에 맞는 새 정면 자료 우선; 기존 두 사진은 참고/튜닝용으로만 관리. 독립성이 확인되지 않으면 independent split으로 지정하지 않음.
- [ ] `DetectionEvaluationTest#evaluateTuningSet` 구현/실행: 외부 manifest/config는 instrumentation 인자의 `evaluationManifest`·`evaluationConfig`로 앱이 읽을 수 있는 기기 로컬 경로 지정. 실제 경로/전송 명령은 실행 환경에서 확인하고 로그·커밋에 개인 경로를 남기지 않음. 원본/가져오기/분석 축소 비교, 형태/영역 후보 기여, Hough 기준선, 누락/반사/이중 경계/이웃 통합/주변 혼입의 근거로 설정 선택. 사진마다 개발자 임계값 조절 금지. 고정 config의 해시·버전과 자료 분리 기록.
- [ ] `DetectionAcceptanceTest#evaluateIndependentSet`을 같은 방식으로 실행: 고정 구성을 바꾸지 않고 각 사진 N·matched·missing·falsePositive·보정량 기록. S22+ Android 16에서 첫 초기화/반복 분석/저장·화면 포함 5초 목표/메모리·취소 측정. 독립 사진의 실패를 평균으로 숨기지 않음. 사용자가 보정 후 정답 일치와 전체 검수 시간·포기 여부를 별도 사용 실험으로 확인. 회전·OS 프로세스 종료·force-stop은 각각 별도 기록.
- [ ] 실패하면 원인과 다음 튜닝 작업을 기록하고 Task 7 중단. 수정 후 새 독립 자료로 다시 검증. 합격/미검증을 docs/verification.md에 구분하고 텍스트·코드만 `test(count): 독립 실사진 계수 평가와 인수 규약 추가` 로컬 커밋. 자료가 없으면 Task 6을 검증 대기로 남기며 합성 결과로 완료하지 않음.

### Task 7: 합격한 검출기의 일반 앱 제공

**Prerequisite:** Task 6의 검증된 지원 범위·사진별 보정량·기기 시간·사용 흐름 결과가 승인 설계의 인수 조건을 충족. 미충족이면 이 Task 시작 금지.

**Files:** I/data/detector의 CompositePipeDetector/CandidateExtractor/DetectionConfig를 동일 패키지의 M/data/detector로 이동; app/build.gradle.kts; M/di/AppModule.kt; M/feature/shared/ValidationNotice.kt; I/DiInstrumentedTest.kt, DetectionFlowUiTest.kt; PRD.md, docs/auto_counting.md, docs/verification.md.

**Interfaces:** 검증된 `CompositePipeDetector`와 Task 5 stage overload 그대로 제공. `AppModule`에 기존 PhotoStore·고정 검증 config를 사용한 `single<PipeDetector>` 연결. 새로운 DI 라이브러리·런타임 기능 플래그 없음.

- [ ] 실패 테스트 작성: 제품 Koin container에서 PipeDetector가 검증된 CompositePipeDetector singleton이며 같은 Repository로 연결되는지 assertion. 일반 앱에서 성공 분석 후 보정·저장·재열기가 동일 표시 ID/메타데이터를 유지하는 UI 테스트 작성.
- [ ] `./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.kyujin0911.pounter.DiInstrumentedTest,io.github.kyujin0911.pounter.DetectionFlowUiTest'` 실행해 기존 검증 대기 등록으로 실패하는지 확인.
- [ ] OpenCV 4.12.0을 테스트 의존성에서 implementation으로 전환하고 클래스 이동·Koin 등록. 검증된 지원 조건과 사람 검토 필요성을 ValidationNotice에 전달. 처리 점수를 정확도 %로 표시하지 않음. 검증 범위를 넘어선 완료 문구 금지.
- [ ] `./gradlew spotlessCheck assembleDebug testDebugUnitTest lintDebug` 통과. 앱 기기 회귀는 AppUiTest·LargeFontUiTest·NavigationUiTest·DiInstrumentedTest·DetectionFlowUiTest·AnalysisMetadataInstrumentedTest·AnalysisPhotoInstrumentedTest·CompositeDetectorInstrumentedTest를 class 인자로 명시하고 RepositoryInstrumentedTest는 기존 prototype 메서드를 제외한 명시적 메서드 목록으로 실행. 현장 성능은 필요 시 Task 6 고정 independent 평가와 비교하되 새 튜닝에 사용하지 않음.
- [ ] 인수 근거·지원 범위·미검증을 관련 문서에 기록하고 `feat(count): 검증된 비AI 자동 계수를 앱에 연결` 로컬 커밋. push/PR/병합은 별도 사용자 요청 후 진행.

## 계획 자체 검토·사용자 검토

설계의 입출력/호환성은 Task 1, 통합/좌표는 Task 2, 방향/해상도는 Task 3, 복합 검출/자원은 Task 4, 취소/저장/화면은 Task 5, 자료 분리/인수는 Task 6, 일반 앱 제공은 Task 7에 대응한다. Review Focus 다섯 항목의 테스트를 해당 Task에 포함했다. 데이터 기반 수치는 Task 6의 측정 결과로 정하며 현장 성능의 임의 기본값을 만들지 않았다.

계획 작성 단계에서는 문서 자체 검토·링크·diff 검사만 수행했다. 이후 사용자가 계획과 직접 실행을 승인했으며, 실제 실행 결과는 아래에 기록한다.

## 직접 실행 기록 — 2026-10-13

사용자가 설계/계획을 승인하고 executing-plans 직접 실행, Task 1~5 구현·검증, Task 6 평가 준비, Task 7 현장 인수 후 진행, 작업 파일만 로컬 커밋을 요청했다. 기존 문서 작업의 범위 제한은 이 새 요청으로 해당 구현 범위에 한해 대체됐다. push/PR/병합은 요청하지 않았으며 수행하지 않는다.

시작: 기존 격리 worktree의 clean detached `b1cd809`. 같은 목적 구현 브랜치가 없어 `feat/auto-counting` 생성. 원래 checkout/브랜치/사진은 변경하지 않았다. 로컬 main `04cd258`과 시작점의 공통 조상 `64f4700`; Koin `478cb50`을 포함한 미병합 문서/코드 선행 이력에 의존한다. 원격 조회 없음.

| 단계 | 결과 | 로컬 커밋/검증 |
|---|---|---|
| Task 1 | 완료: 선택 메타데이터·실패 사유·이전 기록 호환 | `0f2bf52`; JSON default-null/reset/ID, TaskCodec missing/null, 기존 v1 SQL fixture 재열기 |
| Task 2 | 완료: 대표 경계 기준 중복 통합·좌표/반경 역변환 | `f6baa5e`; crop, 비등방 회전 타원, invalid geometry, 이웃/순서/연쇄 |
| Task 3 | 완료: preview와 별도 분석 입력·명시적 픽셀 예산·소유 자원 | `202da16`; EXIF 1~8 위치/크기, decode 크기, budget, recycle |
| Task 4 | 완료: 평가용 복합 검출·취소 직렬 실행 | `444a2de`; 합성 타원/환형 경계, 0개, 후보 포화/301개, 초기화 실패, 취소 후 최대 native owner=1 |
| Task 5 | 완료: 새 자동 결과 저장 대기·재시도·snapshot/stage 유효성 | `890b60d`; 저장 실패 markers/automatic/metadata 유지, 편집/확정 차단, id/revision/photo/region 불일치 차단, entry 종료 저장 경계 복원 |
| Task 6 | 평가 코드/규약 준비; 현장 평가 검증 대기 | `8d4b5af`; 최대 일대일 대응·사진별 보정량, hash/frozen/session/bundle 규약, 외부 tuning/independent 진입점. 외부 자료 미제공으로 두 평가 skip |
| Task 7 | 진행하지 않음 | 현장 인수 근거 없음. 기본 detector/Koin/OpenCV 의존성 유지 |

검증: `spotlessCheck assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 통과. JVM 52개, API36 앱/UI/검출/호환성/규약 회귀 34개 통과 + 외부 평가 2개 skip, Repository 허용 메서드 6개 통과. Task 1/3/4 개별 기기 테스트는 API36과 API36.1 양쪽에서 통과했다. API36.1 UI는 기존 Espresso의 `InputManager.getInstance` 부재로 실패하므로 합격으로 계산하지 않는다. UI 관련 재검증은 API36에서 수행했다. 상세는 [검증 이력](../../verification.md#자동-계수-평가-구현-검증--2026-10-13).

판단: Gaussian adaptiveThreshold 내부 float convertTo가 ARM API36 emulator에서 SIGILL을 발생시켜 양극성 국소 평균 분리로 선택했다. 설계가 허용한 국소 밝기 분리이며 추가 알고리즘/라이브러리를 도입하지 않았다. 이 선택이 실사진에 불리하면 튜닝 자료로 새 고정 버전을 정해야 한다. 합성 시험값은 현장 기본값이 아니다.

남은 검증: 단면별 정답·촬영 세션/묶음·새 독립 자료, 가져오기/축소 손실의 실사진 평가와 고정 config, S22+/Android16 분석 시작→저장→화면 표시 시간·peak 메모리·취소/재시도, 보정 후 정답 일치·기존 수동 방식과 전체 검수 시간/최종 오류/포기 비교, 첫 사용 후 개선 폭/반복 횟수 합의, Activity 재생성·OS 프로세스 종료·force-stop 별도 실행. 외부 정답 없는 합성/에뮬레이터 성공으로 현장 인수를 완료하지 않는다.

### 최종 독립 리뷰와 보완

`b1cd809..8d4b5af` 전체 변경을 fresh-context reviewer가 읽기 전용으로 검토했다. Critical 0 / Important 1 / Minor 0. 최초 리뷰 호출이 사용량 제한으로 중단되어 사용자 재개 요청 후 같은 리뷰를 이어갔으며 완료된 Task를 다시 구현하지 않았다.

Important: `analysisPhoto`의 decode 완료 후 IO→호출 dispatcher 전달 경계에서 취소되면 Bitmap이 미전달 상태로 남는 문제. `cancellationBeforeDecodeDeliveryReleasesOwnedBitmap`으로 반환 재개를 보류한 뒤 취소하여 해제 assertion 실패를 재현했다. 소유권을 반환 경계 바깥까지 유지하는 `readOwnedAnalysisPhoto`로 수정한 뒤 같은 테스트가 통과했다. 정상 전달된 사진은 기존 close/use 소유권을 유지한다.

수정 후 전체 검증: `spotlessCheck assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 통과, JVM 52개 실패 0. API36의 전체 허용 기기 회귀는 41개 통과 + 외부 현장 평가 2개 skip(총 43개), 실패 0. 기존 Hough 프로토타입 메서드는 목록에서 제외했다. Task 6 현장 평가와 Task 7 제공은 여전히 미완료다.

리뷰가 실행 근거로 대체하지 않은 항목: 실제 사진 정확도·밀집 분리, S22+ 시간/메모리/검수 효용, 실제 OS 프로세스 종료/force-stop, API36.1 Espresso 호환성. 이들은 위 남은 검증으로 유지한다. 미뤄둔 Minor 리뷰 항목은 없다. 브랜치/worktree를 로컬 그대로 보존하며 원격 작업은 수행하지 않는다. AST graphify는 갱신했고, 시작점에서 미추적이던 graph 산출물은 로컬에 두어 코드/문서 커밋에 포함하지 않았다.
