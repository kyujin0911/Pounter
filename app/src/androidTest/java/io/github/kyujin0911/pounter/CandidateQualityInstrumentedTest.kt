package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class CandidateQualityInstrumentedTest {
    private val circle = EllipseGeometry(60.0, 60.0, 30.0, 30.0, 0.0)

    private fun pixels(g: EllipseGeometry = circle, hit: (Int) -> Boolean): ByteArray {
        val data = ByteArray(120 * 120)
        for (n in 0 until 72) {
            val t = n * 2 * PI / 72
            val x =
                floor(
                        g.centerXPx + g.semiMajorPx * cos(t) * cos(g.angleRadians) -
                            g.semiMinorPx * sin(t) * sin(g.angleRadians) + .5
                    )
                    .toInt()
            val y =
                floor(
                        g.centerYPx +
                            g.semiMajorPx * cos(t) * sin(g.angleRadians) +
                            g.semiMinorPx * sin(t) * cos(g.angleRadians) +
                            .5
                    )
                    .toInt()
            if (x in 0 until 120 && y in 0 until 120 && hit(n)) data[y * 120 + x] = -1
        }
        return data
    }

    @Test
    fun gapWrapsAcrossZeroWithoutChangingTotalSupport() {
        val q = measureBEdgeQuality(pixels { it in 10..61 }, 120, 120, circle)
        assertEquals(52, q.supported)
        assertEquals(20, q.longestGapSamples)
        assertEquals(100.0, q.longestGapDegrees, 0.0)
        assertEquals(0, q.invalid)
        assertEquals(20, q.unsupported)
        assertFalse(q.undersampled)
        assertTrue(BQualityPolicy.GAP_90.rejects(q))
        assertFalse(BQualityPolicy.GAP_120.rejects(q))
        assertFalse(BQualityPolicy.OBSERVE.rejects(q))
    }

    @Test
    fun outsideIsInvalidInsideBlankUnsupportedNoClampOrShrunkenDenominator() {
        val g = circle.copy(centerXPx = 0.0)
        val q = measureBEdgeQuality(ByteArray(120 * 120) { -1 }, 120, 120, g)
        assertTrue(q.invalid > 0)
        assertEquals(72 - q.invalid, q.supported)
        assertEquals(0, q.unsupported)
        assertTrue(q.support < 1)
        assertFalse(BQualityPolicy.GAP_90.rejects(q))
        val blank = measureBEdgeQuality(ByteArray(120 * 120), 120, 120, circle)
        assertEquals(72, blank.unsupported)
        assertEquals(72, blank.longestGapSamples)
        assertEquals(0.0, blank.support, 0.0)
    }

    @Test
    fun smallAndCoarseSamplingAreObservableNeverAutomaticallyRejected() {
        for (g in
            listOf(
                circle.copy(semiMajorPx = 2.0, semiMinorPx = 2.0),
                circle.copy(semiMajorPx = 80.0, semiMinorPx = 20.0, angleRadians = PI / 4),
            )) {
            val q = measureBEdgeQuality(ByteArray(120 * 120), 120, 120, g)
            assertTrue(q.undersampled)
            assertFalse(BQualityPolicy.GAP_90.rejects(q))
            assertTrue(q.distinctPixels in 1..72)
            assertTrue(q.maxStepPx.isFinite())
        }
    }

    @Test
    fun fragmentedNeighborsAndPartialRimCanShareSupportButDifferInGap() {
        val joined = measureBEdgeQuality(pixels { it in 12..59 }, 120, 120, circle)
        val fragments = measureBEdgeQuality(pixels { it % 3 != 0 }, 120, 120, circle)
        assertEquals(joined.supported, fragments.supported)
        assertEquals(24, joined.longestGapSamples)
        assertEquals(1, fragments.longestGapSamples)
        // This is a distribution measurement, not a physical object classifier.
        assertTrue(BQualityPolicy.GAP_90.rejects(joined))
        assertFalse(BQualityPolicy.GAP_90.rejects(fragments))
    }

    @Test
    fun qualityDoesNotChangeZeroSaturationOrTraceBudgetContracts() {
        assertTrue(org.opencv.android.OpenCVLoader.initLocal())
        val gray = org.opencv.core.Mat.zeros(120, 120, org.opencv.core.CvType.CV_8UC1)
        try {
            for (policy in BQualityPolicy.entries) {
                val zero = processBGeometry(gray, DoubleArray(0), qualityPolicy = policy)
                assertEquals(0, zero.qualityCount)
                assertTrue(zero.representatives.isEmpty())
                val emptySupported =
                    processBGeometry(
                        gray,
                        doubleArrayOf(60.0, 60.0, 30.0, 0.0, 0.0, 0.0),
                        entryBudget = 0,
                        qualityPolicy = policy,
                    )
                assertEquals(1, emptySupported.gateCount)
                assertEquals(0, emptySupported.qualityCount)
                assertTrue(emptySupported.observations.isEmpty())
                try {
                    processBGeometry(
                        gray,
                        DoubleArray(12),
                        maxCandidates = 1,
                        qualityPolicy = policy,
                    )
                    fail("saturation must be failure")
                } catch (e: DetectionFailure) {
                    assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, e.reason)
                }
            }
            assertFalse(gray.empty()) // caller still owns the input Mat
        } finally {
            gray.release()
        }
    }
}
