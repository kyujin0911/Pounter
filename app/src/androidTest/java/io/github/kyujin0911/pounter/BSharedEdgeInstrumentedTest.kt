package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class BSharedEdgeInstrumentedTest {
    private fun rep(id: Int, x: Double, y: Double, r: Double) =
        BRepresentative(BObservation(id, EllipseGeometry(x, y, r, r, 0.0), 1.0), listOf(id))

    @Test
    fun prefilterExhaustionFallsBackWithoutNarrowingTheMergeBudget() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(160, 160, CvType.CV_8UC1, Scalar(210.0))
        try {
            Imgproc.circle(gray, Point(80.0, 80.0), 10, Scalar(30.0), -1)
            val input = (0..2).map { rep(it, 80.0, 80.0, 10.0) }
            assertEquals(
                listOf(0, 1, 2),
                selectBSharedEdges(gray, input, maxPairChecks = 2).single().sourceIds,
            )
        } finally {
            gray.release()
        }
    }

    @Test
    fun fixedAnchorPreventsSharedEdgeChainsAndCenterLimitIsInclusive() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(160, 160, CvType.CV_8UC1, Scalar(210.0))
        try {
            Imgproc.circle(gray, Point(84.0, 80.0), 10, Scalar(30.0), -1)
            val chain =
                listOf(rep(0, 80.0, 80.0, 10.0), rep(1, 84.0, 80.0, 10.0), rep(2, 88.0, 80.0, 10.0))
            val result = selectBSharedEdges(gray, chain)
            assertEquals(listOf(listOf(0, 1), listOf(2)), result.map { it.sourceIds })
            assertEquals(result, selectBSharedEdges(gray, chain.reversed()))
            for ((offset, count) in listOf(4.99 to 1, 5.0 to 1, 5.01 to 2)) {
                assertEquals(
                    count,
                    selectBSharedEdges(gray, listOf(chain[0], rep(1, 80.0 + offset, 80.0, 10.0)))
                        .size,
                )
            }
        } finally {
            gray.release()
        }
    }

    @Test
    fun eccentricBoundaryDistanceUsesPixelUnitsAtBothAxes() {
        assertEquals(4.0, bRadialBoundaryDistancePx(76.0, 0.0, 80.0, 32.0), 1e-10)
        assertTrue(bRadialBoundaryDistancePx(77.99, 0.0, 80.0, 32.0) > 2)
        assertTrue(bRadialBoundaryDistancePx(78.01, 0.0, 80.0, 32.0) < 2)
        assertTrue(bRadialBoundaryDistancePx(0.0, 29.99, 80.0, 32.0) > 2)
        assertTrue(bRadialBoundaryDistancePx(0.0, 30.01, 80.0, 32.0) < 2)
        assertTrue(bRadialBoundaryDistancePx(0.0, 0.0, 80.0, 32.0).isInfinite())
    }

    @Test
    fun sharedActualEdgeMergesShiftedFitsButEmptyOrIndependentEdgesDoNot() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(160, 160, CvType.CV_8UC1, Scalar(210.0))
        try {
            val fits = listOf(rep(0, 80.0, 80.0, 10.45), rep(1, 82.9, 78.4, 7.7))
            assertEquals(2, selectBSharedEdges(gray, fits).size)
            Imgproc.circle(gray, Point(80.0, 80.0), 10, Scalar(30.0), -1)
            val before = gray.clone()
            try {
                val result = selectBSharedEdges(gray, fits)
                assertEquals(1, result.size)
                assertEquals(listOf(0, 1), result.single().sourceIds)
                assertEquals(fits[0].anchor, result.single().anchor)
                assertEquals(result, selectBSharedEdges(gray, fits.reversed()))
                assertEquals(0.0, Core.norm(gray, before), 0.0)
                Imgproc.circle(gray, Point(103.0, 80.0), 8, Scalar(30.0), -1)
                assertEquals(
                    2,
                    selectBSharedEdges(gray, listOf(fits[0], rep(2, 103.0, 80.0, 8.0))).size,
                )
            } finally {
                before.release()
            }
            assertThrows(CancellationException::class.java) {
                selectBSharedEdges(
                    gray,
                    fits,
                    checkActive = { throw CancellationException("test") },
                )
            }
            assertEquals(
                DetectionFailureReason.CANDIDATE_SATURATION,
                assertThrows(DetectionFailure::class.java) {
                        selectBSharedEdges(gray, fits, maxPairChecks = 0)
                    }
                    .reason,
            )
        } finally {
            gray.release()
        }
    }
}
