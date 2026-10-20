# 자동 계수 개선 설계 — 후보 생성부터 다시 비교하기

최신 실행 보충(2026-10-16): native 홀수 표본 수정은 평가 전용으로 채택했고 사진지표는 불변이다. a상한 우회/제3중심veto/Bookstein의 3개 분리 가설은 보류·불채택했다. 이번 요청은 과거 Task 고정을 대체해 원인 우선 실험을 허용했으며 일반 앱 연결·정답 변경·현장 합격은 제외했다. [검증 이력](../../verification.md)의 2026-10-16 절과 외부 native-improvement-v1 증빙이 현재 실행 상태다. 아래 기존 절은 당시 근거로 보존한다.


작성 2026-10-13, 개정 3: 2026-10-14. **설계 검토·격리 실험과 Task 1~3 평가/진단 코드 검증 완료, 후보 선택·정식 개선 구현·현장 인수는 미완료.** 이번 코드 확인 기준은 `feat/auto-counting`의 `89bfa63df4d3866200c71d8d683fff23fbb9a8a8`이다. 기존 Issue #6 / PR #7은 작업 배경이며 이번에 원격 상태를 조회하거나 변경하지 않았다. [실행 계획](../plans/2026-10-13-auto-counting-improvement.md), [PRD](../../../PRD.md), [채택 접근](../../auto_counting.md)을 함께 적용한다.

