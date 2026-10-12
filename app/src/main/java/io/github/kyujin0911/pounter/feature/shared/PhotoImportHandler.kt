package io.github.kyujin0911.pounter.feature.shared

import androidx.lifecycle.SavedStateHandle
import io.github.kyujin0911.pounter.data.repository.CountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An entry-owned handler; no global selection or shared ViewModel instance. */
class PhotoImportHandler(
    private val countRepository: CountRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ownerScope: CoroutineScope,
    private val canImport: () -> Boolean,
) {
    private val _uiState =
        MutableStateFlow(PhotoImportUiState(importedTaskId = savedStateHandle["importedTaskId"]))
    val uiState = _uiState.asStateFlow()

    fun dispatch(action: PhotoImportAction) {
        when (action) {
            PhotoImportAction.ClearMessage -> _uiState.update { it.copy(message = null) }
            PhotoImportAction.Handled -> {
                savedStateHandle["importedTaskId"] = null
                _uiState.update { it.copy(importedTaskId = null) }
            }
            is PhotoImportAction.Import -> {
                if (_uiState.value.isImporting || !canImport()) return
                _uiState.update { it.copy(isImporting = true, message = null) }
                ownerScope.launch {
                    try {
                        val importedTask = countRepository.import(action.photoUri)
                        savedStateHandle["importedTaskId"] = importedTask.id
                        _uiState.update { it.copy(importedTaskId = importedTask.id) }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        _uiState.update {
                            it.copy(message = failure.message ?: "사진을 가져올 수 없습니다. 다시 시도해주세요.")
                        }
                    } finally {
                        _uiState.update { it.copy(isImporting = false) }
                    }
                }
            }
        }
    }
}
