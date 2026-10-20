package io.github.kyujin0911.pounter.data.detector

import kotlin.math.*

internal fun bContinuousPoint(
    g: EllipseGeometry,
    n: Int,
    scale: Double = 1.0,
): Pair<Double, Double> {
    val t = n * 2 * PI / 72
    // A shared image-space ray, independent of each ellipse's local angle modulo PI.
    val radius =
        scale /
            hypot(cos(t - g.angleRadians) / g.semiMajorPx, sin(t - g.angleRadians) / g.semiMinorPx)
    return g.centerXPx + radius * cos(t) to g.centerYPx + radius * sin(t)
}

internal fun bContinuousRadius(g: EllipseGeometry, x: Double, y: Double): Double {
    val dx = x - g.centerXPx
    val dy = y - g.centerYPx
    return hypot(
        (dx * cos(g.angleRadians) + dy * sin(g.angleRadians)) / g.semiMajorPx,
        (-dx * sin(g.angleRadians) + dy * cos(g.angleRadians)) / g.semiMinorPx,
    )
}

internal fun bContinuousGeometry(inner: EllipseGeometry, outer: EllipseGeometry): Boolean {
    if (
        hypot(inner.centerXPx - outer.centerXPx, inner.centerYPx - outer.centerYPx) >
            0.7 * inner.semiMinorPx ||
            inner.semiMajorPx / outer.semiMajorPx !in 0.2..0.85 ||
            inner.semiMinorPx / outer.semiMinorPx !in 0.2..0.85
    )
        return false
    return (0 until 72).all { n ->
        val p = bContinuousPoint(inner, n)
        bContinuousRadius(outer, p.first, p.second) <= 1.01
    }
}
