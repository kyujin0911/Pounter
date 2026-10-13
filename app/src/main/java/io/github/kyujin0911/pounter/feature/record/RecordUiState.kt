package io.github.kyujin0911.pounter.feature.record

import android.graphics.Bitmap
import io.github.kyujin0911.pounter.core.model.CountTask

data class RecordUiState(
    val task: CountTask? = null,
    val preview: Bitmap? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null,
) {
    val isBusy: Boolean
        get() = isLoading || isDeleting
}
