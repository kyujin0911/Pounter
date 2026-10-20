package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*
import kotlinx.coroutines.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

internal const val B_VERSION = "edge-drawing-common-android-v2-even-sampling"

/** Six-value ED output uses radius for circles, SEMI axes and degrees for ellipses. */
internal fun canonicalEdgeDrawing(raw: DoubleArray, offset: Int): EllipseGeometry {
    require(offset >= 0 && offset + 6 <= raw.size)
    val values = raw.sliceArray(offset until offset + 6)
    require(values.all { it.isFinite() } && values[2] >= 0)
    var a = if (values[2] > 0) values[2] else values[3]
    var b = if (values[2] > 0) values[2] else values[4]
    var angle = if (values[2] > 0) 0.0 else values[5] * PI / 180
    if (a < b) {
        val swap = a
        a = b
        b = swap
        angle += PI / 2
    }
    angle = ((angle % PI) + PI) % PI
    return EllipseGeometry(values[0], values[1], a, b, angle)
}

internal data class BObservation(
    val id: Int,
    val geometry: EllipseGeometry,
    val support: Double? = null,
    val stage: String = "quality",
)

internal data class BRepresentative(val anchor: BObservation, val sourceIds: List<Int>)

internal val bObservationOrder =
    compareByDescending<BObservation> { it.support }
        .thenBy { it.geometry.centerXPx }
        .thenBy { it.geometry.centerYPx }
        .thenBy { it.geometry.semiMajorPx }
        .thenBy { it.geometry.semiMinorPx }
        .thenBy { it.geometry.angleRadians }
        .thenBy { it.id }

internal fun mergeBObservations(
    input: List<BObservation>,
    maxPairChecks: Int = 1_000_000,
): List<BRepresentative> {
    require(input.all { it.support != null && it.support.isFinite() && it.support in 0.0..1.0 })
    require(maxPairChecks >= 0)
    var pairChecks = 0
    val sorted = input.sortedWith(bObservationOrder)
    // ponytail: fixed development candidates use an O(n²) anchor scan, indexed only if measured
    // costly.
    val reps = mutableListOf<BObservation>()
    val sources = mutableListOf<MutableList<Int>>()
    for (o in sorted) {
        val index =
            reps.indexOfFirst {
                if (pairChecks >= maxPairChecks) bPairBudgetExceeded("same-rim")
                pairChecks++
                sameBRim(o.geometry, it.geometry)
            }
        if (index < 0) {
            reps += o
            sources += mutableListOf(o.id)
        } else sources[index] += o.id
    }
    return reps.mapIndexed { i, o -> BRepresentative(o, sources[i].sorted()) }
}

private fun sameBRim(a: EllipseGeometry, b: EllipseGeometry): Boolean {
    if (
        hypot(a.centerXPx - b.centerXPx, a.centerYPx - b.centerYPx) >
            .1 * min(a.semiMinorPx, b.semiMinorPx)
    )
        return false
    if (
        max(
            abs(a.semiMajorPx - b.semiMajorPx) / max(a.semiMajorPx, b.semiMajorPx),
            abs(a.semiMinorPx - b.semiMinorPx) / max(a.semiMinorPx, b.semiMinorPx),
        ) > .1
    )
        return false
    if (max(a.semiMajorPx / a.semiMinorPx, b.semiMajorPx / b.semiMinorPx) <= 1.1) return true
    val d = abs(a.angleRadians - b.angleRadians) % PI
    return min(d, PI - d) <= 5 * PI / 180
}

internal data class BBaseRun(
    val rawCount: Int,
    val gateCount: Int,
    val qualityCount: Int,
    val representatives: List<BRepresentative>,
    val observations: List<BObservation>,
    val truncated: Boolean,
)

/** Full denominator72: out-of-image points remain unsupported, never clamp onto an edge. */
internal fun processBBaseGeometry(
    gray: Mat,
    raw: DoubleArray,
    entryBudget: Int = 25000,
    maxCandidates: Int = 10000,
    maxPairChecks: Int = 1_000_000,
    observe: (BObservation, ByteArray, Int, Int) -> BObservation = { o, _, _, _ -> o },
    checkActive: () -> Unit = {},
): BBaseRun {
    require(gray.type() == CvType.CV_8UC1 && gray.rows() >= 8 && gray.cols() >= 8)
    require(raw.size % 6 == 0 && entryBudget >= 0 && maxCandidates > 0)
    if (raw.size / 6 > maxCandidates)
        throw DetectionFailure(DetectionFailureReason.CANDIDATE_SATURATION)
    val blur = Mat()
    val edges = Mat()
    val kernel = Mat.ones(5, 5, CvType.CV_8UC1)
    val observations = mutableListOf<BObservation>()
    val accepted = mutableListOf<BObservation>()
    var gated = 0
    try {
        Imgproc.medianBlur(gray, blur, 3)
        Imgproc.Canny(blur, edges, 40.0, 90.0)
        Imgproc.dilate(edges, edges, kernel)
        val pixels = ByteArray(edges.rows() * edges.cols())
        edges.get(0, 0, pixels)
        for (i in 0 until raw.size / 6) {
            checkActive()
            val g = canonicalEdgeDrawing(raw, i * 6)
            val pass =
                g.semiMinorPx >= 7 && g.semiMajorPx <= 80 && g.semiMajorPx / g.semiMinorPx <= 2.5
            var score: Double? = null
            if (pass) {
                gated++
                var hits = 0
                for (n in 0 until 72) {
                    val t = n * 2 * PI / 72
                    val u = g.semiMajorPx * cos(t)
                    val v = g.semiMinorPx * sin(t)
                    val x =
                        floor(g.centerXPx + u * cos(g.angleRadians) - v * sin(g.angleRadians) + .5)
                            .toInt()
                    val y =
                        floor(g.centerYPx + u * sin(g.angleRadians) + v * cos(g.angleRadians) + .5)
                            .toInt()
                    if (
                        x in 0 until edges.cols() &&
                            y in 0 until edges.rows() &&
                            pixels[y * edges.cols() + x].toInt() != 0
                    )
                        hits++
                }
                score = hits / 72.0
            }
            val o =
                BObservation(
                    i,
                    g,
                    score,
                    if (!pass) "GEOMETRY_REJECTED"
                    else if (score!! < .65) "SUPPORT_REJECTED" else "quality",
                )
            val observed = observe(o, pixels, edges.cols(), edges.rows())
            if (observations.size < entryBudget) observations += observed
            if (observed.stage == "quality") accepted += observed
        }
        val sameRim = mergeBObservations(accepted, maxPairChecks)
        return BBaseRun(
            raw.size / 6,
            gated,
            accepted.size,
            sameRim,
            observations,
            raw.size / 6 > observations.size,
        )
    } finally {
        kernel.release()
        edges.release()
        blur.release()
    }
}
