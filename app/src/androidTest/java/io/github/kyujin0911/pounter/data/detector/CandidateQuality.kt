package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*

/** Scalar observations only. Angles are eccentric parameter angles, not physical arc length. */
internal data class BEdgeQuality(
    val supported: Int,
    val invalid: Int,
    val unsupported: Int,
    val longestGapSamples: Int,
    val distinctPixels: Int,
    val maxStepPx: Double,
) {
    val support: Double
        get() = supported / 72.0

    val longestGapDegrees: Double
        get() = longestGapSamples * 5.0

    val undersampled: Boolean
        get() = distinctPixels < 72 || maxStepPx > 5.0
}

/** Development ablations only; baseline is still the default, OBSERVE rejects nothing. */
internal enum class BQualityPolicy(val maxGapSamples: Int) {
    OBSERVE(72),
    GAP_90(18),
    GAP_120(24);

    fun rejects(q: BEdgeQuality): Boolean =
        this != OBSERVE && q.invalid == 0 && !q.undersampled && q.longestGapSamples > maxGapSamples
}

/** Caller owns edge bytes. All72 samples stay in denominator; never clamp coordinates. */
internal fun measureBEdgeQuality(
    pixels: ByteArray,
    width: Int,
    height: Int,
    g: EllipseGeometry,
): BEdgeQuality {
    require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
    val hits = BooleanArray(72)
    val positions = HashSet<Pair<Int, Int>>(72)
    var invalid = 0
    var maxStep = 0.0
    val ca = cos(g.angleRadians)
    val sa = sin(g.angleRadians)
    for (n in 0 until 72) {
        val t = n * 2 * PI / 72
        val u = g.semiMajorPx * cos(t)
        val v = g.semiMinorPx * sin(t)
        val x = floor(g.centerXPx + u * ca - v * sa + .5).toInt()
        val y = floor(g.centerYPx + u * sa + v * ca + .5).toInt()
        positions += x to y
        if (x !in 0 until width || y !in 0 until height) invalid++
        else hits[n] = pixels[y * width + x].toInt() != 0
        val next = (n + 1) * 2 * PI / 72
        maxStep =
            max(
                maxStep,
                hypot(g.semiMajorPx * (cos(next) - cos(t)), g.semiMinorPx * (sin(next) - sin(t))),
            )
    }
    var run = 0
    var longest = 0
    for (n in 0 until 144) {
        run = if (hits[n % 72]) 0 else run + 1
        longest = max(longest, run)
    }
    val supported = hits.count { it }
    return BEdgeQuality(
        supported,
        invalid,
        72 - supported - invalid,
        min(72, longest),
        positions.size,
        maxStep,
    )
}
