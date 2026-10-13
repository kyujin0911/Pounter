package io.github.kyujin0911.pounter.data.detector
// Evaluation-only configuration. Synthetic values are not field defaults.
data class DetectionConfig(
    val detectorVersion: String,
    val analysisMaxSidePx: Int,
    val maxDecodedPixels: Long,
    val maxCandidates: Int,
    val medianKernelSize: Int,
    val cannyLow: Double,
    val cannyHigh: Double,
    val adaptiveBlockSize: Int,
    val adaptiveOffset: Double,
    val minSemiAxisPx: Double,
    val maxSemiAxisPx: Double,
    val maxAxisRatio: Double,
    val minEdgeSupport: Double,
    val maxFitError: Double,
    val minContrast: Double,
    val mergePolicy: MergePolicy,
) {
    init {
        require(
            detectorVersion.isNotBlank() &&
                analysisMaxSidePx > 0 &&
                maxDecodedPixels > 0 &&
                maxCandidates > 0
        )
        require(
            medianKernelSize >= 3 &&
                medianKernelSize % 2 == 1 &&
                adaptiveBlockSize >= 3 &&
                adaptiveBlockSize % 2 == 1
        )
        require(
            listOf(
                    cannyLow,
                    cannyHigh,
                    adaptiveOffset,
                    minSemiAxisPx,
                    maxSemiAxisPx,
                    maxAxisRatio,
                    minEdgeSupport,
                    maxFitError,
                    minContrast,
                )
                .all { it.isFinite() }
        )
        require(
            cannyLow >= 0 &&
                cannyHigh > cannyLow &&
                minSemiAxisPx > 0 &&
                maxSemiAxisPx >= minSemiAxisPx &&
                maxAxisRatio >= 1 &&
                minEdgeSupport in 0.0..1.0 &&
                maxFitError >= 0 &&
                minContrast in 0.0..255.0
        )
    }

    companion object {
        fun synthetic() =
            DetectionConfig(
                "synthetic-v1",
                1600,
                4_000_000,
                10000,
                3,
                40.0,
                90.0,
                31,
                4.0,
                7.0,
                80.0,
                2.5,
                .65,
                .15,
                0.0,
                MergePolicy(.3, .5, .85),
            )
    }
}
