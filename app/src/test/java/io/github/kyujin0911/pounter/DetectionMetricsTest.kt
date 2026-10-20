package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.data.detector.*
import org.junit.Assert.*
import org.junit.Test

class DetectionMetricsTest {
    private fun m(id: String, x: Float, y: Float = .5f) = Marker(id, x, y, .01f)

    @Test
    fun referencePixelsRespectNonSquareImageAndExposeIds() {
        val expected = listOf(m("a", .5f))
        for (actual in listOf(m("x", .51f), m("x", .5f, .52f))) {
            val result =
                matchDetectionsInReferencePixels(
                    expected,
                    listOf(actual),
                    1000,
                    500,
                    mapOf("a" to 10.001),
                )
            assertEquals(DetectionMetrics(1, 0, 0), result.metrics)
            assertEquals(mapOf("a" to "x"), result.expectedToActualIds)
        }
    }

    @Test
    fun referencePixelsKeepMaximumMatchingAndDuplicateErrors() {
        val result =
            matchDetectionsInReferencePixels(
                listOf(m("a", .4f), m("b", .5f)),
                listOf(m("x", .44f), m("y", .34f)),
                1000,
                500,
                mapOf("a" to 70.0, "b" to 70.0),
            )
        assertEquals(mapOf("a" to "y", "b" to "x"), result.expectedToActualIds)
        val duplicate =
            matchDetectionsInReferencePixels(
                listOf(m("a", .5f)),
                listOf(m("x", .5f), m("y", .5f)),
                1000,
                500,
                mapOf("a" to 1.0),
            )
        assertEquals(DetectionMetrics(1, 0, 1), duplicate.metrics)
        assertEquals(1, duplicate.unmatchedActualIds.size)
        assertEquals(
            DetectionMetrics(0, 1, 1),
            matchDetectionsInReferencePixels(
                    listOf(m("a", .1f)),
                    listOf(m("x", .9f)),
                    1000,
                    500,
                    mapOf("a" to 1.0),
                )
                .metrics,
        )
    }

    @Test
    fun referencePixelsValidateKeysDimensionsToleranceAndEmptyTruth() {
        assertEquals(
            DetectionMetrics(0, 0, 0),
            matchDetectionsInReferencePixels(emptyList(), emptyList(), 1000, 500, emptyMap())
                .metrics,
        )
        for (tolerance in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                matchDetectionsInReferencePixels(
                    listOf(m("a", .5f)),
                    emptyList(),
                    1000,
                    500,
                    mapOf("a" to tolerance),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            matchDetectionsInReferencePixels(
                listOf(m("a", .5f)),
                emptyList(),
                1000,
                500,
                mapOf("other" to 1.0),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            matchDetectionsInReferencePixels(emptyList(), emptyList(), 0, 500, emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            matchDetectionsInReferencePixels(
                listOf(m("a", .5f), m("a", .6f)),
                emptyList(),
                1000,
                500,
                mapOf("a" to 1.0),
            )
        }
    }

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
