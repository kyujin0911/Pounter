package io.github.kyujin0911.pounter.core.model

import kotlin.math.hypot

/** Candidates are in normalized image coordinates; concentric inner/outer edges count once. */
fun suppressConcentric(candidates: List<Marker>, width: Int, height: Int): List<Marker> {
    val result = mutableListOf<Marker>()
    for (candidate in candidates.sortedByDescending { it.radius }) {
        val duplicate =
            result.any {
                val distance = hypot((it.x - candidate.x) * width, (it.y - candidate.y) * height)
                distance < minOf(it.radius, candidate.radius) * width * 0.6f
            }
        if (!duplicate) result += candidate
    }
    return result.sortedWith(compareBy({ it.y }, { it.x }))
}
