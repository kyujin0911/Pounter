package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/** Evaluation only. Fixed-denominator paths on independently observed outer edge sectors. */
internal fun selectBSupportedFaces(
    gray: Mat,
    input: List<BRepresentative>,
    maxPairChecks: Int = 1_000_000,
    checkActive: () -> Unit = {},
): List<BRepresentative> {
    require(gray.type() == CvType.CV_8UC1 && maxPairChecks >= 0)
    checkActive()
    val sorted = input.sortedWith { a, b -> bObservationOrder.compare(a.anchor, b.anchor) }
    val pixels = ByteArray(gray.rows() * gray.cols())
    gray.get(0, 0, pixels)
    val fields by lazy { bSupportedFields(gray) }
    val sectors = mutableMapOf<Int, BooleanArray>()
    var checks = 0
    fun spend() {
        checkActive()
        if (checks >= maxPairChecks) bPairBudgetExceeded("supported-face")
        checks++
    }
    val output = mutableListOf<BRepresentative>()
    for (candidate in sorted) {
        checkActive()
        val index =
            output.indexOfFirst { anchor ->
                spend()
                val a = anchor.anchor.geometry
                val b = candidate.anchor.geometry
                val (inner, outer) =
                    if (a.semiMajorPx * a.semiMinorPx < b.semiMajorPx * b.semiMinorPx) a to b
                    else b to a
                if (!bContinuousGeometry(inner, outer)) false
                else {
                    // An independent aperture center vetoes ownership; no parent self-evidence.
                    val competing =
                        sorted.any { third ->
                            if (
                                third.anchor.id == anchor.anchor.id ||
                                    third.anchor.id == candidate.anchor.id
                            )
                                false
                            else {
                                spend()
                                val g = third.anchor.geometry
                                bContinuousRadius(outer, g.centerXPx, g.centerYPx) < 0.9 &&
                                    hypot(
                                        g.centerXPx - inner.centerXPx,
                                        g.centerYPx - inner.centerYPx,
                                    ) > 0.7 * min(g.semiMinorPx, inner.semiMinorPx)
                            }
                        }
                    !competing &&
                        bSupportedImage(
                            pixels,
                            gray.cols(),
                            gray.rows(),
                            inner,
                            outer,
                            sectors.getOrPut(
                                if (inner === a) candidate.anchor.id else anchor.anchor.id
                            ) {
                                bSupportedSectors(
                                    fields,
                                    gray.cols(),
                                    gray.rows(),
                                    outer,
                                    checkActive,
                                )
                            },
                            checkActive,
                        )
                }
            }
        if (index < 0) output += candidate
        else
            output[index] =
                output[index].copy(
                    sourceIds = (output[index].sourceIds + candidate.sourceIds).distinct().sorted()
                )
    }
    return output
}

private data class BSupportedFields(val edges: ByteArray, val dx: DoubleArray, val dy: DoubleArray)

private fun bSupportedFields(gray: Mat): BSupportedFields {
    val blur = Mat()
    val edge = Mat()
    val gx = Mat()
    val gy = Mat()
    try {
        Imgproc.medianBlur(gray, blur, 3)
        Imgproc.Canny(blur, edge, 40.0, 90.0)
        Imgproc.Sobel(blur, gx, CvType.CV_64F, 1, 0, 3)
        Imgproc.Sobel(blur, gy, CvType.CV_64F, 0, 1, 3)
        val n = gray.rows() * gray.cols()
        val e = ByteArray(n)
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        edge.get(0, 0, e)
        gx.get(0, 0, x)
        gy.get(0, 0, y)
        return BSupportedFields(e, x, y)
    } finally {
        gy.release()
        gx.release()
        edge.release()
        blur.release()
    }
}

