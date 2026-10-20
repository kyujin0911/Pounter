package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*

data class MergePolicy(
    val centerToleranceRatio: Double,
    val maxBoundaryGapRatio: Double,
    val minContainmentFraction: Double,
) {
    init {
        require(
            centerToleranceRatio.isFinite() &&
                centerToleranceRatio >= 0 &&
                maxBoundaryGapRatio in 0.0..1.0 &&
                minContainmentFraction in 0.0..1.0
        )
    }
}

fun mergeCandidates(
    candidates: List<EllipseCandidate>,
    policy: MergePolicy,
    onRepresentative: (EllipseCandidate, Int) -> Unit = { _, _ -> },
): List<EllipseCandidate> {
    val ordered =
        candidates.sortedWith(
            compareByDescending<EllipseCandidate> { it.edgeSupport }
                .thenBy { it.fitError }
                .thenByDescending { it.contrast }
                .thenByDescending { it.semiMajorPx }
                .thenByDescending { it.semiMinorPx }
                .thenBy { it.centerXPx }
                .thenBy { it.centerYPx }
                .thenBy { it.angleRadians }
        )
    val representatives = mutableListOf<EllipseCandidate>()
    for (candidate in ordered) {
        val index = representatives.indexOfFirst { sameRim(it, candidate, policy) }
        onRepresentative(candidate, if (index < 0) representatives.size else index)
        if (index < 0) representatives.add(candidate)
        else
            representatives[index] =
                representatives[index].copy(
                    sources = representatives[index].sources + candidate.sources
                )
    }
    return representatives
}

private fun sameRim(a: EllipseCandidate, b: EllipseCandidate, p: MergePolicy): Boolean {
    if (
        hypot(a.centerXPx - b.centerXPx, a.centerYPx - b.centerYPx) >
            p.centerToleranceRatio * min(a.semiMinorPx, b.semiMinorPx)
    )
        return false
    if (
        abs(a.semiMajorPx - b.semiMajorPx) / max(a.semiMajorPx, b.semiMajorPx) >
            p.maxBoundaryGapRatio ||
            abs(a.semiMinorPx - b.semiMinorPx) / max(a.semiMinorPx, b.semiMinorPx) >
                p.maxBoundaryGapRatio
    )
        return false
    val (inner, outer) =
        if (a.semiMajorPx * a.semiMinorPx <= b.semiMajorPx * b.semiMinorPx) Pair(a, b)
        else Pair(b, a)
    // ponytail: 64 perimeter samples; increase only if field boundary evidence warrants it.
    val contained =
        (0 until 64).count {
            val point = inner.boundaryPoint(it * 2 * PI / 64)
            outer.normalizedRadius(point.first, point.second) <= 1.01
        }
    return contained / 64.0 >= p.minContainmentFraction
}
