package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class DetectionDiagnosticsInstrumentedTest {
    private val config = DetectionConfig.synthetic()

    @Test
    fun traceDoesNotChangeGeometry() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 400, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.ellipse(
                gray,
                Point(110.0, 110.0),
                Size(38.0, 28.0),
                0.0,
                0.0,
                360.0,
                Scalar(210.0),
                4,
            )
            val plain = CandidateExtractor.extract(gray, config)
            val trace = DetectionDiagnostics(2, 5)
            val traced = CandidateExtractor.extract(gray, config, diagnostics = trace)
            assertEquals(plain, traced)
            val snapshot = trace.snapshot()
            assertTrue(snapshot.truncated)
            assertTrue(snapshot.entries.size <= 2)
            assertTrue(snapshot.entries.sumOf { it.points.size } <= 5)
            assertEquals(traced.size, snapshot.totals.getValue("quality"))
            assertTrue(snapshot.totals.getValue("raw") >= snapshot.totals.getValue("fit"))
        } finally {
            gray.release()
        }
    }

    @Test
    fun traceBudgetDoesNotTruncateDetections() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 400, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.circle(gray, Point(100.0, 100.0), 30, Scalar(210.0), 4)
            val plain = CandidateExtractor.extract(gray, config)
            val trace = DetectionDiagnostics(0, 0)
            assertEquals(plain, CandidateExtractor.extract(gray, config, diagnostics = trace))
            assertTrue(trace.snapshot().entries.isEmpty())
            assertEquals(plain.size, trace.snapshot().totals.getValue("quality"))
        } finally {
            gray.release()
        }
    }

    @Test
    fun failureKeepsStageTotals() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 400, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.circle(gray, Point(100.0, 100.0), 30, Scalar(210.0), 4)
            val trace = DetectionDiagnostics(1, 2)
            try {
                CandidateExtractor.extract(
                    gray,
                    config.copy(maxCandidates = 1),
                    diagnostics = trace,
                )
                fail("saturation")
            } catch (failure: DetectionFailure) {
                assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, failure.reason)
            }
            assertTrue(trace.snapshot().totals.getValue("raw") > 0)
            assertEquals("CANDIDATE_SATURATION", trace.snapshot().status)
        } finally {
            gray.release()
        }
    }

    @Test
    fun rejectedGeometryKeepsCanonicalMajorAxisDirection() {
        val geometry = observationGeometry(RotatedRect(Point(50.0, 50.0), Size(40.0, 100.0), 0.0))
        assertEquals(50.0, geometry.aPx, 0.0)
        assertEquals(20.0, geometry.bPx, 0.0)
        assertEquals(kotlin.math.PI / 2, geometry.angleRadians, 1e-12)
    }

    @Test
    fun equalCandidatesKeepBothObservationLinks() {
        val candidate =
            EllipseCandidate(
                50.0,
                50.0,
                20.0,
                20.0,
                0.0,
                setOf(CandidateSource.REGION),
                1.0,
                0.0,
                0.0,
            )
        val trace = DetectionDiagnostics(2, 0)
        trace.reset()
        for (index in 0..1) {
            val id = trace.raw("region", emptyList())
            trace.observe(id, "quality", "ACCEPTED", acceptedIndex = index)
        }
        val merged =
            mergeWithDiagnostics(listOf(candidate, candidate.copy()), config.mergePolicy, trace)
        assertEquals(1, merged.size)
        assertEquals(listOf(0, 0), trace.snapshot().entries.map { it.representativeIndex })
    }

    @Test
    fun nextRunStartsWithEmptyTrace() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 400, CvType.CV_8UC1, Scalar(30.0))
        try {
            val trace = DetectionDiagnostics(1, 2)
            Imgproc.circle(gray, Point(100.0, 100.0), 30, Scalar(210.0), 4)
            CandidateExtractor.extract(gray, config, diagnostics = trace)
            gray.setTo(Scalar(30.0))
            CandidateExtractor.extract(gray, config, diagnostics = trace)
            assertEquals(0, trace.snapshot().totals.getValue("quality"))
            // Uniform binary threshold still has the image-frame contour, rejected by geometry.
            assertTrue(trace.snapshot().entries.all { it.acceptedIndex == null })
            assertEquals(0, trace.snapshot().entries.first().observationId)
            assertEquals(listOf(0.0, 0.0, 399.0, 239.0), trace.snapshot().entries.first().bbox)
            assertEquals("SUCCESS", trace.snapshot().status)
        } finally {
            gray.release()
        }
    }
}
