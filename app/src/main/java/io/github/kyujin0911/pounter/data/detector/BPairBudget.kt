package io.github.kyujin0911.pounter.data.detector

internal fun bPairBudgetExceeded(stage: String): Nothing =
    throw DetectionFailure(
        DetectionFailureReason.CANDIDATE_SATURATION,
        IllegalStateException("B $stage pair budget exhausted; no partial result"),
    )
