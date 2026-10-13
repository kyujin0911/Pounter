package io.github.kyujin0911.pounter.feature.shared

import android.graphics.Bitmap
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker

internal fun designTask(
    markerCount: Int = 0,
    analyzed: Boolean = false,
    confirmed: Boolean = false,
) =
    CountTask(
        id = "design-fixture",
        photoName = "synthetic.png",
        width = 900,
        height = 650,
        createdAt = 0L,
        markers =
            List(markerCount) { i ->
                Marker(
                    "marker-$i",
                    .05f + (i % 20) * .9f / 19,
                    .05f + (i / 20) * .9f / 14,
                    .01f,
                    manual = true,
                )
            },
        analyzed = analyzed,
        confirmedAt = if (confirmed) 1000L else null,
    )

internal fun designBitmap(): Bitmap =
    Bitmap.createBitmap(900, 650, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.GRAY)
    }
