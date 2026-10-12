package io.github.kyujin0911.pounter.feature.home

import io.github.kyujin0911.pounter.core.model.CountTask

data class HomeUiState(
    val records: List<CountTask> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)
