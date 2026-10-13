package io.github.kyujin0911.pounter.data.detector
enum class DetectionFailureReason {
    PHOTO_READ,
    INITIALIZATION,
    INVALID_INPUT,
    PROCESSING,
    CANDIDATE_SATURATION,
    COUNT_LIMIT,
}

class DetectionFailure(val reason: DetectionFailureReason, cause: Throwable? = null) :
    Exception(reason.name, cause)
