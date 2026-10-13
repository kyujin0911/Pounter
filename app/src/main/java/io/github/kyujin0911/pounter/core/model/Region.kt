package io.github.kyujin0911.pounter.core.model

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
