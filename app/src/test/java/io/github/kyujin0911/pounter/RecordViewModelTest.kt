package io.github.kyujin0911.pounter

import androidx.lifecycle.SavedStateHandle
import io.github.kyujin0911.pounter.feature.count.CountViewModel
import io.github.kyujin0911.pounter.feature.record.RecordAction
import io.github.kyujin0911.pounter.feature.record.RecordViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class RecordViewModelTest {
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
    fun detailViewModelsDoNotSubscribeToHomeList() =
        runTest(dispatcher) {
            val repository = ScreenTestRepository()
            val work = CountViewModel(repository, SavedStateHandle(mapOf("taskId" to "task")))
            val record = RecordViewModel(repository, SavedStateHandle(mapOf("taskId" to "task")))
            runCurrent()
            assertEquals(repository.storedTask, work.uiState.value.task)
            assertEquals(repository.storedTask, record.uiState.value.task)
            assertEquals(0, repository.listAccessCount)
        }

    @Test
    fun missingRecordOffersRecoveryAndPreviewFailurePreservesLoadedTask() =
        runTest(dispatcher) {
            val repository = ScreenTestRepository()
            val missing =
                RecordViewModel(repository, SavedStateHandle(mapOf("taskId" to "missing")))
            runCurrent()
            assertNull(missing.uiState.value.task)
            assertFalse(missing.uiState.value.isBusy)
            assertTrue(missing.uiState.value.message!!.contains("기록을 찾을 수 없습니다"))
            repository.hasPreviewFailure = true
            val record = RecordViewModel(repository, SavedStateHandle(mapOf("taskId" to "task")))
            runCurrent()
            assertEquals(repository.storedTask, record.uiState.value.task)
            assertEquals("사진 읽기 실패", record.uiState.value.message)
        }

    @Test
    fun failedDeleteKeepsRecordAndOnlySuccessfulRetrySignalsNavigation() =
        runTest(dispatcher) {
            val repository = ScreenTestRepository().apply { hasDeleteFailure = true }
            val viewModel = RecordViewModel(repository, SavedStateHandle(mapOf("taskId" to "task")))
            runCurrent()
            viewModel.dispatch(RecordAction.Delete)
            runCurrent()
            assertFalse(viewModel.uiState.value.deleted)
            assertEquals(repository.storedTask, viewModel.uiState.value.task)
            assertEquals("삭제 실패", viewModel.uiState.value.message)
            repository.hasDeleteFailure = false
            viewModel.dispatch(RecordAction.Delete)
            runCurrent()
            assertTrue(viewModel.uiState.value.deleted)
            assertTrue(repository.storedTasks.value.isEmpty())
        }

    @Test
    fun duplicateDeleteIsIgnoredUntilDurableDeleteCompletes() =
        runTest(dispatcher) {
            val repository = ScreenTestRepository().apply { deletionGate = CompletableDeferred() }
            val viewModel = RecordViewModel(repository, SavedStateHandle(mapOf("taskId" to "task")))
            runCurrent()
            viewModel.dispatch(RecordAction.Delete)
            viewModel.dispatch(RecordAction.Delete)
            runCurrent()
            assertEquals(1, repository.deleteCount)
            assertTrue(viewModel.uiState.value.isDeleting)
            assertFalse(viewModel.uiState.value.deleted)
            repository.deletionGate!!.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value.deleted)
        }
}
