package io.github.kyujin0911.pounter.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.feature.shared.PhotoImportHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(countRepository: CountRepository, savedStateHandle: SavedStateHandle) :
    ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    val photoImport =
        PhotoImportHandler(countRepository, savedStateHandle, viewModelScope) {
            !uiState.value.isLoading
        }

    init {
        viewModelScope.launch {
            countRepository.tasks
                .catch { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, message = failure.message ?: "기록을 읽을 수 없습니다.")
                    }
                }
                .collect { records ->
                    _uiState.update { it.copy(records = records, isLoading = false) }
                }
        }
    }

    fun dispatch(action: HomeAction) {
        when (action) {
            HomeAction.ClearMessage -> _uiState.update { it.copy(message = null) }
        }
    }
}
