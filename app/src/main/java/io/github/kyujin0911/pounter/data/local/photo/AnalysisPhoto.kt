package io.github.kyujin0911.pounter.data.local.photo

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalysisPhoto(val bitmap: Bitmap, val photoWidthPx: Int, val photoHeightPx: Int) :
    AutoCloseable {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/** Keep ownership until the decoded image has actually been delivered to the caller. */
internal suspend fun readOwnedAnalysisPhoto(
    read: suspend CoroutineScope.() -> AnalysisPhoto
): AnalysisPhoto {
    var acquired: AnalysisPhoto? = null
    try {
        return withContext(Dispatchers.IO) { read().also { acquired = it } }
    } catch (failure: Throwable) {
        acquired?.close()
        throw failure
    }
}
