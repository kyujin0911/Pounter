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
    val targets = expected.sortedBy { it.id }
    val detections = actual.sortedBy { it.id }
    val adjacency =
        targets.map { target ->
            detections.indices
                .filter { index ->
                    hypot(
                        (target.x - detections[index].x).toDouble(),
                        (target.y - detections[index].y).toDouble(),
                    ) <= normalizedTolerance
                }
                .sortedWith(
                    compareBy<Int> {
                            hypot(
                                (target.x - detections[it].x).toDouble(),
                                (target.y - detections[it].y).toDouble(),
                            )
                        }
                        .thenBy { detections[it].id }
                )
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
    return DetectionMetrics(matched, targets.size - matched, detections.size - matched)
}
