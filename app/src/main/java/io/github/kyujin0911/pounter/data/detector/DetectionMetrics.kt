package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.Marker
import kotlin.math.hypot
import kotlin.math.max

data class DetectionMetrics(val matched: Int, val missing: Int, val falsePositive: Int) {
    val correctionCount: Int
        get() = missing + falsePositive
}

fun correctionAllowance(expectedCount: Int): Int {
    require(expectedCount > 0)
    return max(1, expectedCount / 20)
}

fun matchDetections(
    expected: List<Marker>,
    actual: List<Marker>,
    normalizedTolerance: Double,
): DetectionMetrics {
    require(normalizedTolerance.isFinite() && normalizedTolerance >= 0)
    require(
        expected.map { it.id }.distinct().size == expected.size &&
            actual.map { it.id }.distinct().size == actual.size
    )
    return maximumMatch(expected, actual) { target, detection ->
            hypot((target.x - detection.x).toDouble(), (target.y - detection.y).toDouble()).takeIf {
                it <= normalizedTolerance
            }
        }
        .metrics
}

data class DetectionMatchResult(
    val metrics: DetectionMetrics,
    val expectedToActualIds: Map<String, String>,
    val unmatchedExpectedIds: List<String>,
    val unmatchedActualIds: List<String>,
) {
    val netDifference: Int
        get() = metrics.falsePositive - metrics.missing
}

/** Coordinates are normalized against the whole EXIF-oriented reference, never the ROI. */
fun matchDetectionsInReferencePixels(
    expected: List<Marker>,
    actual: List<Marker>,
    referenceWidth: Int,
    referenceHeight: Int,
    tolerancesPx: Map<String, Double>,
): DetectionMatchResult {
    require(referenceWidth > 0 && referenceHeight > 0)
    for (markers in listOf(expected, actual)) {
        require(markers.map { it.id }.distinct().size == markers.size)
        require(
            markers.all {
                it.id.isNotBlank() &&
                    it.x.isFinite() &&
                    it.y.isFinite() &&
                    it.x in 0f..1f &&
                    it.y in 0f..1f
            }
        )
    }
    require(tolerancesPx.keys == expected.map { it.id }.toSet())
    require(tolerancesPx.values.all { it.isFinite() && it > 0 })
    return maximumMatch(expected, actual) { target, detection ->
        hypot(
                (target.x.toDouble() - detection.x) * referenceWidth,
                (target.y.toDouble() - detection.y) * referenceHeight,
            )
            .takeIf { it <= tolerancesPx.getValue(target.id) }
    }
}

private fun maximumMatch(
    expected: List<Marker>,
    actual: List<Marker>,
    distance: (Marker, Marker) -> Double?,
): DetectionMatchResult {
    val targets = expected.sortedBy { it.id }
    val detections = actual.sortedBy { it.id }
    val adjacency =
        targets.map { target ->
            detections.indices
                .mapNotNull { index -> distance(target, detections[index])?.let { index to it } }
                .sortedWith(
                    compareBy<Pair<Int, Double>> { it.second }.thenBy { detections[it.first].id }
                )
                .map { it.first }
        }
    val owners = IntArray(detections.size) { -1 }
    fun augment(target: Int, visited: BooleanArray): Boolean {
        for (index in adjacency[target]) {
            if (visited[index]) continue
            visited[index] = true
            if (owners[index] < 0 || augment(owners[index], visited)) {
                owners[index] = target
                return true
            }
        }
        return false
    }
    val matched = targets.indices.count { augment(it, BooleanArray(detections.size)) }
    val pairs =
        detections.indices
            .filter { owners[it] >= 0 }
            .associate { targets[owners[it]].id to detections[it].id }
    return DetectionMatchResult(
        DetectionMetrics(matched, targets.size - matched, detections.size - matched),
        pairs,
        targets.filter { it.id !in pairs }.map { it.id },
        detections.indices.filter { owners[it] < 0 }.map { detections[it].id },
    )
}
