package io.github.kyujin0911.pounter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.kyujin0911.pounter.feature.home.HomeAction
import io.github.kyujin0911.pounter.feature.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun listRemainsObservedWhenRecordChanges() =
        runTest(dispatcher) {
            val repository = ScreenTestRepository()
            val viewModel = HomeViewModel(repository, SavedStateHandle())
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(listOf(repository.storedTask), viewModel.uiState.value.records)
            repository.storedTasks.value = emptyList()
            runCurrent()
            assertTrue(viewModel.uiState.value.records.isEmpty())
            assertEquals(1, repository.listAccessCount)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun listFailureEndsLoadingAndCanDismissMessage() =
        runTest(dispatcher) {
            val repository = ScreenTestRepository().apply { taskStream = flow { error("목록 실패") } }
            val viewModel = HomeViewModel(repository, SavedStateHandle())
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("목록 실패", viewModel.uiState.value.message)
            viewModel.dispatch(HomeAction.ClearMessage)
            assertNull(viewModel.uiState.value.message)
            viewModel.viewModelScope.cancel()
        }
}
