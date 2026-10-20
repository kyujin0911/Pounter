package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import org.opencv.core.*

/** Experimental pair evidence only; neither policy rejects individual candidates. */
internal enum class BObjectPolicy(val coherentSpokes: Int) {
    ANNULUS_ALL(72),
    ANNULUS_90(65),
}

internal data class BObjectDecision(
    val anchorId: Int,
    val candidateId: Int,
    val reason: String,
    val coherentSpokes: Int = 0,
    val exteriorSpokes: Int = 0,
    val invalidSpokes: Int = 0,
)

internal data class BObjectRun(
    val representatives: List<BRepresentative>,
    val inputCount: Int,
    val mergedCount: Int,
    val pairChecks: Int,
    val reasonCounts: Map<String, Int>,
    val decisions: List<BObjectDecision>,
    val truncated: Boolean,
    val elapsedNanos: Long,
)

/** Fixed anchors, direct relations only. No new contour, geometry, or hierarchy is generated. */
internal fun groupBCrossSectionBoundaries(
    gray: Mat,
    input: List<BRepresentative>,
    policy: BObjectPolicy,
    entryBudget: Int = 25000,
    maxPairChecks: Int = 1_000_000,
): BObjectRun {
    require(gray.type() == CvType.CV_8UC1 && entryBudget >= 0 && maxPairChecks >= 0)
    val started = System.nanoTime()
    val pixels = ByteArray(gray.rows() * gray.cols())
    gray.get(0, 0, pixels)
    val sorted = input.sortedWith { a, b -> bObservationOrder.compare(a.anchor, b.anchor) }
    val representatives = mutableListOf<BRepresentative>()
    val decisions = mutableListOf<BObjectDecision>()
    val reasons = linkedMapOf<String, Int>()
    var pairChecks = 0
    var decisionCount = 0
    fun spend() {
        if (pairChecks >= maxPairChecks) bPairBudgetExceeded("object")
        pairChecks++
    }
    for (candidate in sorted) {
        var merged = false
        for (index in representatives.indices) {
            val anchor = representatives[index]
            spend()
            val a = anchor.anchor.geometry
            val b = candidate.anchor.geometry
            val (inner, outer) =
                if (a.semiMajorPx * a.semiMinorPx < b.semiMajorPx * b.semiMinorPx) a to b
                else b to a
            var reason = "GEOMETRY_DISTINCT_OR_UNRESOLVED"
            var coherent = 0
            var exterior = 0
            var invalid = 0
            if (bNestedBoundaries(inner, outer)) {
                // Other existing apertures/boundaries veto identity; never infer object from
                // parent.
                val competing =
                    sorted.any { third ->
                        if (
                            third.anchor.id == anchor.anchor.id ||
                                third.anchor.id == candidate.anchor.id
                        )
                            false
                        else {
                            spend()
                            bEllipseRadius(
                                outer,
                                third.anchor.geometry.centerXPx,
                                third.anchor.geometry.centerYPx,
                            ) <= 1.0
                        }
                    }
                if (competing) reason = "MULTIPLE_BOUNDARIES"
                else {
                    for (n in 0 until 72) {
                        val t = n * 2 * PI / 72
                        val p = bBoundaryPoint(inner, t)
                        val q = bBoundaryPoint(outer, t)
                        val positions =
                            listOf(
                                bBoundaryPoint(inner, t, .75),
                                p.first + .2 * (q.first - p.first) to
                                    p.second + .2 * (q.second - p.second),
                                p.first + .5 * (q.first - p.first) to
                                    p.second + .5 * (q.second - p.second),
                                p.first + .8 * (q.first - p.first) to
                                    p.second + .8 * (q.second - p.second),
                                bBoundaryPoint(outer, t, 1.08),
                            )
                        val values =
                            positions.map { (x, y) ->
                                val ix = floor(x + .5).toInt()
                                val iy = floor(y + .5).toInt()
                                if (ix !in 0 until gray.cols() || iy !in 0 until gray.rows()) null
                                else pixels[iy * gray.cols() + ix].toInt() and 255
                            }
                        if (values.any { it == null }) {
                            invalid++
                            continue
                        }
                        val band = values.subList(1, 4).map { it!! }
                        val low = band.min()
                        val high = band.max()
                        if (low >= values[0]!! + 12 && high - low <= 24) coherent++
                        if (low >= values[4]!! + 12) exterior++
                    }
                    reason =
                        if (invalid > 0) "IMAGE_OR_ROI_BOUNDARY"
                        else if (coherent < policy.coherentSpokes || exterior < 47)
                            "IMAGE_RELATION_UNRESOLVED"
                        else "ANNULAR_IMAGE_RELATION"
                }
            }
            reasons[reason] = (reasons[reason] ?: 0) + 1
            decisionCount++
            if (decisions.size < entryBudget)
                decisions +=
                    BObjectDecision(
                        anchor.anchor.id,
                        candidate.anchor.id,
                        reason,
                        coherent,
                        exterior,
                        invalid,
                    )
            if (reason == "ANNULAR_IMAGE_RELATION") {
                representatives[index] =
                    anchor.copy(sourceIds = (anchor.sourceIds + candidate.sourceIds).sorted())
                merged = true
                break
            }
        }
        if (!merged) representatives += candidate
    }
    return BObjectRun(
        representatives,
        input.size,
        input.size - representatives.size,
        pairChecks,
        reasons,
        decisions,
        decisionCount > decisions.size,
        System.nanoTime() - started,
    )
}

private fun bBoundaryPoint(
    g: EllipseGeometry,
    t: Double,
    scale: Double = 1.0,
): Pair<Double, Double> {
    val u = g.semiMajorPx * cos(t) * scale
    val v = g.semiMinorPx * sin(t) * scale
    return g.centerXPx + u * cos(g.angleRadians) - v * sin(g.angleRadians) to
        g.centerYPx + u * sin(g.angleRadians) + v * cos(g.angleRadians)
}

private fun bEllipseRadius(g: EllipseGeometry, x: Double, y: Double): Double {
    val dx = x - g.centerXPx
    val dy = y - g.centerYPx
    val c = cos(g.angleRadians)
    val s = sin(g.angleRadians)
    return hypot((dx * c + dy * s) / g.semiMajorPx, (-dx * s + dy * c) / g.semiMinorPx)
}

private fun bNestedBoundaries(inner: EllipseGeometry, outer: EllipseGeometry): Boolean {
    if (
        hypot(inner.centerXPx - outer.centerXPx, inner.centerYPx - outer.centerYPx) >
            .2 * inner.semiMinorPx
    )
        return false
    if (
        inner.semiMajorPx / outer.semiMajorPx !in 0.25..0.8 ||
            inner.semiMinorPx / outer.semiMinorPx !in 0.25..0.8
    )
        return false
    return (0 until 72).all { n ->
        val p = bBoundaryPoint(inner, n * 2 * PI / 72)
        bEllipseRadius(outer, p.first, p.second) <= 1.01
    }
}