개정 3 작성 때의 범위는 문서 개정·공식 자료 조사·저장소 밖 최소 재현이었다. 이후 별도 요청으로 개선 계획 Task 1~3의 평가·진단 코드와 실험을 실행했다. [최신 실행 기록](../../verification.md#개정-3-task-13-직접-실행--2026-10-14)을 따른다. 제품 검출 연결, Android 의존성, 일반 앱 DI, 사진 원본은 유지했고 커밋·원격 작업은 하지 않았다. 개정 2의 복잡한 필터·병합 순서를 확정 구현안으로 승계하지 않는다.

## 1. 결론과 근거의 종류

**추천은 기존 방식 A를 최소 수정 기준선으로 유지하면서, 경계 조각을 연결하는 EdgeDrawing B를 먼저 비교하는 것이다.** 영역 방식 C는 A/B가 놓친 실제 단면을 보완하는지 입증될 때만 비교한다. 모든 기법을 합친 검출기를 먼저 만들지 않는다. B가 Android 비용과 위치 평가를 통과하기 전까지 최종 알고리즘은 미확정이다.

| 종류 | 이번 판단 |
|---|---|
| 사용자 확정 요구 | 한 묶음의 보이는 단면, 노란 캡과 캡 없는 파이프 모두 포함, 작은 돌출/후퇴·원근·반사·식별 가능한 부분 가림, 선택적 crop, 비AI·오프라인, PRD 인수·보존 기준 유지 |
| 현재 코드 사실 | whole-contour fitEllipse 기반 세 마스크, 합성용 절대 크기 gate, 포함률 병합. 복합 검출기/OpenCV는 androidTest, 일반 앱은 ValidationRequiredDetector |
| 과거 실제 실행 | 다섯 사진의 Android 평가 결과는 §3. 위치 정답이 동결되지 않아 정확한 FP/FN은 미확정 |
| 이번 실제 실행 | 데스크톱 OpenCV 4.12 후보 실험·기하 수치 계산·로컬 AAR 검사. §5의 실행 범위만 확인 |
| 과거 기술 선택 | Canny/양극성 adaptiveThreshold, 7/80px·축비2.5, 72점 지원도, 85% 포함률. 제품 요구가 아니므로 변경 가능 |
| 아직 가설 | B의 현장 후보 회수 우위, 영역 보완의 가치, 새 품질/통합 정책의 정확도·Android 비용. 이번 자료만으로 채택 성능을 확정하지 않음 |

### 중요도순 발견

1. **후보가 없는 단면은 후단 필터·병합만으로 복구할 수 없다.** 합성의 끊어진 타원에서 기존 후보 경로가 중심을 놓치고 B는 회수했다. 실사진에서 같은 원인이 얼마나 있는지는 위치 정답·중간 후보 확인이 필요하다. 기존 M→S→Q→O 순서는 생성 방식의 한계를 너무 늦게 판별한다.
2. **현재 '영역 보완'도 결국 전체 윤곽 피팅이다.** binary/inverse는 이번 다섯 입력에서 정확한 비트 반전이었다. 다른 윤곽 경계를 제공할 수는 있지만 독립된 두 영상 근거가 아니다. source 수를 신뢰도처럼 가산하거나 무조건 두 경로를 유지하지 않는다.
3. **동일 경계 중복과 한 파이프의 서로 다른 경계는 다른 문제다.** 반경20·중심차1px인 원의 기존 포함률은 35/64여서 85%를 통과하지 않는다. 반면 넓은 캡의 구멍/외곽은 본래 크기가 다르다. 한 임계값 확대로 둘을 해결하면 이웃 단면을 합칠 위험이 있다.
4. **개정 2의 맞춤 거리 계산·법선 특징 묶음도 미검증이다.** 현재 AAR에 `getClosestEllipsePoints`가 있다. 표준 API를 기준 구현으로 쓰고, 더 단순한 기하 gate로 충분한지 먼저 비교한다. 법선·gradient·구간별 점수 전부를 필수 구현하지 않는다.
5. **최종 정확도를 비교할 평가 근거가 아직 부족하다.** 정답 개수는 확정됐지만 기준 원본에 매핑·검토된 위치 정답은 미완료다. 총 개수가 가까워지거나 raw 후보가 줄어도 FP/FN 개선이라고 말할 수 없다.
6. **형태 인식만으로 의도한 묶음을 결정할 수는 없다.** 배경 묶음의 실제 파이프와 바닥 질감 FP를 분리한다. full 실패를 crop 성공으로 덮거나 필수 crop/중앙 cluster 정책을 몰래 도입하지 않는다.
7. **Android 적용과 native 자원 제약이 알고리즘 선택 조건이다.** 현재 AAR에는 EdgeDrawing이 없다. 논문의 데스크톱 속도나 Python 결과로 기기 성능·의존성 적합성을 확정하지 않는다.

## 2. 유지·수정·폐기할 판단

| 판단 | 결정과 이유 |
|---|---|
| 원·타원/영역/영상 근거를 활용하는 비AI 접근 | 유지. 정확한 후보 생성기·순서·구성은 실험으로 선택 |
| 같은 직경·평면·격자를 전제하지 않음, 숨은 수 추정 금지 | 유지. 물리적 직경이나 배열로 부족한 수를 채우지 않음 |
| 평가와 제한된 단계 진단부터 구축 | 유지. 입력·metric·실패 기록이 바뀌면 알고리즘 비교 자체가 성립하지 않음 |
| 대칭 경계 거리를 먼저 정식 구현 | 수정. 단순 gate 수정과 표준 최근접점 기반 비교를 작은 실험으로 판별 |
| 법선/gradient/대비/12구간 등 일괄 품질 모델 | 폐기. 실제 FP를 구분한 최소 특징만 채택; 표본 수·구간 수는 실험 변수 |
| hierarchy면 구멍/외곽을 객체로 통합 가능 | 폐기. 마스크 위상은 물체 ID가 아님; 필요할 때 보조 근거로만 사용 |
| 크기 상한 비율화가 스케일 문제를 해결 | 수정. gate 거절·입력 정보 손실·경계 단절·불안정 fit을 각각 판별 |
| 새 후보 경로를 기존 경로에 계속 합침 | 폐기. 단일 경로 교체를 먼저 비교하고 고유한 회수 이득이 있을 때만 보완 |
| 반드시 7단계, R0→M→S→Q→O | 폐기. 평가→진단→대안 판별→선택 경로 구현→필요 품질→필요 통합→기기 자원→독립 인수의 8단계로 변경 |

## 3. 다섯 사진의 과거 Android 기준선

저장된 결과 JSON과 오버레이를 재확인했다. `89bfa63`/`synthetic-v1`, PhotoStore 가져오기(JPEG95), Small_Phone Android16/API36 실행 기록이다. 이번에 Android 검출을 재실행한 결과가 아니다. 첨부본은 PNG 2장/JPEG 3장, 1400×1050 또는 1050×1400이며 카메라의 전송 전 원해상도는 미확인이다.

| sampleId | 사용자 N | full 결과 | 기존 crop 결과 | crop 최소 보정 / 허용 |
|---|---:|---:|---:|---:|
| cap-151 | 151 | COUNT_LIMIT | 275 | 124 / 7 |
| cap-55 | 55 | 73 | 65 | 10 / 2 |
| metal-40 | 40 | 104 | 84 | 44 / 2 |
| metal-18 | 18 | 37 | 32 | 14 / 1 |
| metal-37 | 37 | 54 | 52 | 15 / 1 |

최소 보정은 `abs(출력 수−N)`이며 실제 `FN+FP`의 하한이다. 다섯 crop 모두 이 하한만으로 기존 보정량 기준을 넘는다. full/crop별 두 실행의 개수 일치는 관찰했으나 위치 결정성·성능 분포의 증거는 아니다. COUNT_LIMIT를 0/300개로 바꾸거나 누락 행으로 처리하지 않는다.

원본 금속 사진 순서는 **40·18·37**, 번호사진 순서는 **40·37·18**이다. 파일 내용과 기존 결과의 sample 대응으로 확인했다. cap-151/metal 세 장의 번호사진은 위치 주석 참고이며 cap-55는 개수만 확인됐다. 번호 상자가 단면 중심을 가리므로 박스 중심을 정답으로 복사하지 않는다. 이미 본 다섯 사진은 development/tuning이고 독립 검증 자료가 아니다.

| 오류 유형 | 현재 증거 | 아직 필요한 확인 |
|---|---|---|
| 후보 자체 누락 | 끊어진 타원 합성 반례; whole-contour 구조 확인 | 실사진의 target별 fit 이전 윤곽/후보 대응 |
| 크기·품질 거절 | 코드의 7/80px·축비2.5·support/fit gate 확인 | 거절 후보가 실제 어느 단면인지 위치 검토 |
| 같은 경계 복수 검출 | 1px 포함률 반례; metal-18 crop의 복수 표시 관찰 | source/shape별 같은 경계 판정 및 개수 |
| 구멍·캡·금속 테두리 중복 | 넓은 캡 합성에서 여러 후보, 사진 내부 복수 표시 | 같은 경계 중복과 물리적으로 다른 내외곽의 구분 |
| 서로 다른 파이프 오통합 | 구조상 가능한 위험과 회귀 필요 | 현재 실사진의 확정 오통합 수 없음 |
| 바닥·몸체·반사 FP | 비단면 위치 표시 관찰 | 위치 정답과 근거 영상으로 배타적 원인 분류 |
| 비대상 묶음의 실제 파이프 | 사진 배경에 실제 단면 존재·표시 관찰 | 대상 scope 동결 후 FP 분류; 형태 검증만으로 해결했다고 주장 금지 |

## 4. 실제 구현과 문서의 차이

`CandidateExtractor.kt`는 median3 이후 Canny40/90, adaptive mean block31/C4의 binary/inverse에서 RETR_LIST·CHAIN_APPROX_NONE으로 모든 contour를 얻는다. 5점 이상에 fitEllipse를 적용하고 짧은 반축≥7px, 긴 반축≤80px, 축비≤2.5를 요구한다. '원·타원+영역'이라는 설계 표현만으로 호 연결·영역 안정성·부분 가림 대응이 구현됐다고 해석할 수 없다.

품질은 피팅에 쓴 contour의 평균 정규화 반경 잔차와 72개 균일 매개변수 점 주변 5×5 edge 유무다. 같은 영상에서 얻은 근거이며 독립 확률이 아니다. 대비 최소0은 유효 대비 표본이 없는 경우도 대비 조건으로 거절하지 않는다. 이웃 rim이 support를 대신 제공할 수 있다. 기존 얇은 테두리 합성만으로 넓은 캡 내부/외곽 통합을 검증하지 못한다.

`CandidateMerge.kt`는 대표 후보를 기준으로 통합하고 중심을 평균 이동시키지 않는 장점이 있다. 이 계약과 비전이적 통합은 유지한다. 크기·중심·포함률만으로 같은 물리적 파이프를 보증하지는 못한다.

`PhotoStore`의 가져오기는 50MB 제한, 긴 변3000 기준 샘플링, EXIF 정규화, JPEG95 저장이다. 분석은 긴 변1600·4M pixel 예산을 사용한다. 다섯 입력은 긴 변1400이라 분석 단계 축소가 없었다. crop의 실제 가로/세로 scale을 사용한 좌표 복원은 이미 존재한다. 원본 비교는 압축과 해상도를 함께 바꿀 수 있어 원인 실험에서는 분리해야 한다.

후보 상한은 findContours의 native 할당 **후** 확인된다. 상한10,000이 사전 메모리 보장은 아니다. CompositePipeDetector의 직렬 소유·finally 해제·취소 확인은 유지하되 native 호출 중 즉시 취소가 보장된다고 쓰지 않는다. 평가 runner의 import와 primary/repeat/Hough/original 비교 오류 경계를 분리해야 한다. 현재 `initializationMillis`는 새 프로세스의 전체 최초 지연과 동일하지 않다.

## 5. 이번에 수행한 최소 재현

### 5.1 환경과 비교 범위

저장소 밖 가상환경의 Python3.12, `opencv-contrib-python-headless==4.12.0.88`(OpenCV4.12.0), NumPy2.2.6, macOS arm64에서 실행했다. 후보 비교는 native thread1로 고정했다. 사진은 로컬 첨부 원본을 읽기만 했고 실행 전후 hash가 같았다. **Android 구현·PhotoStore JPEG95 경로·완성된 계수 파이프라인의 재실행이 아니다.**

- A: 현재 세 마스크 후보 경로를 이식, fitEllipse 후 기존 geometry gate. quality도 별도 관찰했으나 병합/최종 출력은 구현하지 않음.
- B: OpenCV4.12 ximgproc EdgeDrawing 기본 설정(NFA=true, PF=false), detectEdges→detectEllipses. 반환 `(x,y,r,a,b,angleDegrees)`를 반축 `(r+a,r+b)`로 변환·canonicalization 후 같은 geometry gate. 이 설정에서 입력 픽셀 불변 확인.
- C1: 기본 MSER region을 로컬 mask로 만들고 최대 외부 contour에 fitEllipse 후 같은 geometry gate. 기본 설정의 적합성은 미검증.
- C0: 더 단순한 SimpleBlobDetector도 확인. threshold50..<220/step10, repeat2, minDist10, area π7²..π80², inertia≥.16, color/circularity/convexity 필터 해제. 이는 A/B의 축 gate와 **동등한 정책이 아니므로** 숫자를 동등 정확도 비교로 쓰지 않음.

### 5.2 합성 6종

표의 값은 `geometry 후보 수 / 정답 중심 3px 이내 후보가 존재하는 target 수`다. 후보 하나가 실제 객체라는 뜻이 아니며 이 근접 진단은 인수 metric이 아니다.

| fixture (target 수) | A 세 마스크 | C1 MSER | B EdgeDrawing |
|---|---:|---:|---:|
| 얇은 rim (1) | 12 / 1 | 2 / 1 | 2 / 1 |
| 넓은 캡 annulus (1) | 10 / 1 | 2 / 1 | 2 / 1 |
| 접한 rim (2) | 14 / 2 | 3 / 2 | 4 / 2 |
| 끊어진 타원 (1) | 6 / 0 | 2 / 0 | 1 / 1 |
| 명암이 나뉜 내부 (1) | 3 / 1 | 1 / 1 | 1 / 1 |
| 빈 영상 (0) | 0 / 0 | 0 / 0 | 0 / 0 |

재현 fixture는 uint8 400×240, 기본 배경30/선210, LINE_8이다. thin: 중심(110,110), 반축38/28, 각0°, 두께4; cap: 같은 중심의 채운 반경36/내부 반경10(배경색); touching: 중심(120,110)/(168,110), 반경24, 두께3; fragmented: 중심(160,120), 반축65/40, 각25°, 호0–75/100–175/200–280°, 두께3. split: 배경180, 중심(160,120), 반축60/40, 각25°인 채운 타원30, 내부 y>120은100. blank는30이다.

**반증된 것:** 현재 contour/fit 경로가 이 끊어진 타원 중심을 회수한다는 가정. **입증되지 않은 것:** B의 실사진 우위, 심한 원근·다양한 가림의 지원. B도 annulus에서2개를 출력하므로 물체 통합 문제를 자동 해결하지 않는다. 기존 gate를 적용한 한 fixture만으로 후보 방식의 일반적 한계를 단정하지 않는다.

### 5.3 실사진 5종의 후보 부담

모두 full 입력이다. 아래 값은 **자동 계수 결과가 아니다.** 표준 gate 전후 후보 수만 비교하며 위치 정답 없는 FP/FN·성능 순위는 계산하지 않았다.

| sample | A geometry | C1 MSER raw region / geometry | B native / geometry | C0 blob |
|---|---:|---:|---:|---:|
| cap-151 | 1241 | 5155 / 3683 | 392 / 392 | 324 |
| cap-55 | 336 | 1325 / 450 | 106 / 94 | 93 |
| metal-40 | 662 | 2140 / 1647 | 85 / 85 | 108 |
| metal-18 | 240 | 730 / 264 | 31 / 30 | 35 |
| metal-37 | 793 | 1455 / 1097 | 49 / 33 | 106 |

MSER를 그대로 합치면 처리 후보가 늘어난다. MSER 자체가 부적합하다는 판정은 아니다. B의 metal-37 후보49→33은 gate가16개를 버렸다는 뜻이며 실제 파이프16개 누락을 의미하지 않는다. 원본·색 변환·JPEG·후처리가 §3과 다르므로 두 표의 수를 직접 비교하지 않는다. Python A/C의 반복문과 B native 처리 시간은 공정한 속도 비교가 아니어서 채택 근거에서 제외했다.

추가 계산에서 다섯 사진의 adaptive binary/inverse는 모두 정확히 상보적이었다. 역마스크를 지워도 후보가 같다는 뜻은 아니므로 삭제는 ablation 뒤 결정한다. `getClosestEllipsePoints`는 반경20 원/1px 이동/64점에서 해석적 원 거리 대비 최대오차 약1.30×10⁻⁶px였다. 이것은 원 fixture의 API 변환 검증이며 타원 전체의 정확도 보증이 아니다.

### 5.4 재현 증거와 제한

로컬 실험 파일을 별도 보존했다. 개인 경로·실사진 후보 좌표를 공용 문서에 포함하지 않는다. `probe.py` SHA256은 `0b3b9a199c3e0c7c84d0148cd02a4fd520a939ffecbd0b6a1b00ff98e1ffa5a7`, 결과 JSON은 `71ae49e0155cacabb9f6f36183fbe417814cba3889fc2cebbf7cc0f50319f330`이다. 추가 blob/거리 script는 `43a8eb70acf3bf01abc85c58d882829bc41bcb9b157a10d1b7026abfb3871ecf`, 결과는 `3f6c8b697972bd1ba875be8e5fc3979e9cfdc754cc1a28d42b63f9a321af8429`다. 사진 source hash는 로컬 결과에 있다.

후속 재현은 위 버전·fixture·설정으로 시작하되, 실사진 정확도 비교는 §7의 검토된 위치 정답과 Android 동일 입력 경로가 준비된 뒤 실행한다. 이번 실험에서 EXIF 전 경우, crop, 고밀도300개, 실제 취소/메모리 peak는 검사하지 않았다.

## 6. 대안 비교와 1차 자료

### 6.1 비교할 경로는 세 종류로 제한

| 경로 | 해결할 수 있는 문제 / 가정 | 주요 위험·실패 조건 | Android 비용·유지보수 | 결정 |
|---|---|---|---|---|
| A: 세 마스크 whole-contour의 최소 수정 | 현재 회수된 후보의 같은 경계 중복·잘못된 gate. 연속된 contour가 충분히 필요 | 접촉·반사로 합쳐지거나 끊어진 윤곽, 몸체를 포함한 fit. fitEllipseAMS/Direct만으로 호 연결이 생기지 않음 | 현재 AAR/코드 재사용. 윤곽점·후보쌍 비용은 여전히 큼 | 필수 비교 기준선. 실제 회수 한계가 크면 필터 추가만으로 연명하지 않음 |
| B: EdgeDrawing의 연결 edge/arc 기반 원·타원 후보 | 끊어진 경계에서도 호를 모아 후보를 만들 가능성. 영상 경사·연결 가능한 호 필요 | 작고 흐린 rim, 길쭉한 타원, 이웃 호의 잘못된 결합, 반사·몸체의 원호, 내외곽 중복. NFA가 파이프 확률은 아님 | 현재 AAR에 없음. contrib 포함 단일 OpenCV SDK 빌드 또는 최소 native bridge의 유지·ABI·APK 비용 필요 | 먼저 실험할 구조적 대안. 실사진 위치 평가·기기 연결 검증 전 채택 아님 |
| C: 안정된 밝기 영역의 후보 | 단면 내부/캡 영역이 연결되고 threshold 변화에서 지속되면 약한 경계를 보완할 가능성 | 금속 내부 명암 분할, 이웃 영역 연결, 캡 annulus/hole 중복. 하나의 stable region≠파이프 하나 | 현재 AAR에 SimpleBlobDetector/MSER 존재. threshold별 영역/점 보관 비용; MSER 후처리도 필요 | 더 단순한 C0 blob부터 고유 누락 회수 확인. C1 MSER는 안정성 정보가 추가 이득을 줄 때만 |

세 경로 모두 색상·동일 직경·규칙 격자가 필요 조건이 아니다. 그러나 그 사실만으로 작은/캡 없는 단면을 회수한다는 뜻은 아니다. 이를 target 속성별로 보고한다. A+B+C 합집합, RANSAC/CLAHE/watershed/타일링을 동시에 추가하는 안은 추천하지 않는다. 별도 분할·복원 단계가 필요한 실패가 확인되면 한 대안으로 다시 비교한다. AI나 필수 사용자 seed/묶음 선택이 필요한 안은 범위 변경 제안으로만 분리한다.

### 6.2 출처·버전·적용 한계

| 1차 자료 | 확인한 내용과 현재 문제 연결 | 적용·비용·라이선스 경계 |
|---|---|---|
| [OpenCV4.12 imgproc API](https://raw.githubusercontent.com/opencv/opencv/4.12.0/modules/imgproc/include/opencv2/imgproc.hpp) | fitEllipse/AMS/Direct는 주어진 점의 적합법, getClosestEllipsePoints는 점의 타원 최근접점. 적합법 교체와 후보 생성 문제를 구분하는 근거 | 로컬 4.12 AAR 소스에서 Java 메서드 확인. native 타입은 평가 계층에 한정. 거리 호출 횟수·점 수의 실제 비용은 측정 필요 |
| [OpenCV4.12 EdgeDrawing API](https://raw.githubusercontent.com/opencv/opencv_contrib/4.12.0/modules/ximgproc/include/opencv2/ximgproc/edge_drawing.hpp), [구현](https://raw.githubusercontent.com/opencv/opencv_contrib/4.12.0/modules/ximgproc/src/edge_drawing.cpp) | 연결 edge에서 원/타원 생성, 기본 NFA/PF 옵션과 반환6값 확인. 끊어진 rim 후보 실험의 재현 기준 | 현 AAR의 classes.jar에는 EdgeDrawing 없음. native segment/arc·영상 buffer 비용을 측정해야 함. Python 제공 여부가 Android 제공 증거는 아님 |
| Akinlar·Topal, 2013 [EDCircles 원 논문 서지·초록](https://research.itu.edu.tr/en/publications/edcircles-a-real-time-circle-detector-with-a-false-detection-cont/), DOI 10.1016/j.patcog.2012.09.020 | edge→line→arc→결합과 a contrario 검증. 논문이 설명하는 원·near-circular ellipse 및 끊어진 경계 연결의 근거 | 접근한 범위는 서지·초록이며 유료 원문 전체를 읽었다고 주장하지 않음. 논문 데스크톱 수치·형상 범위를 Android/강한 원근 타원에 확대하지 않음 |
| [저자 ED_Lib](https://github.com/CihanTopal/ED_Lib), [MIT License](https://raw.githubusercontent.com/CihanTopal/ED_Lib/master/LICENSE) | 저자 구현을 재현·native bridge 후보로 조사 | 채택 시 commit pin·해당 파일/의존 라이선스 확인. master 링크만으로 재현 버전 확정 금지. contrib와 동일 결과라고 가정하지 않음 |
| Matas 등, BMVC2002 [MSER 원 논문](https://bmva-archive.org.uk/bmvc/2002/papers/113/full_113.pdf), DOI 10.5244/C.16.36 | threshold에 따른 연결 extremal region의 면적 안정성. 윤곽 단절을 영역으로 보완하는 가설의 근거 | 매칭용 영역의 안정성이 파이프 분할·객체 수를 보장하지 않음. 반사·그림자 분할/영역 연결 실패 확인 필요 |
| [OpenCV4.12 features2d API](https://raw.githubusercontent.com/opencv/opencv/4.12.0/modules/features2d/include/opencv2/features2d.hpp) | MSER와 여러 threshold의 blob 중심 반복 검출. 단순 영역 baseline을 먼저 두는 근거 | 둘 다 로컬 AAR 클래스 확인. Blob의 keypoint radius는 실제 타원 반축이 아님. 필요하면 contour를 이용한 변환의 Java 지원·오차를 확인 |
| [OpenCV4.12 License](https://raw.githubusercontent.com/opencv/opencv/4.12.0/LICENSE), [contrib4.12 License](https://raw.githubusercontent.com/opencv/opencv_contrib/4.12.0/LICENSE) | 4.12 배포의 Apache2 조건 및 포함 고지 확인 | 통합할 파일의 BSD 등 별도 고지·3rdparty·재배포 의무는 다시 확인. 서로 다른 OpenCV native 배포본을 동시에 넣어 class/ABI 충돌을 만들지 않음 |

논문/공식 구현은 알고리즘의 가정과 구현 가능성 근거다. 사진별 정확도·메모리·속도의 근거는 해당 입력과 기기에서 별도로 수집한다.

## 7. 평가 규약

### 7.1 scope·원본·정답

대상은 사용자가 지정한 한 묶음이다. 번호가 없는 가장자리 파이프는 N에 맞춰 제외하지 않고 대상 여부·사유를 확인한다. 다른 묶음/몸체/바닥에 생긴 출력은 작업 scope 안에서 FP다. `excluded` 주석은 원인 기록이며 출력을 지우는 필터가 아니다. full과 기존 crop을 고정하고 각각 보고한다. crop이 외곽을 자르는 경우도 기록한다. 분석 halo는 별도 실험 버전으로 두며 출력 중심은 원래 Region 안으로 제한한다.

기준 좌표는 EXIF 회전/반전을 적용한 **첨부 원본 전체 이미지**다. sourceSHA256, referenceWidth/Height, import 후 파일 hash/실제 치수, decode 치수, crop offset, 실제 x/y scale을 기록한다. 정규화 marker를 이 기준 픽셀로 환산한다. 모든 비교가 동일 기준 공간을 써야 한다. ROI 좌표를 전체 사진 좌표로 해석하지 않는다.

번호사진은 식별 참고로만 사용하고 주석 없는 원본에서 target ID·중심·보이는 외곽의 짧은 반축 bPx·주석 불확실성 uPx를 작성한다. 전역 대응점으로 crop/resize 관계를 확인하고 다른 시점·비선형 변형을 단순 resize로 맞추지 않는다. detector overlay를 숨기고 주석을 검토한다. cap-55 위치55개도 필요하다. 캡 없는 target 포함, N/ID/제외 대상 중복을 검사한다. 불확실한 가림 중심을 임의 확정하거나 지원 제외하지 않는다. 검토가 끝나기 전 `groundTruthFrozen=true`로 인수 판정하지 않는다.

### 7.2 일대일 매칭과 불확실성

기존 최대 일대일 대응 코어를 재사용하되 거리 `hypot(Δx×referenceWidth, Δy×referenceHeight)`를 쓰는 metric-v2를 둔다. 정답 주석 오차 uPx와 검출 허용 tPx는 별개다. `tPx=min(0.25bPx, 0.45×최근접 target 거리)`는 **미확정 초기 제안**으로 유지하며 단일 target의 최근접 거리는 무한대로 취급한다. 결과 수를 보고 허용치를 넓히지 않는다. 불확실성이 판정을 바꾸면 재주석/검토하고 인수는 보류한다. 주변 target 간 대체 매칭도 검토해 총 대응 수의 안정성을 확인한다.

metric-v1과 schema1 읽기는 보존한다. 같은 R0 예측을 v1/v2로 먼저 평가해 metric 변경 효과를 분리한 후 R0/개선안은 동일 v2로 비교한다. 출력은 M, FN, FP, correction=FN+FP, netDifference, 대응/미대응 ID다. precision/recall의 분모0은 N/A다. FP의 중복/배경/기타 원인은 배타적으로 분류하며 FP와 다시 더하지 않는다.

후보 단계에는 같은 위치 규약의 **일대일 후보 회수 M_candidate**와 geometry/quality 통과 후 회수를 별도로 기록한다. 고정 후보 geometry를 선택·제거만 하는 후처리는 이 회수 상한을 넘을 수 없다. `N−M_candidate`가 사진 허용 보정량보다 크면 그 후보 집합으로는 최종 기준을 만족할 수 없다. 이는 후보 중심이 유효한 위치 진단이며 새 피팅/중심 수정이 포함되면 별도 후보 버전이다. 정답을 이용해 후보를 선택한 결과는 oracle 진단으로만 보고 검출 출력으로 사용하지 않는다.

### 7.3 재현성과 실패 보존

schema2에서 dataset/truth/source/annotation 버전과 run code/config/metric 버전을 분리한다. 설정 탐색 때 정답이 바뀌지 않아야 한다. 실제 파일 hash/치수를 확인하고 Android build/ABI/device/OS·OpenCV 빌드도 남긴다. 정답 좌표·N·sampleId에 따른 분기는 detector에 전달하지 않는다.

다섯 사진은 development이며 session/bundle unknown을 명시할 수 있다. independent에는 unknown·추정 ID를 허용하지 않으며 개발 자료와 다른 묶음/촬영 세션인지 확인한다. 다른 crop/압축/증강본은 독립 자료가 아니다. 독립 평가 후 조정하면 새 config/version과 새 독립 자료가 필요하다.

주 결과를 먼저 저장한다. import, primary, repeat, Hough, original/대안 비교를 각각 success/failure/skip으로 기록한다. 한 비교 실패가 primary 결과를 덮지 않게 한다. cancel은 전파하고, 입력 실패는 sample별 보존, 환경/예상 밖 프로그램 오류는 세션 실패로 남긴다. COUNT_LIMIT/SATURATION/OOM/native crash를 행 삭제나 정상 빈 결과로 바꾸지 않는다. 프로세스가 죽을 수 있으므로 실행 전 sample/run ID를 기록하고 미완료 실행도 보존한다.

## 8. 추천 파이프라인과 최소 책임

최종 생성기는 Task 3의 실험으로 고른다. 아래는 **채택 조건을 만족한 한 경로**의 계약이며 A/B/C를 모두 실행하라는 설계가 아니다.

현재 EllipseCandidate는 edgeSupport/fitError/contrast를 필수로 가진다. B/C에 contour 피팅 점이 없다고 fitError=0을 넣으면 대표 정렬에 유리한 가짜 근거가 된다. 생성기별 진단 score와 공통 영상 근거를 구분하고, 동일 의미로 측정할 수 없으면 평가 계층의 geometry/quality를 분리하는 최소 adapter를 선택한다. native NFA/MSER 안정성을 기존 score로 바꾸지 않는다. 이 계약을 Task 3에서 결정한 뒤 Task 4의 연결을 구현한다.

| 단계 | 입력→출력 | 책임과 선택 조건 |
|---|---|---|
| 입력/ROI | 저장 사진+Region→분석 gray+변환 메타데이터 | 기존 import·EXIF·pixel 예산 보존. 좌표/압축/scale 실험과 제품 변경 분리 |
| 선택한 후보 생성 | gray→canonical ellipse와 출처 | A/B 또는 증거 있는 보완 C. major≥minor>0, 각도 modulo π, 분석 픽셀 단위. 경계 조각/region ID는 평가 provenance |
| 최소 geometry/품질 | 후보+영상→유지/거절과 근거 | invalid 값·확인된 size gate부터. 과한 quality로 candidate FN을 숨기지 않음 |
| 같은 경계 중복 제거 | 비슷한 크기의 근접 후보→대표 | 포함률 반례를 최소 수정; 단순 gate와 표준 경계 거리 중 검증된 방법. 고정 순위/대표 anchor/출처 합집합 |
| 필요한 물체 단위 통합 | 서로 다른 경계+국소 근거→단면 대표 | cap hole/outer 중복이 남을 때만. hierarchy 단독 억제·무조건 큰 경계 선택 금지 |
| 좌표 복원/검토 | 단면 대표→기존 Marker 목록 | 실제 x/y scale·crop 복원, 중심 평균 이동 없음. UUID/저장/수동 보정 계약 유지 |

### 경계 거리와 물체 통합

먼저 같은 크기·미세 이동 pair의 기존 포함률 조건을 단순화한 정책과, `getClosestEllipsePoints`로 양방향 경계 거리를 계산하는 정책을 비교한다. center/axis cheap gate 뒤에만 비싼 연산을 한다. 후자는 서로 다른 angle 표기나 같은 t 대응 문제를 피하지만 표본 밀도·p90/max·정규화 기준은 검증해야 한다. 표준 API를 reference로 쓰고 main 순수 Kotlin 경계에 native 타입을 넣지 않는다. 필요하면 평가 계층에서 관계를 계산해 전달하거나 검증된 순수 기하 구현과 비교한다. custom 최근접점 solver는 표준 API/단순 gate의 비용·기능 부족이 입증될 때만 만든다.

cap 구멍/외곽은 별도 실험이다. 내부 어둠, 외곽 밝기, 중심 관계, 관측된 aperture 등을 이용하되 금속 내부 반사·캡 없는 target을 배제하지 않는다. parent/child는 동일 mask의 위상이고 물체 ID가 아니다. 별도 aperture가 보이거나 관계가 모호하면 자동 병합하지 않는다. A–B–C 전이 병합, 이웃 작은 파이프를 덮는 큰 후보, 잘린 외곽을 회귀로 둔다. 경계가 하나만 보여도 충분한 근거가 있으면 후보를 유지한다.

### 품질은 필요한 특징부터

먼저 기하 잔차+경계 지지 분포로 실제 FP와 target 후보가 분리되는지 확인한다. 분리되지 않을 때만 국소 법선/대비를 추가한다. 타원 법선은 `normalize(R(cos(t)/a,sin(t)/b))`이며 중심 방사선과 다르다. a80/b20/t45°에서는 방향 차이 약61.9°다. 균일 t는 균일 호 길이가 아니므로 실제 길이 분포와 계산 예산을 확인한다. 모든 타원에 고정72점 또는 12구간을 최적값으로 선언하지 않는다.

영상/허용 ROI 밖의 읽을 수 없는 표본과 영상 안의 비지지 표본을 구분한다. 가림으로 추정한 내부 표본을 분모에서 빼 점수를 올리지 않는다. clamp한 같은 픽셀을 반복 표본으로 세지 않는다. 밖의 띠가 항상 배경이라는 가정·특정 대비 부호·모든 구간 지지 요구를 금지한다. 특징을 추가해도 작은 단면/가림의 FN이 늘거나 비용 대비 이득이 없으면 거절 정책을 채택하지 않는다.

### 스케일과 진단

크기 gate 거절이 확인되면 crop 전 전체 분석 이미지 치수에 대한 상대 상한과 raster 하한을 비교한다. crop 폭으로 대상 크기 기준을 바꾸지 않는다. median/adaptive block/탐색 띠도 픽셀 의존이므로 축 상한만 비율화해 scale 불변을 주장하지 않는다. 같은 oriented pixels에서 무손실/JPEG95를 같은 크기로 비교하고, 같은 저장 파일의 decode scale 변경은 별도 실험한다. 현재 compareOriginal의 압축+해상도 효과는 분리한다.

상세 trace는 기본 off, 정확한 stage 총량과 제한된 entry/point 예산만 둔다. fit 이전 실패는 bbox·윤곽 예시 없이는 target 원인을 단정할 수 없다. Mat/Bitmap을 trace가 보유하지 않는다. RETR_TREE 변경도 후보 geometry를 비교하며 달라지면 관측만 추가했다고 보고하지 않는다. trace truncation이 검출을 자르지 않도록 하고 포화/실패에도 가능한 집계와 미완료 상태를 남긴다.

## 9. 반박 가능한 실험과 채택 기준

모든 카드는 실행 전에 입력 hash/ROI/metric·기준선·설정 목록·후보 budget을 고정한다. 사진별 설정 튜닝이나 정답 개수 출력 절단을 금지한다. 한 카드에서 기본+소수 변형(초기 최대4개)을 먼저 비교하고, 더 탐색하려면 새 근거를 기록한다. 숫자를 채우기 위한 탐색은 하지 않는다. 후보 방식 고유 설정 비교와 공통 후처리 비교를 분리하고 필요한 상호작용만 2×2로 확인한다.

| 카드 / 가설 | 최소 실험과 반례 | 지표·고정 조건 | 채택·보류·폐기 |
|---|---|---|---|
| G: 연결 arc 후보가 A의 누락을 회수 | §5 단절 fixture와 실제 누락 target. 반례: 이웃 호 결합/작고 흐린 rim/큰 원근 | A/B 동일 입력·canonicalization, gate 전/후 M_candidate, 고유 회수, 잘못된 shape, 후보 수. 같은 후처리 비교 뒤 각 경로의 소수 설정 비교 | B가 확인된 누락을 회수하고 공통 후처리의 FN+FP가 개선될 때 Android spike 진행. 개수 근접만이면 보류; 회수 이득 없거나 비용 감당 불가면 A 유지 |
| R: 영역 안정성이 A/B의 약한 경계를 보완 | C0 blob 먼저, 부족할 때 C1 MSER. 반례: 그림자로 나뉜 금속 내부/접한 캡 영역 | 같은 gray/ROI, A/B 대비 고유 target 회수와 추가 FP·할당량. 합집합 전에 C 단독 확인 | 실제 고유 회수와 최종 correction 이득이 있어야 보완. 후보 폭증만 있으면 보류/제외 |
| M: 포함률 제거의 최소 수정으로 같은 rim을 합침 | r20/중심차1, angle+π, 원의 angle차; 반례 접한 원2개·인접 소형·연쇄 | 단순 gate 대 표준 양방향 거리; 동일 후보 목록·대표 순위; 오통합/FN/FP·pair 비용 | 더 단순한 정책으로 같은 결과면 그것을 채택. 표준 거리의 추가 이득 없으면 custom solver/복잡 metric 폐기 |
| Q: 최소 경계 특징이 실제 FP를 분리 | 이웃 rim이 지지하는 가짜 타원·몸체·반사와 진짜 부분 가림 | 같은 후보·통합 고정; 원인별 FN/FP/coverage, trace-off 비용 | 경계 분포만으로 충분하면 법선/gradient 생략. 필터 강화로 누락을 교환했으면 사진별 correction·작은/캡 없는 대상 회귀를 함께 판정 |
| O: 내외곽 관계가 남은 cap 중복을 해결 | r8/r24 cap, 접한 annulus2개, parent 아래 aperture2개, 중심이 어긋난 원근 고리 | M/Q 고정, 같은 경계와 물체 통합 분리; 오통합·대표 중심·금속 FN | 중복이 실제 남을 때만 채택. hierarchy/포함률만의 통합은 제외. Q×O 상호작용 필요 시2×2 |
| S: gate/입력 손실이 특정 누락 원인 | 큰 타원 gate, 반크기 decode, JPEG만 변경, crop만 변경 | 한 요인씩; 같은 기준 픽셀 매칭, 실제 pixels·후보 회수·FP·시간/메모리 | 확인된 원인만 수정. 확대를 정보 복구로 간주하거나 gate 조정만으로 모든 scale 문제 해결 선언 금지 |

중간 채택의 최소 조건은 해결 대상으로 지정한 실패의 회수/FP 개선, target 종류별 회귀 설명, 같은 평가에서의 사진별 correction 비교, 자원 실패 없음이다. 단일 사진 악화를 평균으로 감추지 않는다. 중간 비교가 일부 목표에 미달하면 연구 경로로만 유지하고 최종 통과로 표시하지 않는다. 모든 경로의 후보 회수 상한이 불충분하면 품질/병합을 계속 강화하지 말고 실제 경계 단절·scope·정보 손실의 다음 최소 실험을 결정한다.

## 10. Android·자원·현장 인수

B를 채택하려면 **평가용으로만** OpenCV4.12 contrib 포함 단일 SDK 또는 검증된 bridge 중 하나를 고른다. 현재 AAR와 다른 OpenCV 배포를 함께 넣지 않는다. ABI별 빌드/Java wrapper/각도·축 변환, release/예외 수명, 패키지 크기 증가, arm64 실기기 동작을 확인한다. desktop Python 이식과 Android pixel/geometry parity를 분리한다. Native 연결 실패·비용 과다면 A/C로 돌아갈 수 있어야 하며 제품 의존성 전환은 현재 작업에 포함하지 않는다.

S22+/Android16에서 trace-off로 프로세스를 새로 시작한 첫 호출 최소3회와 같은 프로세스 반복 최소10회의 원시값/median/max를 기록한다. 새 detector 객체만 만드는 것은 cold 실행이 아니다. init·decode·전처리·생성·품질·병합·좌표·대기·저장/표시를 분리하고 사용자 관찰 분석→표시5초 목표를 유지한다. 가져오기 시간은 이 목표와 별도 기록하고 전체 검수에는 포함한다.

실제 Java/native/프로세스 메모리 peak를 profiler/측정 도구·샘플 주기와 함께 기록한다. 한 시점 PSS나 가정한 Mat 크기의 합을 실제 peak라 부르지 않는다. contour/point/arc/region·candidate pair·동시 Mat 수를 원인 지표로 남기며 사후 상한의 한계를 표시한다. 작은 빈 영상뿐 아니라 많은 질감·큰 입력·300개 근처·실패/재시도를 측정한다. 호출 도중 즉시 중단되지 않는 native 연산은 종료까지 소유를 유지하고 늦은 결과를 폐기한다. 정리 전 새 native 작업을 겹치지 않는다.

최종 합격은 [PRD 인수 조건](../../../PRD.md#5-인수-조건)을 그대로 따른다. N>0 사진마다 correction≤max(1,floor(.05N)), 보정 후 정답 일치, N=0의 사전 규약, 최대300·포화 실패 구분, 대표 기기 분석→표시5초, 보존/취소/복원과 전체 검수 효용을 평가한다. 다섯 개발 사진의 허용량은7/2/2/1/1이다. full/crop은 별도 결과이며 지원 범위를 사후 변경하지 않는다.

독립 session/bundle의 실사진·검토된 위치 정답·대표 기기·사용자 보정 및 전체 검수 시간 없이 현장 인수를 완료하지 않는다. 테스트 전용 주입으로 화면 흐름을 확인할 수 있지만 일반 앱 제공 상태와 구분한다. 합성·리뷰·에뮬레이터·Activity 재생성은 실사진·실기기·OS 프로세스 종료·force-stop의 대체 증거가 아니다. **기존 구현 계획의 Task 7 제품 연결은 계속 검증 대기다.**

## 11. 이번 검토 상태와 다음 결정

- 완료: 코드/과거 결과 대조, primary 자료 조사, AAR API 검사, 합성6종·실사진5종 후보 비교, blob·상보 마스크·최근접점 수치 검사, 개정 2의 가정 재검토.
- 미완료: 위치 정답 동결/정확한 FP·FN, 공통 Android A/B 비교, B의 빌드·ABI·자원, 최종 threshold/품질/통합 선택, independent·S22+·전체 검수.
- 다음 결정: Task 1~3의 평가/진단 계약과 가능한 판별 실험은 실행했으나 최종 선택은 모두 보류다. 위치 정답/허용치 검토와 B의 격리 Android build·ABI·자원 근거를 보완하고 Task 4 adapter를 확정해야 한다. 편평 타원 합성에서 A/B 모두 gate 전 회수하고 기존 축비2.5 뒤 누락됐으므로, 이 실패는 생성기 부재로 단정하지 않고 별도 S gate 카드로 관리한다. 실사진 정확도 우승자는 선언하지 않는다.
- 자기 검토: 대칭 거리·법선·hierarchy를 확정 정답으로 두지 않았고, 새로운 방법의 복잡도·범위·라이선스 비용과 반례를 포함했다. 이번 후보 실험은 성능 개선 달성으로 기록하지 않았다. 기존 7단계와 새8단계 대응은 실행 계획에 명시했다.

### 위치 주석 자료 후속 상태 — 2026-10-14

새 사용자 번호사진5장을 원본과 대조해 중심301개(cap-151=151, cap-55=55, metal-40=40, metal-18=18, metal-37=37)의 초안을 외부 로컬 자료에 반영했다. 위치 미작성은 해소됐으나 주석·반축/불확실성 시각 추정·허용치 동결은 미완료다. 번호사진 식별과 원본 중심을 구분하며 DRAFT/groundTruthFrozen=false를 유지했다. 실제 schema2 검증은 통과했지만 새로운 정확도/Android 후보 비교나 인수를 실행한 것은 아니다. [상세 검증](../../verification.md#번호사진-추가와-원본-위치-주석-반영--2026-10-14)을 따른다.

### 후속 실험에 따른 상태 갱신 — 2026-10-14

위치 검토는 마지막112 재수정 승인으로 중심301개 완료/0개 대기다. 모든 b/u/t와 전체 주석 동결·독립 자료는 여전히 대기다. Android 기준선의 동일 예측 v1/v2 및 단계별 잠정 지표를 작성하고, v5 중심으로 같은 예측을 별도 재평가했다. 중심 확인을 전체 주석 동결로 확대하지 않는다.

B의 Android 가능성은 더 이상 CMake 부재만으로 보류하지 않는다. 기존4.12 AAR prefab와 같은 NDK27로 최소 JNI를4 ABI 빌드/재현했고 API36 arm64에서 실제 호출·6값 geometry·정상/예외 자원 경로와 동일 JPEG95 gray10개를 검증했다. source의 약64MiB color LUT BSS와 배포 고지·대표 기기 peak/취소 조건을 기록했다. 새 AAR·일반 DI·앱 런타임은 변경하지 않았다.

단순 gap-only A1은 R0 quality/대표 순서를 유지해도 보정량을 늘렸다. 단순 gate가 반드시 정확도를 개선한다는 해석을 채택하지 않는다. B는 공통 comparator에서 일부 회수/오류를 개선했으나 cap151 COUNT_LIMIT·물체 통합과 metric 동결 문제가 남고, 완성된 Android B detector의 승리가 아니다. 크기 gate 완화는 metal37의 M을 높이지만 FN/FP 교환으로 보정량이 같았다. 편평 타원 누락은 생성기와 gate를 분리한다. C0의 추가1개 center 가능성도 종이/파이프 경계 검토 전에는 고유 물체 회수 증거로 올리지 않는다.

최종 선택은 **모두 보류**, B는 조건부 우선 후보로 남긴다. Task4의 geometry/quality 분리 최소 adapter와 진행 조건은 [후속 검증 기록](../../verification.md#개정-3-task-13-후속-실행--2026-10-14)을 따른다. 다른 설계 범위·인수 목표는 바꾸지 않았다.

### 원본 검토에 따른 개발 착수 판단 대체 — 2026-10-14

직접 순차 실행한 original-review-1은 위 **모두 보류** 판단을 **B 교체 경로의 조건부 개발 선택/Task4 착수 가능**으로 대체한다. 인수 결과나 정식 통합 완료가 아니다. 원본 b/u 범위·외곽 지름 근거·국소 재검토 상태, scope 사용자 확인, R0 동일 예측/원본 FP 분류, 공통 A/B 민감도와 최소 adapter를 작성했다. 전체 주석 DRAFT/frozen=false와 개발 자료 구분을 유지한다. 상세는 [최신 실행 계획](../plans/2026-10-13-auto-counting-improvement.md#원본-검토와-개발-경로-선택--2026-10-14-original-review-1), [검증 기록](../../verification.md#원본-주석-검토와-task4-착수-판단--2026-10-14)을 따른다.

반증된 해석만 정정한다. 독립 정답·S22+ 전체 자원/흐름 검증을 모두 Task4 전에 요구하는 것은 단계 구분과 맞지 않는다. Task4에는 개발 개선 가능성·Android 연결·geometry/quality와 소유권 계약이 필요하고 실제 crash/OOM/비용 결함은 착수 판단에 반영한다. 대표 기기 전체 검증과 독립 인수는 Task7/8, 최초 제품 연결은 인수 후 별도 작업이다.

기하학적 내외곽 통합만으로 보정 문제를 해결한다는 가정도 반증됐다. 고정 postpass는 B cap151 crop correction168→32로 줄였지만 M150→148로 낮췄고 cap55도 회수를 잃었다. 같은 단면의 외곽 대표로 바뀌면서 draft 중심/t 대응을 잃는 원본 반례를 보존했다. 이웃 물체 오통합으로 단정하거나 해당 정책을 정식 연결하지 않는다. 표준 closest-point는 이번 공통 비교에서 단순 same-rim gate보다 추가 이득이 없었으므로 custom solver를 구현하지 않는다.

C0#25의 center-only 가능성은 실제 slanted 단면 근처임을 확인했으나 원형 keypoint/밝기 분할이 전체 대상 경계·안정성을 입증하지 못했다. C/MSER/합집합으로 확대하지 않는다. raw 후보 부재·gate 거절·품질·대표 선택 손실은 계속 분리한다. 기존 지원 범위·PRD 인수 목표·R0·일반 앱 검출기를 바꾸지 않았다.

### 사용자 중심 정정에 따른 반례 대체 — 2026-10-14, v6

사용자가 cap151 전체 중심 치우침과 metal37의3중복 번호 쌍/누락3단면을 정정했다. 총301·scope를 유지하면서 원본 기준 새 좌표/번호 연결 **v6 AI DRAFT**를 별도 작성하고 같은 예측을 재평가했다. cap151 crop R0는151/0/124/124, metal37 full/crop은17/20/37/57·17/20/35/55다. 검출 개선이 아니라 주석 변경 효과다. v5 전체 중심 승인과 당시 원인/대응 결과를 최신 사실로 재사용하지 않는다.

v6에서는 고정 E-O1의 cap151#59/#144 대응 손실이 사라졌다. 따라서 그 두 사례를 검출의 물리적 누락/이웃 오통합 반례로 사용하는 판단을 대체한다. cap55 손실은 여전히 남으므로 postpass를 그대로 채택하지 않는다. 공통 B의 개발 이득·Android 근거와 조건부 Task4 판단은 유지하며, 주석 동결·정식 통합·인수는 미완료다. [v6 실행 기록](../plans/2026-10-13-auto-counting-improvement.md#사용자-중심번호-정정에-따른-v6-재평가--2026-10-14), [검증 기록](../../verification.md#사용자-중심중복-번호-정정과-v6--2026-10-14)을 따른다.


### Task4 최소 adapter의 실제 재현 — 2026-10-14

조건부 선택B를 `edge-drawing-common-android-v1` Android 평가경로로 구현하고 기존공통후처리의 동등성을 확인했다. 기존 R0 `EllipseCandidate` quality의 의미를 보존하고 평가B의geometry/measuredSupport를분리했다. main의순수EllipseGeometry/toMarker와androidTest의anchor/source 대표선택을사용한다. support는median3/Canny40/90/dilate5,72점전체분모와floor(p+.5);sort는support내림차순→canonical(cx,cy,a,b,angle)→sourceID. same-rim은center≤.1min-b/축상대≤.1/각≤5°(두near-circle≤1.1은각무시)이며anchorgeometry는고정하고출처만합친다. 없는fitError/contrast/NFAquality는미측정이며가짜0을넣지않는다.

이 계약은 새 품질/정식 내외곽 통합 설계를 추가하지 않는다. 호출지역RAII/전역native직렬실행/전후취소검사/late result폐기와시험APK격리가실행으로확인됐다. native즉시취소·실제OOM·대표기기peak·현장인수는입증하지않았다. 같은gray10행의geometry/support/order/좌표/상태동등성이확인됐지만cap151full/cropCOUNT_LIMIT와metal37FN18은남는다. v6의주석정정및새b/P0와center-only/v5t해석을분리한다. 기존선택/철회/Task5–8계약은유지한다. [실행검증](../../verification.md#task-4-edgedrawing-android-평가-경로--2026-10-14)참조.


### Native 내부 소유권 가정의 정정 — 2026-10-15

호출지역Mat/Ptr만으로upstream4.12의내부scratch까지예외안전하다는가정은반증됐다. Join/ellipse/부분matrix/생성자/NFA교체의수동소유를std::unique_ptr로보완했다. 원본버전+ownership1과새hash를기록하고검출계산/옵션/공통품질/대표계약은유지한다. 실제RED및1,641지점할당실패/live0·Android50·같은10행/9합성동등성은 [최종소유권검증](../../verification.md#task4-소유권-회귀-수정과-최종-검증--2026-10-15)을따른다. 이변경은Task4의필수소유권수정이며Task5품질/Task6통합/gray-only최적화가아니다.


### Task6 pair 비용과 영상 관계의 한계 — 2026-10-15

후보상한10000만으로same-rim/물체pair비용을제한할수없으므로각단계1,000,000검사예산과초과CANDIDATE_SATURATION계약을추가했다. 판정·대표순서는그대로이며trace예산이출력/출처를자르지않는다.

밝은annular-band/aperture/exterior와고정geometry관계는개발사진의실제캡내외곽중복을줄였지만모든물체ID를증명하지않는다. parent+두aperture후보에서parent도남는3개결과는정확히2개인수조건을충족하지않는다. 따라서두시험정책은보류/defaultnull이며parent-only제거나새품질/geometry를몰래추가하지않는다. simple same-rim은재사용하고표준거리/customsolver/hierarchy를도입하지않았다. [Task6실행기록](../plans/2026-10-13-auto-counting-improvement.md#task6-조건부-내외곽-실험과-보류--2026-10-15)을따른다. 독립인수/제품검증대기상태는유지한다.
