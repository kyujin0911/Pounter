package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.CountTask

/** A test result, a demo, or a runtime flag cannot replace field acceptance. */
const val DETECTOR_VALIDATED = false

interface PipeDetector {
    suspend fun detect(task: CountTask): Detection
}
