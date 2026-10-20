package io.github.kyujin0911.pounter

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/** Task 3 standard API probe only; no alternative native distribution is packaged. */
class CandidateGenerationInstrumentedTest {
    @Test
    fun standardClosestPointsAgreeWithCircleDistance() {
        assertTrue(OpenCVLoader.initLocal())
        val input =
            MatOfPoint2f(
                *Array(64) { i ->
                    Point(51 + 20 * cos(i * 2 * PI / 64), 50 + 20 * sin(i * 2 * PI / 64))
                }
            )
        val output = MatOfPoint2f()
        try {
            Imgproc.getClosestEllipsePoints(
                RotatedRect(Point(50.0, 50.0), Size(40.0, 40.0), 0.0),
                input,
                output,
            )
            input.toArray().zip(output.toArray()).forEach { (p, q) ->
                assertEquals(abs(hypot(p.x - 50, p.y - 50) - 20), hypot(p.x - q.x, p.y - q.y), 1e-4)
            }
        } finally {
            input.release()
            output.release()
        }
    }

    @Test
    fun rotatedEllipseAgreesWithDenseReferenceAndAxisSwap() {
        assertTrue(OpenCVLoader.initLocal())
        val input = MatOfPoint2f(Point(90.0, 90.0), Point(100.0, 115.0), Point(135.0, 130.0))
        val output = MatOfPoint2f()
        val swapped = MatOfPoint2f()
        try {
            val ellipse = RotatedRect(Point(100.0, 100.0), Size(60.0, 30.0), 25.0)
            Imgproc.getClosestEllipsePoints(ellipse, input, output)
            Imgproc.getClosestEllipsePoints(
                RotatedRect(ellipse.center, Size(30.0, 60.0), 115.0),
                input,
                swapped,
            )
            input.toArray().zip(output.toArray()).forEach { (p, q) ->
                // Exhaustive reference sample, not a custom distance solver.
                val reference =
                    (0 until 36000).minOf { i ->
                        val t = i * 2 * PI / 36000
                        val angle = 25 * PI / 180
                        val x = 100 + 30 * cos(t) * cos(angle) - 15 * sin(t) * sin(angle)
                        val y = 100 + 30 * cos(t) * sin(angle) + 15 * sin(t) * cos(angle)
                        hypot(p.x - x, p.y - y)
                    }
                assertEquals(reference, hypot(p.x - q.x, p.y - q.y), 1e-3)
            }
            output.toArray().zip(swapped.toArray()).forEach { (a, b) ->
                assertTrue(hypot(a.x - b.x, a.y - b.y) < 1e-3)
            }
        } finally {
            input.release()
            output.release()
            swapped.release()
        }
    }
}
