package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

internal fun selectBRadialObjects(
    gray: Mat,
    input: List<BRepresentative>,
    entryBudget: Int = 25000,
    maxPairChecks: Int = 1_000_000,
    checkActive: () -> Unit = {},
    localBoundaries: Boolean = false,
): BRadialRun =
    selectBBoundaryObjects(
        gray,
        input,
        entryBudget,
        maxPairChecks,
        checkActive,
        localBoundaries,
        ::radialSeeds,
    )

/** Signed gradient voting; max absolute scale response. Not the reference implementation's mean. */
internal fun radialSeeds(gray: Mat, spend: () -> Unit, checkActive: () -> Unit): List<RadialSeed> {
    val width = gray.cols()
    val height = gray.rows()
    val size = width * height
    val blur = Mat()
    val gx = Mat()
    val gy = Mat()
    val work = Mat(height, width, CvType.CV_32FC1)
    val smooth = Mat()
    val maxima = Mat()
    val kernel = Mat.ones(3, 3, CvType.CV_8UC1)
    try {
        Imgproc.medianBlur(gray, blur, 3)
        Imgproc.Sobel(blur, gx, CvType.CV_32F, 1, 0, 3, 1.0 / 8)
        Imgproc.Sobel(blur, gy, CvType.CV_32F, 0, 1, 3, 1.0 / 8)
        val dx = FloatArray(size)
        val dy = FloatArray(size)
        gx.get(0, 0, dx)
        gy.get(0, 0, dy)
        val magnitude = FloatArray(size) { hypot(dx[it].toDouble(), dy[it].toDouble()).toFloat() }
        val active = IntArray(size)
        var activeCount = 0
        for (i in 0 until size) if (magnitude[i] >= 4) {
            active[activeCount++] = i
            dx[i] /= magnitude[i]
            dy[i] /= magnitude[i]
        }
        val best = FloatArray(size)
        val signed = FloatArray(size)
        val radiusAt = FloatArray(size)
        val orientation = IntArray(size)
        val weights = DoubleArray(size)
        val values = FloatArray(size)
        for (radius in listOf(4, 6, 8, 12, 16, 24, 32, 48, 64, 96)) {
            checkActive()
            orientation.fill(0)
            weights.fill(0.0)
            for (k in 0 until activeCount) {
                val i = active[k]
                val x = i % width
                val y = i / width
                for (signIndex in 0..1) {
                    val sign = if (signIndex == 0) 1 else -1
                    val xx = x + floor((sign * radius * dx[i]).toDouble() + .5).toInt()
                    val yy = y + floor((sign * radius * dy[i]).toDouble() + .5).toInt()
                    if (xx !in 0 until width || yy !in 0 until height) continue
                    val index = yy * width + xx
                    orientation[index] += sign
                    weights[index] += sign * magnitude[i].toDouble()
                }
            }
            for (i in 0 until size) {
                val o = min(abs(orientation[i]).toFloat(), 9.9f) / 9.9f
                values[i] = (weights[i].toFloat() / 9.9f) * o * o
            }
            work.put(0, 0, values)
            Imgproc.GaussianBlur(
                work,
                smooth,
                Size(0.0, 0.0),
                .25 * radius,
                0.0,
                Core.BORDER_CONSTANT,
            )
            smooth.get(0, 0, values)
            for (i in 0 until size) {
                val v = values[i] * radius
                if (abs(v) > best[i]) {
                    best[i] = abs(v)
                    signed[i] = v
                    radiusAt[i] = radius.toFloat()
                }
            }
        }
        work.put(0, 0, best)
        Imgproc.dilate(work, maxima, kernel)
        maxima.get(0, 0, values)
        val peaks =
            (0 until size)
                .filter { best[it] >= 20 && best[it] == values[it] }
                .sortedWith(
                    compareByDescending<Int> { best[it] }
                        .thenBy { it % width }
                        .thenBy { it / width }
                )
        val output = mutableListOf<RadialSeed>()
        // ponytail: bounded O(n²) peak suppression; budget failure is explicit, never a partial
        // count.
        for (i in peaks) {
            checkActive()
            val x = i % width
            val y = i / width
            val r = radiusAt[i]
            if (
                output.any { s ->
                    spend()
                    hypot((x - s.x).toDouble(), (y - s.y).toDouble()) <=
                        max(2.0, .2 * min(r, s.radius))
                }
            )
                continue
            output += RadialSeed(x, y, r, best[i], signed[i] < 0)
            if (output.size > 10000) bPairBudgetExceeded("radial-seeds")
        }
        return output.sortedWith(
            compareBy<RadialSeed> { it.x }.thenBy { it.y }.thenBy { it.radius }
        )
    } finally {
        kernel.release()
        maxima.release()
        smooth.release()
        work.release()
        gy.release()
        gx.release()
        blur.release()
    }
}
