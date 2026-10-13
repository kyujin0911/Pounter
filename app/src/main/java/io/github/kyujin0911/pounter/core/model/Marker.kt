package io.github.kyujin0911.pounter.core.model

import kotlinx.serialization.Serializable

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
