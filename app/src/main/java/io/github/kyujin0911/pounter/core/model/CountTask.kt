package io.github.kyujin0911.pounter.core.model

import kotlinx.serialization.Serializable

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
    val analysisMetadata: AnalysisMetadata? = null,
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
            analysisMetadata = null,
        )
    }

    fun undoEdit(): CountTask =
        if (confirmed || undo == null) this else copy(markers = undo, undo = null)

    fun confirm(time: Long): CountTask {
        check(!confirmed && analyzed)
        return copy(confirmedAt = time, undo = null)
    }
}
