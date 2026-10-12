package io.github.kyujin0911.pounter.model

import kotlin.math.hypot
import kotlinx.serialization.Serializable

@Serializable
data class Region(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    init {
        require(
            listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } &&
                left < right &&
                top < bottom
        )
    }

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

@Serializable
data class Marker(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val manual: Boolean = false,
) {
    init {
        require(
            x.isFinite() &&
                y.isFinite() &&
                x in 0f..1f &&
                y in 0f..1f &&
                radius.isFinite() &&
                radius > 0f
        )
    }
}

@Serializable
data class CountTask(
    val id: String,
    val photoName: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val revision: Long = 0,
    val region: Region = Region(),
    val markers: List<Marker> = emptyList(),
    val automatic: List<Marker> = emptyList(),
    val undo: List<Marker>? = null,
    val analyzed: Boolean = false,
    val analysisMillis: Long? = null,
    val confirmedAt: Long? = null,
    val deleted: Boolean = false,
) {
    val count: Int
        get() = markers.size

    val confirmed: Boolean
        get() = confirmedAt != null

    fun edit(next: List<Marker>): CountTask {
        check(!confirmed)
        require(next.map { it.id }.distinct().size == next.size)
        require(next.all { region.contains(it.x, it.y) })
        return copy(markers = next, undo = markers)
    }

    fun reset(next: Region): CountTask {
        check(!confirmed)
        return copy(
            region = next,
            markers = emptyList(),
            automatic = emptyList(),
            undo = null,
            analyzed = false,
            analysisMillis = null,
        )
    }

    fun undoEdit(): CountTask =
        if (confirmed || undo == null) this else copy(markers = undo, undo = null)

    fun confirm(time: Long): CountTask {
        check(!confirmed && analyzed)
        return copy(confirmedAt = time, undo = null)
    }
}

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
