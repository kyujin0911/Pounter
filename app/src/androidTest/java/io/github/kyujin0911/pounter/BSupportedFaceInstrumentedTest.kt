package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import java.util.concurrent.CancellationException
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class BSupportedFaceInstrumentedTest {
    private fun rep(id: Int, x: Double, y: Double, a: Double, b: Double = a) =
        BRepresentative(BObservation(id, EllipseGeometry(x, y, a, b, 0.0), 1.0), listOf(id))

    @Test
    fun brighterExteriorDoesNotChangeTheIdentityOfTheSameContinuousFace() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(240.0))
        try {
            Imgproc.circle(gray, Point(80.0, 80.0), 25, Scalar(150.0), -1)
            Imgproc.circle(gray, Point(80.0, 80.0), 8, Scalar(30.0), -1)
            assertEquals(
                1,
                selectBSupportedFaces(
                        gray,
                        listOf(rep(0, 80.0, 80.0, 8.0), rep(1, 80.0, 80.0, 25.0)),
                    )
                    .size,
            )
            gray.setTo(Scalar(30.0))
            assertEquals(
                2,
                selectBSupportedFaces(
                        gray,
                        listOf(rep(0, 80.0, 80.0, 8.0), rep(1, 80.0, 80.0, 25.0)),
                    )
                    .size,
            )
        } finally {
            gray.release()
        }
    }

    @Test
    fun spatialRaysPreserveRotatedAndPiWrappedEllipseIdentity() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(30.0))
        try {
            for (angle in listOf(0.0, 37.0, 89.0)) {
                gray.setTo(Scalar(30.0))
                Imgproc.ellipse(
                    gray,
                    Point(80.0, 80.0),
                    Size(31.0, 25.0),
                    angle,
                    0.0,
                    360.0,
                    Scalar(210.0),
                    -1,
                )
                Imgproc.ellipse(
                    gray,
                    Point(80.0, 80.0),
                    Size(10.0, 8.0),
                    angle,
                    0.0,
                    360.0,
                    Scalar(30.0),
                    -1,
                )
                for (delta in listOf(1.0, 179.0)) {
                    val small = rep(0, 80.0, 80.0, 10.0, 8.0)
                    val large = rep(1, 80.0, 80.0, 31.0, 25.0)
                    val input =
                        listOf(
                            small.copy(
                                anchor =
                                    small.anchor.copy(
                                        geometry =
                                            small.anchor.geometry.copy(
                                                angleRadians = (angle + delta) * PI / 180
                                            )
                                    )
                            ),
                            large.copy(
                                anchor =
                                    large.anchor.copy(
                                        geometry =
                                            large.anchor.geometry.copy(
                                                angleRadians = angle * PI / 180
                                            )
                                    )
                            ),
                        )
                    assertEquals(
                        "angle=$angle delta=$delta",
                        1,
                        selectBSupportedFaces(gray, input).size,
                    )
                }
            }
            val outer = EllipseGeometry(80.0, 80.0, 25.0, 25.0, 0.0)
            assertTrue(bContinuousGeometry(EllipseGeometry(85.59, 80.0, 8.0, 8.0, 0.0), outer))
            assertFalse(bContinuousGeometry(EllipseGeometry(85.61, 80.0, 8.0, 8.0, 0.0), outer))
            assertTrue(bContinuousGeometry(EllipseGeometry(80.0, 80.0, 5.0, 5.0, 0.0), outer))
            assertFalse(bContinuousGeometry(EllipseGeometry(80.0, 80.0, 4.99, 4.99, 0.0), outer))
        } finally {
            gray.release()
        }
    }

    @Test
    fun gradualFaceVariationHasOneObjectButAnInternalBoundaryStaysSeparate() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1)
        try {
            val pixels = ByteArray(180 * 300)
            for (y in 0 until 180) for (x in 0 until 300) {
                val r = hypot(x - 80.0, y - 80.0)
                pixels[y * 300 + x] = (if (r in 8.0..25.0) 140 + x / 2 else 30).toByte()
            }
            gray.put(0, 0, pixels)
            val input = listOf(rep(0, 80.0, 80.0, 8.0), rep(1, 83.0, 80.0, 25.0))
            val before = gray.clone()
            try {
                val selected = selectBSupportedFaces(gray, input)
                assertEquals(1, selected.size)
                assertEquals(listOf(0, 1), selected.single().sourceIds)
                assertEquals(selected, selectBSupportedFaces(gray, input.reversed()))
                assertEquals(0.0, Core.norm(gray, before), 0.0)
            } finally {
                before.release()
            }
            // Independent ring within the proposed band: continuous-face evidence must fail.
            Imgproc.circle(gray, Point(80.0, 80.0), 16, Scalar(10.0), 3)
            assertEquals(2, selectBSupportedFaces(gray, input).size)
        } finally {
            gray.release()
        }
    }

    @Test
    fun parentAndDistinctAperturesCannotBecomeOneAndOuterOnlySurvives() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.ellipse(
                gray,
                Point(100.0, 80.0),
                Size(55.0, 36.0),
                0.0,
                0.0,
                360.0,
                Scalar(210.0),
                -1,
            )
            for (x in listOf(77.0, 123.0)) Imgproc.circle(gray, Point(x, 80.0), 8, Scalar(30.0), -1)
            val input =
                listOf(
                    rep(0, 77.0, 80.0, 8.0),
                    rep(1, 123.0, 80.0, 8.0),
                    rep(2, 100.0, 80.0, 55.0, 36.0),
                )
            val local = selectBLocalObjects(gray, input).representatives
            val out = selectBSupportedFaces(gray, selectBSharedEdges(gray, local))
            assertEquals(2, out.size)
            for (x in listOf(77.0, 123.0)) assertEquals(
                1,
                out.count {
                    hypot(it.anchor.geometry.centerXPx - x, it.anchor.geometry.centerYPx - 80.0) <=
                        3
                },
            )
            val outerOnly = listOf(rep(0, 100.0, 80.0, 55.0, 36.0), rep(1, 123.0, 80.0, 8.0))
            assertEquals(2, selectBSupportedFaces(gray, outerOnly).size)
        } finally {
            gray.release()
        }
    }

    @Test
    fun pairBudgetCancellationRetryAndFixedAnchorChainAreExplicit() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.circle(gray, Point(80.0, 80.0), 60, Scalar(210.0), -1)
            Imgproc.circle(gray, Point(80.0, 80.0), 24, Scalar(30.0), -1)
            Imgproc.circle(gray, Point(80.0, 80.0), 8, Scalar(210.0), -1)
            val input =
                listOf(rep(0, 80.0, 80.0, 8.0), rep(1, 80.0, 80.0, 24.0), rep(2, 80.0, 80.0, 60.0))
            val failure =
                assertThrows(DetectionFailure::class.java) { selectBSupportedFaces(gray, input, 0) }
            assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, failure.reason)
            var checks = 0
            assertThrows(CancellationException::class.java) {
                selectBSupportedFaces(
                    gray,
                    input,
                    checkActive = { if (++checks == 3) throw CancellationException() },
                )
            }
            val out = selectBSupportedFaces(gray, input)
            assertEquals(listOf(listOf(0, 1), listOf(2)), out.map { it.sourceIds })
            assertEquals(out, selectBSupportedFaces(gray, input.reversed()))
        } finally {
            gray.release()
        }
    }

    @Test
    fun fixedDenominatorMinimumSpreadAndContradictions() {
        val good = BooleanArray(72)
        for (q in 0 until 3) for (k in 0 until 8) good[q * 18 + k] = true
        assertTrue(bSupportedCoverage(good, 24))
        assertFalse(bSupportedCoverage(good, 25))
        good[0] = false
        assertFalse(bSupportedCoverage(good, 0))
        val oneArc = BooleanArray(72) { it < 30 }
        assertFalse(bSupportedCoverage(oneArc, 0))
        assertThrows(IllegalArgumentException::class.java) {
            bSupportedCoverage(BooleanArray(24), 0)
        }
    }

    @Test
    fun actualPixelSupportNeedsThreeQuadrantsAndMissingImageStaysUnsupported() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 180, CvType.CV_8UC1)
        try {
            val input = listOf(rep(0, 80.0, 80.0, 8.0), rep(1, 80.0, 80.0, 25.0))
            for (threeQuadrants in listOf(false, true)) {
                val pixels = ByteArray(180 * 180) { 30 }
                for (y in 0 until 180) for (x in 0 until 180) {
                    val radius = hypot(x - 80.0, y - 80.0)
                    if (radius in 8.0..25.0 && (x >= 80 || (threeQuadrants && y >= 80)))
                        pixels[y * 180 + x] = 210.toByte()
                }
                gray.put(0, 0, pixels)
                assertEquals(if (threeQuadrants) 1 else 2, selectBSupportedFaces(gray, input).size)
            }
            gray.setTo(Scalar(30.0))
            Imgproc.circle(gray, Point(4.0, 4.0), 25, Scalar(210.0), -1)
            Imgproc.circle(gray, Point(4.0, 4.0), 8, Scalar(30.0), -1)
            assertEquals(
                2,
                selectBSupportedFaces(gray, listOf(rep(0, 4.0, 4.0, 8.0), rep(1, 4.0, 4.0, 25.0)))
                    .size,
            )
        } finally {
            gray.release()
        }
    }

    @Test
    fun cancellationDuringImageEvidenceLeavesInputReusable() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 180, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.circle(gray, Point(80.0, 80.0), 25, Scalar(210.0), -1)
            Imgproc.circle(gray, Point(80.0, 80.0), 8, Scalar(30.0), -1)
            val input = listOf(rep(0, 80.0, 80.0, 8.0), rep(1, 80.0, 80.0, 25.0))
            var total = 0
            val expected = selectBSupportedFaces(gray, input, checkActive = { total++ })
            assertTrue(total > 72)
            var n = 0
            assertThrows(CancellationException::class.java) {
                selectBSupportedFaces(
                    gray,
                    input,
                    checkActive = { if (++n == total - 1) throw CancellationException() },
                )
            }
            assertEquals(expected, selectBSupportedFaces(gray, input))
        } finally {
            gray.release()
        }
    }
}
