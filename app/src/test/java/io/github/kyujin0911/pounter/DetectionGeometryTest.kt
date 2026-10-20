package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import org.junit.Assert.*
import org.junit.Test

class DetectionGeometryTest {
    @Test
    fun geometryOnlyRestoresFloatAndIndependentAxisRadius() {
        // Literal inverse-scale fixture: center(50/.5+100,40/.25+80)=(200,240),
        // rotated semi axes20/.25=80 and10/.5=20; short radius20/1000.
        val g = EllipseGeometry(50.0, 40.0, 20.0, 10.0, Math.PI / 2)
        val t = AnalysisTransform(1000, 800, 100, 80, .5, .25)
        val m = toMarker(g, t, "geometry")
        assertEquals(.2f, m.x, 1e-7f)
        assertEquals(.3f, m.y, 1e-7f)
        assertEquals(.02f, m.radius, 1e-7f)
        val old =
            toMarker(
                candidate(50.0, 40.0)
                    .copy(semiMajorPx = 20.0, semiMinorPx = 10.0, angleRadians = Math.PI / 2),
                t,
                "geometry",
            )
        assertEquals(old, m)
    }

    @Test
    fun geometryOnlyRejectsInvalidWithoutFabricatedQuality() {
        for (axes in
            listOf(0.0 to 0.0, 5.0 to 10.0, Double.NaN to 2.0, Double.POSITIVE_INFINITY to 2.0)) {
            assertThrows(IllegalArgumentException::class.java) {
                EllipseGeometry(1.0, 2.0, axes.first, axes.second, 0.0)
            }
        }
    }

    @Test
    fun cropAndScaleRestoreCenter() {
        val m = toMarker(candidate(50.0, 40.0), AnalysisTransform(1000, 800, 100, 80, .5, .5), "m")
        assertEquals(.2f, m.x, .0001f)
        assertEquals(.2f, m.y, .0001f)
    }

    @Test
    fun rotatedEllipseRestoresNonUniformRadius() {
        val c =
            candidate(50.0, 40.0)
                .copy(semiMajorPx = 20.0, semiMinorPx = 10.0, angleRadians = Math.PI / 2)
        val m = toMarker(c, AnalysisTransform(1000, 800, 100, 80, .5, .25), "m")
        assertEquals(.02f, m.radius, .0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidGeometryIsRejected() {
        candidate(Double.NaN, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAxesRejected() {
        candidate(0.0, 0.0).copy(semiMinorPx = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidScaleRejected() {
        AnalysisTransform(100, 80, 0, 0, 0.0, 1.0)
    }
}

internal fun candidate(x: Double, y: Double, r: Double = 20.0) =
    EllipseCandidate(x, y, r, r, 0.0, setOf(CandidateSource.CONTOUR), .9, .01, 40.0)
