package io.github.kyujin0911.pounter.feature.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.feature.shared.PhotoImportHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Read-only lookup and deletion; no editing/analysis or record-list subscription. */
class RecordViewModel(
    private val countRepository: CountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState = _uiState.asStateFlow()
    val photoImport =
        PhotoImportHandler(countRepository, savedStateHandle, viewModelScope) {
            !uiState.value.isBusy
        }

    init {
        viewModelScope.launch {
            try {
                val taskId = savedStateHandle.get<String>("taskId")
                val task = taskId?.let { countRepository.get(it) }
                _uiState.update {
                    it.copy(
                        task = task,
                        message = if (task == null) "기록을 찾을 수 없습니다. 삭제된 작업일 수 있습니다." else null,
                    )
                }
                val preview = task?.let { countRepository.preview(it) }
                _uiState.update { it.copy(preview = preview) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.message ?: "기록 복원에 실패했습니다.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun dispatch(action: RecordAction) {
        if (action == RecordAction.ClearMessage) {
            _uiState.update { it.copy(message = null) }
            return
        }
        if (_uiState.value.isBusy || photoImport.uiState.value.isImporting) return
        val task = _uiState.value.task ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                countRepository.delete(task)
                _uiState.update { it.copy(deleted = true) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.message ?: "기록 삭제에 실패했습니다.") }
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }
}
