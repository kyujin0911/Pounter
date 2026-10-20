package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import org.opencv.core.*

/** Evaluation only. Dense, polarity-symmetric paths between independently supported boundaries. */
internal fun selectBContinuousFaces(
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
    var checks = 0
    fun spend() {
        checkActive()
        if (checks >= maxPairChecks) bPairBudgetExceeded("continuous-face")
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
                        bContinuousImage(
                            pixels,
                            gray.cols(),
                            gray.rows(),
                            inner,
                            outer,
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

private fun bContinuousImage(
    pixels: ByteArray,
    width: Int,
    height: Int,
    inner: EllipseGeometry,
    outer: EllipseGeometry,
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
    var continuous = 0
    var innerPositive = 0
    var outerPositive = 0
    var innerNegative = 0
    var outerNegative = 0
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
        continuous++
        if (first >= inside + 12) innerPositive++
        if (last >= outside + 12) outerPositive++
        if (first <= inside - 12) innerNegative++
        if (last <= outside - 12) outerNegative++
    }
    // Full denominator72, no clamping/renormalization of missing image samples.
    return continuous >= 47 &&
        (innerPositive >= 47 || innerNegative >= 47) &&
        outerPositive + outerNegative >= 24
}
