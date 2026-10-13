package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.data.detector.*
import org.junit.Assert.*
import org.junit.Test

class DetectionMetricsTest {
    private fun m(id: String, x: Float, y: Float = .5f) = Marker(id, x, y, .01f)

    @Test
    fun duplicateDetectionMatchesOneTargetOnly() {
        assertEquals(
            DetectionMetrics(1, 0, 1),
            matchDetections(listOf(m("a", .5f)), listOf(m("b", .5f), m("c", .5f)), .02),
        )
    }

    @Test
    fun equalCountsDoNotHideWrongPositions() {
        val result = matchDetections(listOf(m("a", .1f)), listOf(m("b", .9f)), .02)
        assertEquals(DetectionMetrics(0, 1, 1), result)
        assertEquals(2, result.correctionCount)
    }

    @Test
    fun correctionAllowanceIsPerPhoto() {
        assertEquals(1, correctionAllowance(10))
        assertEquals(1, correctionAllowance(20))
        assertEquals(15, correctionAllowance(300))
    }

    @Test
    fun augmentingPathPreservesMaximumMatch() {
        // a can match x or y; b only x. Greedy a→x loses b, optimum a→y/b→x.
        val expected = listOf(m("a", .4f), m("b", .5f))
        val actual = listOf(m("x", .44f), m("y", .34f))
        assertEquals(DetectionMetrics(2, 0, 0), matchDetections(expected, actual, .07))
        assertEquals(
            DetectionMetrics(2, 0, 0),
            matchDetections(expected.reversed(), actual.reversed(), .07),
        )
    }

    @Test
    fun actualZeroIsSeparatelyCounted() {
        assertEquals(
            DetectionMetrics(0, 0, 1),
            matchDetections(emptyList(), listOf(m("x", .4f)), .02),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidToleranceRejected() {
        matchDetections(emptyList(), emptyList(), Double.NaN)
    }
}
