package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/** Object counts are separate from unchanged same-rim boundary-preservation contracts. */
class BContinuousObjectInstrumentedTest {
    private fun sharedObjects(
        gray: Mat,
        input: List<BRepresentative>,
        entryBudget: Int = 25000,
        maxPairChecks: Int = 1_000_000,
        checkActive: () -> Unit = {},
    ): BRadialRun {
        val run = selectBLocalObjects(gray, input, entryBudget, maxPairChecks, checkActive)
        return run.copy(
            representatives =
                selectBContinuousFaces(
                    gray,
                    selectBSharedEdges(gray, run.representatives, maxPairChecks, checkActive),
                    maxPairChecks,
                    checkActive,
                )
        )
    }

    private fun rep(id: Int, x: Double, r: Double, b: Double = r) =
        BRepresentative(BObservation(id, EllipseGeometry(x, 80.0, r, b, 0.0), 1.0), listOf(id))

    private fun ring(gray: Mat, x: Double, inner: Int = 8, outer: Int = 24) {
        Imgproc.circle(gray, Point(x, 80.0), outer, Scalar(210.0), -1)
        Imgproc.circle(gray, Point(x, 80.0), inner, Scalar(30.0), -1)
    }

    @Test
    fun visibleObjectsHaveOneMarkerEachIncludingTwoRealAperturesAndParent() {
        assertTrue(OpenCVLoader.initLocal())
        for (name in
            listOf(
                "ring",
                "parent",
                "touching",
                "small",
                "capless",
                "bright",
                "outer",
                "reflection",
                "occlusion",
            )) {
            val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(30.0))
            try {
                var input = listOf(rep(0, 80.0, 8.0), rep(1, 80.0, 24.0))
                var expected = listOf(80.0)
                when (name) {
                    "parent" -> {
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
                        for (x in listOf(77.0, 123.0)) Imgproc.circle(
                            gray,
                            Point(x, 80.0),
                            8,
                            Scalar(30.0),
                            -1,
                        )
                        input =
                            listOf(rep(0, 77.0, 8.0), rep(1, 123.0, 8.0), rep(2, 100.0, 55.0, 36.0))
                        expected = listOf(77.0, 123.0)
                    }
                    "touching",
                    "small" -> {
                        ring(gray, 80.0)
                        val small = name == "small"
                        val x = if (small) 115.0 else 128.0
                        val inner = if (small) 4 else 8
                        val outer = if (small) 9 else 24
                        ring(gray, x, inner, outer)
                        input =
                            input + listOf(rep(2, x, inner.toDouble()), rep(3, x, outer.toDouble()))
                        expected = listOf(80.0, x)
                    }
                    "capless" -> {
                        Imgproc.circle(gray, Point(80.0, 80.0), 20, Scalar(210.0), 3)
                        input = listOf(rep(0, 80.0, 20.0))
                    }
                    "bright",
                    "outer" -> {
                        Imgproc.circle(gray, Point(80.0, 80.0), 24, Scalar(210.0), -1)
                        if (name == "bright")
                            Imgproc.circle(gray, Point(80.0, 80.0), 8, Scalar(245.0), -1)
                        else input = listOf(rep(0, 80.0, 24.0))
                    }
                    "reflection",
                    "occlusion" -> {
                        ring(gray, 80.0, 15)
                        input = listOf(rep(0, 80.0, 15.0), rep(1, 80.0, 24.0))
                        if (name == "reflection")
                            Imgproc.rectangle(
                                gray,
                                Point(70.0, 64.0),
                                Point(75.0, 95.0),
                                Scalar(150.0),
                                -1,
                            )
                        else
                            Imgproc.rectangle(
                                gray,
                                Point(55.0, 52.0),
                                Point(105.0, 68.0),
                                Scalar(30.0),
                                -1,
                            )
                    }
                    else -> ring(gray, 80.0)
                }
                val before = gray.clone()
                try {
                    if (name == "parent") {
                        assertEquals(
                            3,
                            groupBCrossSectionBoundaries(gray, input, BObjectPolicy.ANNULUS_90)
                                .representatives
                                .size,
                        )
                    }
                    val result = sharedObjects(gray, input)
                    assertEquals(name, expected.size, result.representatives.size)
                    for (x in expected) assertEquals(
                        name,
                        1,
                        result.representatives.count {
                            hypot(
                                it.anchor.geometry.centerXPx - x,
                                it.anchor.geometry.centerYPx - 80.0,
                            ) <= 3
                        },
                    )
                    assertEquals(name, 0.0, Core.norm(gray, before), 0.0)
                    assertEquals(
                        name,
                        result.representatives,
                        sharedObjects(gray, input.reversed(), entryBudget = 0).representatives,
                    )
                } finally {
                    before.release()
                }
            } finally {
                gray.release()
            }
        }
    }

    @Test
    fun parentCannotSupplyItsOwnChildEvidenceAndEraseAnOuterOnlyObject() {
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
            for (x in listOf(100.0, 123.0)) Imgproc.circle(
                gray,
                Point(x, 80.0),
                8,
                Scalar(30.0),
                -1,
            )
            val outerOnly = listOf(rep(0, 100.0, 55.0, 36.0), rep(1, 123.0, 8.0))
            val retained = sharedObjects(gray, outerOnly).representatives
            assertEquals(2, retained.size)
            assertEquals(setOf(0, 1), retained.map { it.anchor.id }.toSet())
            val withIndependentInner =
                sharedObjects(gray, outerOnly + rep(2, 100.0, 8.0)).representatives
            assertEquals(2, withIndependentInner.size)
            assertEquals(setOf(1, 2), withIndependentInner.map { it.anchor.id }.toSet())
        } finally {
            gray.release()
        }
    }

    @Test
    fun budgetAndCancellationNeverReturnPartialObjectsAndInputRemainsReusable() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(30.0))
        try {
            ring(gray, 80.0)
            val input = listOf(rep(0, 80.0, 8.0), rep(1, 80.0, 24.0))
            val failure =
                assertThrows(DetectionFailure::class.java) {
                    sharedObjects(gray, input, maxPairChecks = 0)
                }
            assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, failure.reason)
            var checkpoints = 0
            assertThrows(CancellationException::class.java) {
                sharedObjects(
                    gray,
                    input,
                    checkActive = { if (++checkpoints == 3) throw CancellationException("test") },
                )
            }
            assertEquals(1, sharedObjects(gray, input).representatives.size)
        } finally {
            gray.release()
        }
    }
}
