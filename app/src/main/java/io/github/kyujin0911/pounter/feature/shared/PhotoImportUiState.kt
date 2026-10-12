package io.github.kyujin0911.pounter.feature.shared
data class PhotoImportUiState(
    val isImporting: Boolean = false,
    val importedTaskId: String? = null,
    val message: String? = null,
)
