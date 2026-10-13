package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.CountTask

class ValidationRequiredDetector : PipeDetector {
    override suspend fun detect(task: CountTask): Detection =
        throw IllegalStateException(
            "자동 계수는 검증 대기 중입니다. 정면 촬영 사진으로 검출 성능을 확인한 뒤 제공됩니다. 지금은 수동 표시를 사용할 수 있습니다."
        )
}
