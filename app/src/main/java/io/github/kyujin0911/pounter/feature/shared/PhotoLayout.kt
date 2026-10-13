package io.github.kyujin0911.pounter.feature.shared

internal fun photoViewportHeightDp(
    contentHeightDp: Float,
    contentWidthDp: Float,
    photoAspectRatio: Float,
): Float =
    minOf(contentWidthDp / photoAspectRatio, contentHeightDp * .45f, 480f)
        .coerceAtLeast(minOf(180f, contentHeightDp))

internal fun shouldPinConfirmAction(contentHeightDp: Float, fontScale: Float): Boolean =
    contentHeightDp >= 720f && fontScale < 1.5f
