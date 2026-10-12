package io.github.kyujin0911.pounter.feature.shared

import kotlin.math.min

data class PhotoGeometry(
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val zoom: Float = 1f,
    val panXPx: Float = 0f,
    val panYPx: Float = 0f,
) {
    val fit: Float = min(viewportWidthPx / imageWidthPx, viewportHeightPx / imageHeightPx)
    val drawnWidthPx: Float = imageWidthPx * fit * zoom
    val drawnHeightPx: Float = imageHeightPx * fit * zoom
    val leftPx: Float = (viewportWidthPx - drawnWidthPx) / 2f + panXPx
    val topPx: Float = (viewportHeightPx - drawnHeightPx) / 2f + panYPx

    fun imagePoint(screenXPx: Float, screenYPx: Float): Pair<Float, Float>? {
        val nx = (screenXPx - leftPx) / drawnWidthPx
        val ny = (screenYPx - topPx) / drawnHeightPx
        return if (nx in 0f..1f && ny in 0f..1f) nx to ny else null
    }

    fun screenPoint(normalizedX: Float, normalizedY: Float) =
        (leftPx + normalizedX * drawnWidthPx) to (topPx + normalizedY * drawnHeightPx)

    fun clampPan(panXPx: Float, panYPx: Float): Pair<Float, Float> {
        val limitX = ((drawnWidthPx - viewportWidthPx) / 2).coerceAtLeast(0f)
        val limitY = ((drawnHeightPx - viewportHeightPx) / 2).coerceAtLeast(0f)
        return panXPx.coerceIn(-limitX, limitX) to panYPx.coerceIn(-limitY, limitY)
    }
}
