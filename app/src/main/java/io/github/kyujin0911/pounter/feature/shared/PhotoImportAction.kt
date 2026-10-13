package io.github.kyujin0911.pounter.feature.shared

import android.net.Uri

sealed interface PhotoImportAction {
    data class Import(val photoUri: Uri) : PhotoImportAction

    data object Handled : PhotoImportAction

    data object ClearMessage : PhotoImportAction
}
