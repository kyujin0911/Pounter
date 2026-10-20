package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class BObjectMergeInstrumentedTest {
    private fun o(id: Int, x: Double, r: Double, y: Double = 80.0, support: Double = 1.0) =
        BObservation(id, EllipseGeometry(x, y, r, r, 0.0), support)

    private fun reps(vararg obs: BObservation) = mergeBObservations(obs.toList())

    private fun image(block: (Mat) -> Unit) {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 300, CvType.CV_8UC1, Scalar(30.0))
        try {
            block(gray)
        } finally {
            gray.release()
        }
    }

    private fun ring(gray: Mat, x: Double, y: Double = 80.0, inner: Int = 8, outer: Int = 24) {
        Imgproc.circle(gray, Point(x, y), outer, Scalar(210.0), -1)
        Imgproc.circle(gray, Point(x, y), inner, Scalar(30.0), -1)
    }

    @Test
    fun sameRimShapeSourcePermutationAngleAndAnchorChain() {
        val input =
            listOf(
                o(0, 50.0, 20.0),
                o(1, 51.0, 20.0),
                o(2, 53.0, 20.0),
                o(3, 90.0, 20.0),
                o(4, 120.0, 8.0),
                o(5, 145.0, 16.0),
            )
        val base = mergeBObservations(input)
        assertEquals(listOf(0, 2, 3, 4, 5), base.map { it.anchor.id })
        assertEquals(listOf(0, 1), base.first().sourceIds)
        for (k in input.indices) assertEquals(
            base,
            mergeBObservations(input.drop(k) + input.take(k)),
        )
        val a = canonicalEdgeDrawing(doubleArrayOf(50.0, 50.0, 0.0, 24.0, 12.0, 20.0), 0)
        val b = canonicalEdgeDrawing(doubleArrayOf(50.0, 50.0, 0.0, 24.0, 12.0, 200.0), 0)
        assertEquals(
            1,
            mergeBObservations(listOf(BObservation(7, a, 1.0), BObservation(8, b, 1.0))).size,
        )
        assertEquals(
            canonicalEdgeDrawing(doubleArrayOf(50.0, 50.0, 8.0, 0.0, 0.0, 0.0), 0),
            canonicalEdgeDrawing(doubleArrayOf(50.0, 50.0, 8.0, 0.0, 0.0, 131.0), 0),
        )
    }

    @Test
    fun sameRimPairBudgetExhaustionNeverReturnsPartialSuccess() {
        val e =
            assertThrows(DetectionFailure::class.java) {
                mergeBObservations(listOf(o(1, 50.0, 8.0), o(2, 100.0, 8.0)), maxPairChecks = 0)
            }
        assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, e.reason)
        assertTrue(e.cause!!.message!!.contains("same-rim"))
    }

    @Test
    fun imageSupportedAnnulusUsesExistingRankedAnchorAndSources() = image { gray ->
        ring(gray, 80.0)
        val input = reps(o(1, 80.0, 8.0), o(2, 80.0, 24.0))
        val before = gray.clone()
        try {
            for (policy in BObjectPolicy.entries) {
                val full = groupBCrossSectionBoundaries(gray, input, policy)
                assertEquals(1, full.representatives.size)
                assertEquals(input.first().anchor, full.representatives.single().anchor)
                assertEquals(listOf(1, 2), full.representatives.single().sourceIds)
                assertEquals(1, full.mergedCount)
                for (budget in listOf(0, 1, 3)) {
                    val short =
                        groupBCrossSectionBoundaries(gray, input, policy, entryBudget = budget)
                    assertEquals(full.representatives, short.representatives)
                    assertEquals(full.reasonCounts, short.reasonCounts)
                    assertEquals(full.pairChecks, short.pairChecks)
                    assertTrue(short.decisions.size <= budget)
                }
            }
            assertEquals(0.0, Core.norm(gray, before), 0.0)
        } finally {
            before.release()
        }
    }

    @Test
    fun neighboringCaplessAndTwoTouchingAnnuliRemainTwoObjects() = image { gray ->
        ring(gray, 80.0)
        ring(gray, 128.0)
        val input = reps(o(1, 80.0, 8.0), o(2, 80.0, 24.0), o(3, 128.0, 8.0), o(4, 128.0, 24.0))
        for (p in BObjectPolicy.entries) {
            val r = groupBCrossSectionBoundaries(gray, input, p)
            assertEquals(2, r.representatives.size)
            assertEquals(listOf(listOf(1, 2), listOf(3, 4)), r.representatives.map { it.sourceIds })
        }
        Imgproc.circle(gray, Point(180.0, 80.0), 20, Scalar(210.0), 3)
        val r =
            groupBCrossSectionBoundaries(
                gray,
                reps(o(1, 80.0, 8.0), o(2, 80.0, 24.0), o(5, 180.0, 20.0)),
                BObjectPolicy.ANNULUS_ALL,
            )
        assertEquals(2, r.representatives.size)
        assertTrue(r.representatives.any { it.anchor.id == 5 })
    }

    @Test
    fun twoAperturesAmbiguityOffsetsRecessedAndMissingBoundaryPreserved() = image { gray ->
        // One parent image region with two distinct apertures: candidate boundaries are apertures.
        Imgproc.rectangle(gray, Point(45.0, 45.0), Point(135.0, 115.0), Scalar(210.0), -1)
        Imgproc.circle(gray, Point(70.0, 80.0), 8, Scalar(30.0), -1)
        Imgproc.circle(gray, Point(110.0, 80.0), 8, Scalar(30.0), -1)
        assertEquals(
            2,
            groupBCrossSectionBoundaries(
                    gray,
                    reps(o(1, 70.0, 8.0), o(2, 110.0, 8.0)),
                    BObjectPolicy.ANNULUS_90,
                )
                .representatives
                .size,
        )
        gray.setTo(Scalar(30.0))
        ring(gray, 80.0)
        // Existing third aperture crossing the band vetoes automatic object identity.
        val multiple = reps(o(1, 80.0, 8.0), o(2, 80.0, 24.0), o(3, 94.0, 7.0))
        val m = groupBCrossSectionBoundaries(gray, multiple, BObjectPolicy.ANNULUS_90)
        assertEquals(3, m.representatives.size)
        assertTrue(m.reasonCounts.containsKey("MULTIPLE_BOUNDARIES"))
        for (input in
            listOf(
                reps(o(1, 80.0, 8.0)),
                reps(o(2, 80.0, 24.0)),
                reps(o(1, 85.0, 8.0), o(2, 80.0, 24.0)),
            )) {
            assertEquals(
                input,
                groupBCrossSectionBoundaries(gray, input, BObjectPolicy.ANNULUS_90).representatives,
            )
        }
        gray.setTo(Scalar(30.0)) // nesting alone / dark recessed aperture is insufficient
        val ambiguous = reps(o(1, 80.0, 8.0), o(2, 80.0, 24.0))
        assertEquals(
            ambiguous,
            groupBCrossSectionBoundaries(gray, ambiguous, BObjectPolicy.ANNULUS_90).representatives,
        )
    }

    @Test
    fun occlusionImageRoiBorderAndObjectPairBudgetDoNotEraseCandidates() = image { gray ->
        ring(gray, 80.0)
        Imgproc.rectangle(gray, Point(55.0, 55.0), Point(80.0, 105.0), Scalar(30.0), -1)
        val input = reps(o(1, 80.0, 8.0), o(2, 80.0, 24.0))
        assertEquals(
            input,
            groupBCrossSectionBoundaries(gray, input, BObjectPolicy.ANNULUS_90).representatives,
        )
        ring(gray, 10.0)
        val clipped = reps(o(3, 10.0, 8.0), o(4, 10.0, 24.0))
        assertEquals(
            clipped,
            groupBCrossSectionBoundaries(gray, clipped, BObjectPolicy.ANNULUS_90).representatives,
        )
        val e =
            assertThrows(DetectionFailure::class.java) {
                groupBCrossSectionBoundaries(
                    gray,
                    input,
                    BObjectPolicy.ANNULUS_90,
                    maxPairChecks = 0,
                )
            }
        assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, e.reason)
        assertTrue(e.cause!!.message!!.contains("object"))
    }
}
