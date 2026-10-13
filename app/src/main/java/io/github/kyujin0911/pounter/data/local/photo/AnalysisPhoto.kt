package io.github.kyujin0911.pounter.data.local.photo

import android.graphics.Bitmap

class AnalysisPhoto(val bitmap: Bitmap, val photoWidthPx: Int, val photoHeightPx: Int) :
    AutoCloseable {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
