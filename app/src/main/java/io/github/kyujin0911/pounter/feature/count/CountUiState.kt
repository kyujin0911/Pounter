package io.github.kyujin0911.pounter.feature.count

import android.graphics.Bitmap
import io.github.kyujin0911.pounter.core.model.CountTask

enum class Operation {
    NONE,
    LOADING,
    ANALYZING,
    SAVING,
}

data class CountUiState(
    val task: CountTask? = null,
    val preview: Bitmap? = null,
    val operation: Operation = Operation.LOADING,
    val message: String? = null,
    val unavailable: Boolean = false,
    val deleted: Boolean = false,
    val unsavedChanges: Boolean = false,
    val analysisWaiting: Boolean = false,
) {
    val isBusy: Boolean
        get() = operation != Operation.NONE
}
