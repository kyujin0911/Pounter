package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

internal data class BRadialDecision(val reason: String, val sourceIds: List<Int>)

internal data class BRadialRun(
    val representatives: List<BRepresentative>,
    val seedCount: Int,
    val pairChecks: Int,
    val decisions: List<BRadialDecision>,
    val truncated: Boolean,
    val elapsedNanos: Long,
)

internal data class RadialSeed(
    val x: Int,
    val y: Int,
    val radius: Float,
    val response: Float,
    val dark: Boolean,
)

/** Shared boundary/parent computation. No sample identity, truth or target count. */
internal fun selectBBoundaryObjects(
    gray: Mat,
    input: List<BRepresentative>,
    entryBudget: Int = 25000,
    maxPairChecks: Int = 1_000_000,
    checkActive: () -> Unit = {},
    localBoundaries: Boolean,
    makeSeeds: (Mat, () -> Unit, () -> Unit) -> List<RadialSeed> = { _, _, _ ->
        error("RADIAL is evaluation-only")
    },
): BRadialRun {
    require(gray.type() == CvType.CV_8UC1 && entryBudget >= 0 && maxPairChecks >= 0)
    require(input.all { it.anchor.support != null && it.anchor.support!!.isFinite() })
    val start = System.nanoTime()
    var checks = 0
    fun spend() {
        if (checks >= maxPairChecks) bPairBudgetExceeded("radial-object")
        checks++
    }
    val reps = input.sortedWith { a, b -> bObservationOrder.compare(a.anchor, b.anchor) }
    val pixels = ByteArray(gray.rows() * gray.cols())
    gray.get(0, 0, pixels)
    val groups = mutableListOf<MutableList<Int>>()
    val groupSeeds = mutableListOf<RadialSeed>()
    val seedCount: Int
    if (localBoundaries) {
        // Existing supported rims provide locations; the image predicate decides object grouping.
        // Fixed-anchor groups avoid transitive chains through touching neighbors.
        for (i in reps.indices) {
            checkActive()
            val group =
                groups.firstOrNull { members ->
                    spend()
                    radialPairImage(
                        pixels,
                        gray.cols(),
                        gray.rows(),
                        reps[members.first()].anchor.geometry,
                        reps[i].anchor.geometry,
                        centerFraction = .3,
                        maxAxisRatio = .9,
                    )
                }
            if (group == null) groups += mutableListOf(i) else group += i
        }
        for (group in groups) {
            checkActive()
            val g = group.map { reps[it].anchor.geometry }.minBy { it.semiMajorPx * it.semiMinorPx }
            var inside = 0.0
            var outside = 0.0
            var valid = true
            for (n in 0 until 72) {
                for (scale in listOf(.5, 1.3)) {
                    val p = radialPoint(g, n, scale)
                    val x = floor(p.x + .5).toInt()
                    val y = floor(p.y + .5).toInt()
                    if (x !in 0 until gray.cols() || y !in 0 until gray.rows()) {
                        valid = false
                    } else if (scale == .5) inside += pixels[y * gray.cols() + x].toInt() and 255
                    else outside += pixels[y * gray.cols() + x].toInt() and 255
                }
            }
            groupSeeds +=
                RadialSeed(
                    floor(g.centerXPx + .5).toInt(),
                    floor(g.centerYPx + .5).toInt(),
                    sqrt(g.semiMajorPx * g.semiMinorPx).toFloat(),
                    0f,
                    valid && inside < outside,
                )
        }
        seedCount = groupSeeds.size
    } else {
        val seeds = makeSeeds(gray, ::spend, checkActive)
        val claims = List(seeds.size) { mutableListOf<Int>() }
        for ((i, r) in reps.withIndex()) {
            checkActive()
            val g = r.anchor.geometry
            var nearest = -1
            var distance = Double.POSITIVE_INFINITY
            for ((j, s) in seeds.withIndex()) {
                spend()
                val d = hypot(g.centerXPx - s.x, g.centerYPx - s.y)
                if (d <= .35 * min(g.semiMinorPx, s.radius.toDouble()) && d < distance) {
                    nearest = j
                    distance = d
                }
            }
            if (nearest >= 0) claims[nearest] += i
        }
        for ((si, indices) in claims.withIndex()) {
            val local = mutableListOf<MutableList<Int>>()
            for (i in indices) {
                val group =
                    local.firstOrNull { g ->
                        spend()
                        radialPairImage(
                            pixels,
                            gray.cols(),
                            gray.rows(),
                            reps[g.first()].anchor.geometry,
                            reps[i].anchor.geometry,
                        )
                    }
                if (group == null) local += mutableListOf(i) else group += i
            }
            for (group in local) {
                groups += group
                groupSeeds += seeds[si]
            }
        }
        seedCount = seeds.size
    }
    // Lossless spatial lookup: child containment cannot use a center outside its bounding box.
    val seedColumns = groupSeeds.indices.groupBy { groupSeeds[it].x / 32 }
    val parentEvidence = mutableMapOf<Int, MutableMap<Int, MutableList<List<List<Int>>>>>()
    val blur = Mat()
    val binary = Mat()
    val hierarchy = Mat()
    try {
        Imgproc.medianBlur(gray, blur, 3)
        for (threshold in 10 until 250 step 20) {
            checkActive()
            Imgproc.threshold(blur, binary, threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
            val contours = mutableListOf<MatOfPoint>()
            try {
                Imgproc.findContours(
                    binary,
                    contours,
                    hierarchy,
                    Imgproc.RETR_TREE,
                    Imgproc.CHAIN_APPROX_SIMPLE,
                )
                if (contours.size > 10000) bPairBudgetExceeded("radial-contours")
                val hs = IntArray(contours.size * 4)
                if (hs.isNotEmpty()) hierarchy.get(0, 0, hs)
                for ((ci, contour) in contours.withIndex()) {
                    checkActive()
                    if (Imgproc.contourArea(contour) < 30) continue
                    val children = mutableListOf<Int>()
                    var childIndex = hs[ci * 4 + 2]
                    while (childIndex >= 0) {
                        children += childIndex
                        childIndex = hs[childIndex * 4]
                    }
                    children.sort()
                    if (children.size < 2) continue
                    val childGroups = linkedMapOf<Int, List<Int>>()
                    for (child in children) {
                        val area = Imgproc.contourArea(contours[child])
                        val childBounds = Imgproc.boundingRect(contours[child])
                        val possibleSeeds =
                            (childBounds.x / 32..(childBounds.x + childBounds.width) / 32)
                                .flatMap { seedColumns[it].orEmpty() }
                                .filter { i ->
                                    val seed = groupSeeds[i]
                                    seed.dark &&
                                        seed.x in
                                            childBounds.x..childBounds.x + childBounds.width &&
                                        seed.y in childBounds.y..childBounds.y + childBounds.height
                                }
                                .sorted()
                        if (possibleSeeds.isEmpty()) continue
                        val childCurve = MatOfPoint2f(*contours[child].toArray())
                        try {
                            for (si in possibleSeeds) {
                                spend()
                                val s = groupSeeds[si]
                                val seedArea = PI * s.radius * s.radius
                                if (
                                    area in .4 * seedArea..2 * seedArea &&
                                        Imgproc.pointPolygonTest(
                                            childCurve,
                                            Point(s.x.toDouble(), s.y.toDouble()),
                                            false,
                                        ) >= 0
                                ) {
                                    val supported =
                                        groups[si].filter { ri ->
                                            spend()
                                            val g = reps[ri].anchor.geometry
                                            val ellipseArea = PI * g.semiMajorPx * g.semiMinorPx
                                            abs(area - ellipseArea) <= .25 * ellipseArea &&
                                                (0 until 72).count { n ->
                                                    abs(
                                                        Imgproc.pointPolygonTest(
                                                            childCurve,
                                                            radialPoint(g, n),
                                                            true,
                                                        )
                                                    ) <= 2.5
                                                } >= 65
                                        }
                                    if (supported.isNotEmpty()) {
                                        childGroups[si] = supported
                                        break
                                    }
                                }
                            }
                        } finally {
                            childCurve.release()
                        }
                    }
                    if (childGroups.size < 2) continue
                    val childList = childGroups.keys.toList()
                    if (
                        childList.indices.any { a ->
                            (a + 1 until childList.size).any { b ->
                                groups[childList[a]].any { it in groups[childList[b]] }
                            }
                        }
                    )
                        continue
                    val bounds = Imgproc.boundingRect(contour)
                    val area = Imgproc.contourArea(contour)
                    val curve = MatOfPoint2f(*contour.toArray())
                    try {
                        for ((ri, r) in reps.withIndex()) {
                            spend()
                            val g = r.anchor.geometry
                            if (
                                g.centerXPx !in
                                    bounds.x.toDouble()..(bounds.x + bounds.width).toDouble() ||
                                    g.centerYPx !in
                                        bounds.y.toDouble()..(bounds.y + bounds.height).toDouble()
                            )
                                continue
                            val ellipseArea = PI * g.semiMajorPx * g.semiMinorPx
                            if (abs(area - ellipseArea) > .25 * ellipseArea) continue
                            var close = 0
                            for (n in 0 until 72) if (
                                abs(Imgproc.pointPolygonTest(curve, radialPoint(g, n), true)) <= 2.5
                            )
                                close++
                            val independent =
                                childGroups.values
                                    .map { members -> members.filter { it != ri } }
                                    .filter { it.isNotEmpty() }
                            if (close >= 65 && independent.size >= 2) {
                                parentEvidence
                                    .getOrPut(ri) { mutableMapOf() }
                                    .getOrPut(threshold) { mutableListOf() } += independent
                            }
                        }
                    } finally {
                        curve.release()
                    }
                }
            } finally {
                contours.forEach { it.release() }
            }
        }
    } finally {
        hierarchy.release()
        binary.release()
        blur.release()
    }
    checkActive()
    val parents = parentEvidence.filterValues { it.size >= 2 }.keys.toMutableSet()
    // Every removed parent needs two independently supported children that survive all removals.
    // Removing all unsupported proposals together is conservative in mutually dependent cases.
    val unsupported =
        parents.filter { ri ->
            parentEvidence.getValue(ri).values.count { evidenceAtThreshold ->
                evidenceAtThreshold.any { children ->
                    children.count { members -> members.any { it !in parents } } >= 2
                }
            } < 2
        }
    parents.removeAll(unsupported.toSet())
    val assigned = parents.toMutableSet()
    val output = mutableListOf<BRepresentative>()
    val decisions = mutableListOf<BRadialDecision>()
    for (group in groups) {
        val members = group.filter { it !in parents }
        if (members.isEmpty()) continue
        assigned += members
        val sources = members.flatMap { reps[it].sourceIds }.distinct().sorted()
        output += reps[members.first()].copy(sourceIds = sources)
        if (members.size > 1)
            decisions +=
                BRadialDecision(
                    if (localBoundaries) "LOCAL_SUPPORTED_BOUNDARIES"
                    else "RADIAL_CENTER_AND_IMAGE_BOUNDARIES",
                    sources,
                )
    }
    for (i in reps.indices) if (i !in assigned) output += reps[i]
    for (i in parents.sorted()) decisions +=
        BRadialDecision("OBSERVED_MULTI_APERTURE_PARENT", reps[i].sourceIds)
    val order = reps.mapIndexed { i, r -> r.anchor.id to i }.toMap()
    return BRadialRun(
        output.sortedBy { order.getValue(it.anchor.id) },
        seedCount,
        checks,
        decisions.take(entryBudget),
        decisions.size > entryBudget,
        System.nanoTime() - start,
    )
}

private fun radialPoint(g: EllipseGeometry, n: Int, scale: Double = 1.0): Point {
    val t = n * 2 * PI / 72
    val u = g.semiMajorPx * cos(t) * scale
    val v = g.semiMinorPx * sin(t) * scale
    return Point(
        g.centerXPx + u * cos(g.angleRadians) - v * sin(g.angleRadians),
        g.centerYPx + u * sin(g.angleRadians) + v * cos(g.angleRadians),
    )
}

private fun radialPairImage(
    pixels: ByteArray,
    width: Int,
    height: Int,
    a: EllipseGeometry,
    b: EllipseGeometry,
    centerFraction: Double = .2,
    maxAxisRatio: Double = .8,
): Boolean {
    val (inner, outer) =
        if (a.semiMajorPx * a.semiMinorPx < b.semiMajorPx * b.semiMinorPx) a to b else b to a
    if (
        hypot(inner.centerXPx - outer.centerXPx, inner.centerYPx - outer.centerYPx) >
            centerFraction * inner.semiMinorPx ||
            inner.semiMajorPx / outer.semiMajorPx !in 0.25..maxAxisRatio ||
            inner.semiMinorPx / outer.semiMinorPx !in 0.25..maxAxisRatio
    )
        return false
    var positive = 0
    var negative = 0
    var exteriorPositive = 0
    var exteriorNegative = 0
    var filledPositive = 0
    var filledNegative = 0
    for (n in 0 until 72) {
        val p = radialPoint(inner, n)
        val q = radialPoint(outer, n)
        val positions =
            listOf(
                radialPoint(inner, n, .65),
                Point(p.x + .2 * (q.x - p.x), p.y + .2 * (q.y - p.y)),
                Point(p.x + .5 * (q.x - p.x), p.y + .5 * (q.y - p.y)),
                Point(p.x + .8 * (q.x - p.x), p.y + .8 * (q.y - p.y)),
                radialPoint(outer, n, 1.08),
            )
        val v = IntArray(5)
        for ((j, point) in positions.withIndex()) {
            val x = floor(point.x + .5).toInt()
            val y = floor(point.y + .5).toInt()
            if (x !in 0 until width || y !in 0 until height) return false
            v[j] = pixels[y * width + x].toInt() and 255
        }
        val lo = min(v[1], min(v[2], v[3]))
        val hi = max(v[1], max(v[2], v[3]))
        if (lo >= v[0] + 12 && hi - lo <= 24) positive++
        if (hi <= v[0] - 12 && hi - lo <= 24) negative++
        if (lo >= v[4] + 12) exteriorPositive++
        if (hi <= v[4] - 12) exteriorNegative++
        if (min(lo, v[0]) >= v[4] + 12 && hi - lo <= 24) filledPositive++
        if (max(hi, v[0]) <= v[4] - 12 && hi - lo <= 24) filledNegative++
    }
    return (positive >= 47 && exteriorPositive >= 47) ||
        (negative >= 47 && exteriorNegative >= 47) ||
        filledPositive >= 65 ||
        filledNegative >= 65
}

/** Evaluation-only local boundary path. Leaves original P2-v2 selectable for comparison. */
internal fun selectBLocalObjects(
    gray: Mat,
    input: List<BRepresentative>,
    entryBudget: Int = 25000,
    maxPairChecks: Int = 1_000_000,
    checkActive: () -> Unit = {},
): BRadialRun = selectBBoundaryObjects(gray, input, entryBudget, maxPairChecks, checkActive, true)
