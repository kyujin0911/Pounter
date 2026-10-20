package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Evaluation only: associate fits borrowing the same actual edge pixels, not nearby centers alone.
 */
internal fun selectBSharedEdges(
    gray: Mat,
    input: List<BRepresentative>,
    maxPairChecks: Int = 1_000_000,
    checkActive: () -> Unit = {},
): List<BRepresentative> {
    require(gray.type() == CvType.CV_8UC1 && maxPairChecks >= 0)
    checkActive()
    val sorted = input.sortedWith { a, b -> bObservationOrder.compare(a.anchor, b.anchor) }
    val eligibleIds = mutableSetOf<Int>()
    var gateChecks = 0
    prefilter@ for (i in sorted.indices) {
        checkActive()
        for (j in 0 until i) {
            if (gateChecks >= maxPairChecks) {
                // Work-saving only: exhaustion falls back to the original full calculation.
                // The actual fixed-anchor merge below still owns the failure budget.
                eligibleIds += sorted.map { it.anchor.id }
                break@prefilter
            }
            gateChecks++
            if (bSharedGeometryEligible(sorted[i].anchor.geometry, sorted[j].anchor.geometry)) {
                eligibleIds += sorted[i].anchor.id
                eligibleIds += sorted[j].anchor.id
            }
        }
    }
    if (eligibleIds.isEmpty()) return sorted
    val blur = Mat()
    val edge = Mat()
    val gx = Mat()
    val gy = Mat()
    try {
        Imgproc.medianBlur(gray, blur, 3)
        Imgproc.Canny(blur, edge, 40.0, 90.0)
        Imgproc.Sobel(blur, gx, CvType.CV_64F, 1, 0, 3)
        Imgproc.Sobel(blur, gy, CvType.CV_64F, 0, 1, 3)
        val width = gray.cols()
        val height = gray.rows()
        val edges = ByteArray(width * height)
        val dx = DoubleArray(width * height)
        val dy = DoubleArray(width * height)
        edge.get(0, 0, edges)
        gx.get(0, 0, dx)
        gy.get(0, 0, dy)
        val provenance = mutableMapOf<Int, Set<Int>>()
        // Stable original anchor order; merged members never become new geometric anchors.
        for (r in sorted) {
            checkActive()
            if (r.anchor.id !in eligibleIds) continue
            val g = r.anchor.geometry
            val ca = cos(g.angleRadians)
            val sa = sin(g.angleRadians)
            val pixels = mutableSetOf<Int>()
            val left = max(0, floor(g.centerXPx - g.semiMajorPx - 3).toInt())
            val right = min(width, ceil(g.centerXPx + g.semiMajorPx + 4).toInt())
            val top = max(0, floor(g.centerYPx - g.semiMajorPx - 3).toInt())
            val bottom = min(height, ceil(g.centerYPx + g.semiMajorPx + 4).toInt())
            for (y in top until bottom) {
                checkActive()
                for (x in left until right) {
                    val i = y * width + x
                    if (edges[i].toInt() == 0) continue
                    val px = x - g.centerXPx
                    val py = y - g.centerYPx
                    val u = px * ca + py * sa
                    val v = -px * sa + py * ca
                    if (bRadialBoundaryDistancePx(u, v, g.semiMajorPx, g.semiMinorPx) > 2) continue
                    val nu = u / (g.semiMajorPx * g.semiMajorPx)
                    val nv = v / (g.semiMinorPx * g.semiMinorPx)
                    val nx = nu * ca - nv * sa
                    val ny = nu * sa + nv * ca
                    val magnitude = hypot(dx[i], dy[i])
                    if (
                        magnitude > 0 &&
                            abs(dx[i] * nx + dy[i] * ny) >= cos(PI / 6) * magnitude * hypot(nx, ny)
                    )
                        pixels += i
                }
            }
            provenance[r.anchor.id] = pixels
        }
        val result = mutableListOf<BRepresentative>()
        var checks = 0
        for (r in sorted) {
            checkActive()
            val g = r.anchor.geometry
            val s = provenance[r.anchor.id].orEmpty()
            val index =
                result.indexOfFirst { anchor ->
                    if (checks >= maxPairChecks) bPairBudgetExceeded("shared-edge")
                    checks++
                    val h = anchor.anchor.geometry
                    val t = provenance[anchor.anchor.id].orEmpty()
                    bSharedGeometryEligible(g, h) &&
                        s.isNotEmpty() &&
                        t.isNotEmpty() &&
                        s.count { it in t } >= .5 * min(s.size, t.size)
                }
            if (index < 0) result += r
            else
                result[index] =
                    result[index].copy(
                        sourceIds = (result[index].sourceIds + r.sourceIds).distinct().sorted()
                    )
        }
        return result
    } finally {
        gy.release()
        gx.release()
        edge.release()
        blur.release()
    }
}

/** Distance along the center-to-pixel ray to the ellipse; not a shortest-distance approximation. */
internal fun bRadialBoundaryDistancePx(u: Double, v: Double, a: Double, b: Double): Double {
    val rho = hypot(u / a, v / b)
    return if (rho > 0) abs(rho - 1) * hypot(u, v) / rho else Double.POSITIVE_INFINITY
}

private fun bSharedGeometryEligible(g: EllipseGeometry, h: EllipseGeometry): Boolean =
    hypot(g.centerXPx - h.centerXPx, g.centerYPx - h.centerYPx) <=
        .5 * min(g.semiMinorPx, h.semiMinorPx) &&
        min(g.semiMajorPx, h.semiMajorPx) / max(g.semiMajorPx, h.semiMajorPx) >= .6 &&
        min(g.semiMinorPx, h.semiMinorPx) / max(g.semiMinorPx, h.semiMinorPx) >= .6