private fun bSupportedSectors(
    f: BSupportedFields,
    width: Int,
    height: Int,
    g: EllipseGeometry,
    checkActive: () -> Unit,
): BooleanArray {
    val sectors = BooleanArray(72)
    val ca = cos(g.angleRadians)
    val sa = sin(g.angleRadians)
    val left = max(0, floor(g.centerXPx - g.semiMajorPx - 3).toInt())
    val right = min(width, ceil(g.centerXPx + g.semiMajorPx + 4).toInt())
    val top = max(0, floor(g.centerYPx - g.semiMajorPx - 3).toInt())
    val bottom = min(height, ceil(g.centerYPx + g.semiMajorPx + 4).toInt())
    for (y in top until bottom) {
        checkActive()
        for (x in left until right) {
            val i = y * width + x
            if (f.edges[i].toInt() == 0) continue
            val dx = x - g.centerXPx
            val dy = y - g.centerYPx
            val u = dx * ca + dy * sa
            val v = -dx * sa + dy * ca
            if (bRadialBoundaryDistancePx(u, v, g.semiMajorPx, g.semiMinorPx) > 2) continue
            val nu = u / (g.semiMajorPx * g.semiMajorPx)
            val nv = v / (g.semiMinorPx * g.semiMinorPx)
            val nx = nu * ca - nv * sa
            val ny = nu * sa + nv * ca
            val magnitude = hypot(f.dx[i], f.dy[i])
            if (
                magnitude <= 0 ||
                    abs(f.dx[i] * nx + f.dy[i] * ny) < cos(PI / 6) * magnitude * hypot(nx, ny)
            )
                continue
            val angle = (atan2(dy, dx) + 2 * PI) % (2 * PI)
            sectors[floor(angle * 72 / (2 * PI) + .5).toInt() % 72] = true
        }
    }
    return sectors
}

/**
 * Missing sectors stay false in the fixed 72-sector denominator. No success-based renormalization.
 */
internal fun bSupportedCoverage(good: BooleanArray, contradictory: Int): Boolean {
    require(good.size == 72 && contradictory >= 0)
    return good.count { it } >= 24 &&
        contradictory <= 24 &&
        (0 until 4).count { q -> (q * 18 until q * 18 + 18).count { good[it] } >= 3 } >= 3
}

private fun bSupportedImage(
    pixels: ByteArray,
    width: Int,
    height: Int,
    inner: EllipseGeometry,
    outer: EllipseGeometry,
    support: BooleanArray,
    checkActive: () -> Unit,
): Boolean {
    val p = List(72) { bContinuousPoint(inner, it) }
    val q = List(72) { bContinuousPoint(outer, it) }
    // At most one actual pixel between samples, independent of ellipse aspect ratio.
    val steps =
        max(
            3,
            ceil(
                    (0 until 72).maxOf {
                        hypot(q[it].first - p[it].first, q[it].second - p[it].second)
                    } * 0.7
                )
                .toInt() + 1,
        )
    fun sample(x: Double, y: Double): Int? {
        val ix = floor(x + 0.5).toInt()
        val iy = floor(y + 0.5).toInt()
        return if (ix in 0 until width && iy in 0 until height)
            pixels[iy * width + ix].toInt() and 255
        else null
    }
    val continuous = BooleanArray(72)
    val innerPositive = BooleanArray(72)
    val innerNegative = BooleanArray(72)
    val exterior = BooleanArray(72)
    for (n in 0 until 72) {
        checkActive()
        val inside =
            bContinuousPoint(inner, n, 0.75).let { sample(it.first, it.second) } ?: continue
        val outside =
            bContinuousPoint(outer, n, 1.08).let { sample(it.first, it.second) } ?: continue
        var first = 0
        var last = 0
        var valid = true
        var maxJump = 0
        for (j in 0 until steps) {
            val fraction = 0.15 + 0.7 * j / (steps - 1)
            val value =
                sample(
                    p[n].first + fraction * (q[n].first - p[n].first),
                    p[n].second + fraction * (q[n].second - p[n].second),
                )
            if (value == null) {
                valid = false
                break
            }
            if (j == 0) first = value else maxJump = max(maxJump, abs(value - last))
            last = value
        }
        if (!valid || maxJump > 12) continue
        continuous[n] = true
        innerPositive[n] = first >= inside + 12
        innerNegative[n] = first <= inside - 12
        exterior[n] = abs(last - outside) >= 12
    }
    val inner =
        if (innerPositive.count { it } >= innerNegative.count { it }) innerPositive
        else innerNegative
    val good = BooleanArray(72) { support[it] && continuous[it] && inner[it] && exterior[it] }
    val contradictory = (0 until 72).count { support[it] && !continuous[it] }
    return bSupportedCoverage(good, contradictory)
}
