package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.kyujin0911.pounter.core.model.AnalysisMetadata
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.core.model.Region
import io.github.kyujin0911.pounter.data.detector.AnalysisStage
import io.github.kyujin0911.pounter.data.detector.DETECTOR_VALIDATED
import io.github.kyujin0911.pounter.data.detector.Detection
import io.github.kyujin0911.pounter.data.detector.ValidationRequiredDetector
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.feature.count.CountAction
import io.github.kyujin0911.pounter.feature.count.CountViewModel
import io.github.kyujin0911.pounter.feature.count.Operation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    private class FakeRepository : CountRepository {
        var storedTask = CountTask("task", "photo.jpg", 1000, 1500, 1L, analyzed = true)
        override val tasks = MutableStateFlow(listOf(storedTask))
        var hasSaveFailure = false
        var saveCount = 0
        var hasDeleteFailure = false
        var hasPreviewFailure = false
        var previewedTask: CountTask? = null
        var importedTask: CountTask? = null
        var analysisResultGate: CompletableDeferred<Detection>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var afterCommitGate: CompletableDeferred<Unit>? = null
        var stageCallback: ((AnalysisStage) -> Unit)? = null

        override suspend fun get(id: String) = storedTask.takeIf { it.id == id }

        override suspend fun preview(task: CountTask): Bitmap? {
            previewedTask = task
            if (hasPreviewFailure) error("사진 읽기 실패")
            return null
        }

        override suspend fun import(uri: Uri): CountTask = importedTask ?: error("import failed")

        override suspend fun save(task: CountTask): CountTask {
            saveCount++
            saveGate?.await()
            if (hasSaveFailure) error("저장 공간 부족")
            check(task.revision == storedTask.revision)
            storedTask = task.copy(revision = task.revision + 1)
            tasks.value = listOf(storedTask)
            afterCommitGate?.let { withContext(NonCancellable) { it.await() } }
            return storedTask
        }

        override suspend fun delete(task: CountTask) {
            if (hasDeleteFailure) error("삭제 실패")
            tasks.value = emptyList()
        }

        override suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit): Detection {
            stageCallback = onStage
            onStage(AnalysisStage.WAITING)
            return detect(task)
        }

        override suspend fun detect(task: CountTask): Detection {
            val deferred = analysisResultGate ?: return Detection(emptyList(), 1L)
            return withContext(NonCancellable) { deferred.await() }
        }
    }

    private suspend fun TestScope.open(countRepository: FakeRepository): CountViewModel {
        val viewModel = CountViewModel(countRepository, SavedStateHandle(mapOf("taskId" to "task")))
        runCurrent()
        return viewModel
    }

    @Test
    fun analysisSaveFailureKeepsNewResultForRetry() =
        runTest(dispatcher) {
            val repo = FakeRepository()
            val vm = open(repo)
            repo.hasSaveFailure = true
            val result =
                Detection(
                    listOf(Marker("new", .2f, .3f, .02f)),
                    7,
                    AnalysisMetadata("evaluation-v1"),
                )
            repo.analysisResultGate = CompletableDeferred(result)
            vm.dispatch(CountAction.Analyze)
            runCurrent()
            assertEquals(result.markers, vm.uiState.value.task!!.markers)
            assertEquals(result.markers, vm.uiState.value.task!!.automatic)
            assertEquals(result.analysisMetadata, vm.uiState.value.task!!.analysisMetadata)
            assertTrue(vm.uiState.value.unsavedChanges)
            vm.dispatch(CountAction.Add(.5f, .5f))
            vm.dispatch(CountAction.Analyze)
            vm.dispatch(CountAction.Confirm)
            runCurrent()
            assertEquals(1, repo.saveCount)
            repo.hasSaveFailure = false
            vm.dispatch(CountAction.RetrySave)
            runCurrent()
            assertEquals(result.markers, repo.storedTask.markers)
            assertEquals(2, repo.saveCount)
            assertFalse(vm.uiState.value.unsavedChanges)
        }

    @Test
    fun failedReanalysisKeepsCorrections() =
        runTest(dispatcher) {
            val repo = FakeRepository()
            repo.storedTask = repo.storedTask.edit(listOf(Marker("m", .5f, .5f, .02f, true)))
            val vm = open(repo)
            repo.analysisResultGate =
                CompletableDeferred<Detection>().also {
                    it.completeExceptionally(IllegalStateException("failed"))
                }
            vm.dispatch(CountAction.Analyze)
            runCurrent()
            assertEquals(repo.storedTask, vm.uiState.value.task)
            assertEquals(0, repo.saveCount)
        }

    @Test
    fun oldPendingSnapshotRestoresWithoutMetadata() =
        runTest(dispatcher) {
            val repo = FakeRepository()
            val old =
                """{"id":"task","photoName":"photo.jpg","width":1000,"height":1500,"createdAt":1,"analyzed":true,"markers":[{"id":"old","x":0.5,"y":0.5,"radius":0.02,"manual":true}]}"""
            val vm =
                CountViewModel(
                    repo,
                    SavedStateHandle(mapOf("taskId" to "task", "pendingSave" to old)),
                )
            runCurrent()
            assertNull(vm.uiState.value.task!!.analysisMetadata)
            assertTrue(vm.uiState.value.unsavedChanges)
            vm.dispatch(CountAction.RetrySave)
            runCurrent()
            assertEquals("old", repo.storedTask.markers.single().id)
        }

    @Test
    fun savedResultAtCancellationBoundaryIsReconciled() =
        runTest(dispatcher) {
            val repo = FakeRepository()
            val pending =
                repo.storedTask.copy(
                    markers = listOf(Marker("new", .5f, .5f, .02f)),
                    analysisMetadata = AnalysisMetadata("v"),
                )
            repo.storedTask = pending.copy(revision = 1, updatedAt = 10)
            val handle =
                SavedStateHandle(
                    mapOf("taskId" to "task", "pendingSave" to Json.encodeToString(pending))
                )
            val vm = CountViewModel(repo, handle)
            runCurrent()
            assertFalse(vm.uiState.value.unsavedChanges)
            assertNull(handle.get<String>("pendingSave"))
            vm.dispatch(CountAction.RetrySave)
            runCurrent()
            assertEquals(0, repo.saveCount)
        }

    @Test
    fun lateResultWithChangedSnapshotIsIgnored() =
        runTest(dispatcher) {
            for (change in
                listOf<(CountTask) -> CountTask>(
                    { it.copy(id = "other") },
                    { it.copy(revision = 1) },
                    { it.copy(photoName = "other.jpg") },
                    { it.copy(region = Region(.1f, .1f, .9f, .9f)) },
                )) {
                val repo = FakeRepository()
                val vm = open(repo)
                repo.analysisResultGate = CompletableDeferred()
                vm.dispatch(CountAction.Analyze)
                runCurrent()
                // Simulate a restored/replaced snapshot while the asynchronous result is in flight.
                val field =
                    CountViewModel::class.java.getDeclaredField("_uiState").apply {
                        isAccessible = true
                    }
                @Suppress("UNCHECKED_CAST")
                val state =
                    field.get(vm)
                        as MutableStateFlow<io.github.kyujin0911.pounter.feature.count.CountUiState>
                val replacement = change(state.value.task!!)
                state.value = state.value.copy(task = replacement)
                repo.analysisResultGate!!.complete(
                    Detection(listOf(Marker("late", .4f, .4f, .02f)), 1)
                )
                runCurrent()
                assertEquals(replacement, vm.uiState.value.task)
                assertEquals(0, repo.saveCount)
            }
        }

    @Test
    fun entryCancellationAfterCommittedSaveReopensWithoutDuplicateWrite() =
        runTest(dispatcher) {
            val repo = FakeRepository()
            val handle = SavedStateHandle(mapOf("taskId" to "task"))
            val vm = CountViewModel(repo, handle)
            runCurrent()
            repo.afterCommitGate = CompletableDeferred()
            vm.dispatch(CountAction.Analyze)
            runCurrent()
            assertEquals(1L, repo.storedTask.revision)
            assertNotNull(handle.get<String>("pendingSave"))
            vm.viewModelScope.cancel()
            repo.afterCommitGate!!.complete(Unit)
            runCurrent()
            val restored =
                CountViewModel(
                    repo,
                    SavedStateHandle(
                        mapOf(
                            "taskId" to "task",
                            "pendingSave" to handle.get<String>("pendingSave"),
                        )
                    ),
                )
            runCurrent()
            assertFalse(restored.uiState.value.unsavedChanges)
            restored.dispatch(CountAction.RetrySave)
            runCurrent()
            assertEquals(1, repo.saveCount)
        }

    @Test
    fun waitingProcessingAndStaleCallbackAreGenerationBound() =
        runTest(dispatcher) {
            val repo = FakeRepository()
            val vm = open(repo)
            val gate = CompletableDeferred<Detection>()
            repo.analysisResultGate = gate
            vm.dispatch(CountAction.Analyze)
            runCurrent()
            assertTrue(vm.uiState.value.analysisWaiting)
            val old = repo.stageCallback!!
            old(AnalysisStage.PROCESSING)
            runCurrent()
            assertFalse(vm.uiState.value.analysisWaiting)
            vm.dispatch(CountAction.CancelAnalysis)
            runCurrent()
            old(AnalysisStage.WAITING)
            runCurrent()
            assertFalse(vm.uiState.value.analysisWaiting)
            repo.analysisResultGate = CompletableDeferred()
            vm.dispatch(CountAction.Analyze)
            runCurrent()
            assertTrue(vm.uiState.value.analysisWaiting)
            old(AnalysisStage.PROCESSING)
            runCurrent()
            assertTrue(vm.uiState.value.analysisWaiting)
            gate.complete(Detection(emptyList(), 1))
            repo.stageCallback!!(AnalysisStage.PROCESSING)
            repo.analysisResultGate!!.complete(Detection(emptyList(), 1))
            runCurrent()
            assertFalse(vm.uiState.value.analysisWaiting)
            assertEquals(1, repo.saveCount)
        }

    @Test
    fun restoresPersistedTaskAndDoesNotPretendAnalysisIsRunning() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            assertEquals(countRepository.storedTask, viewModel.uiState.value.task)
            assertEquals(Operation.NONE, viewModel.uiState.value.operation)
        }

    @Test
    fun failedSaveKeepsUnsavedMarkersAndRetriesTheSameChange() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            countRepository.hasSaveFailure = true
            viewModel.dispatch(CountAction.Add(.5f, .5f))
            runCurrent()
            assertEquals(1, viewModel.uiState.value.task!!.count)
            assertEquals("저장 공간 부족", viewModel.uiState.value.message)
            assertTrue(viewModel.uiState.value.unsavedChanges)
            assertEquals(0, countRepository.storedTask.count)
            countRepository.hasSaveFailure = false
            viewModel.dispatch(CountAction.RetrySave)
            runCurrent()
            assertEquals(1, viewModel.uiState.value.task!!.count)
            assertEquals(countRepository.storedTask, viewModel.uiState.value.task)
            assertFalse(viewModel.uiState.value.unsavedChanges)
        }

    @Test
    fun duplicateConfirmIsIgnoredDuringDurableSave() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            countRepository.saveGate = CompletableDeferred()
            viewModel.dispatch(CountAction.Confirm)
            viewModel.dispatch(CountAction.Confirm)
            runCurrent()
            assertFalse(viewModel.uiState.value.task!!.confirmed)
            assertEquals(1, countRepository.saveCount)
            countRepository.saveGate!!.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value.task!!.confirmed)
        }

    @Test
    fun cancelledLateAnalysisCannotOverwriteManualEdits() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            countRepository.analysisResultGate = CompletableDeferred()
            viewModel.dispatch(CountAction.Analyze)
            runCurrent()
            viewModel.dispatch(CountAction.CancelAnalysis)
            viewModel.dispatch(CountAction.Add(.5f, .5f))
            runCurrent()
            countRepository.analysisResultGate!!.complete(
                Detection(listOf(Marker("stale", .2f, .2f, .03f)), 100)
            )
            runCurrent()
            assertEquals(1, viewModel.uiState.value.task!!.count)
            assertTrue(viewModel.uiState.value.task!!.markers.single().manual)
            assertEquals(1, countRepository.saveCount)
        }

    @Test
    fun zeroDetectionsRequireExplicitConfirmation() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            viewModel.dispatch(CountAction.Analyze)
            runCurrent()
            assertEquals(0, viewModel.uiState.value.task!!.count)
            assertFalse(viewModel.uiState.value.task!!.confirmed)
            assertTrue(viewModel.uiState.value.message!!.contains("검출된 파이프가 없습니다"))
        }

    @Test
    fun failedConfirmationDoesNotExposeConfirmedStateUntilRetrySucceeds() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            countRepository.hasSaveFailure = true
            viewModel.dispatch(CountAction.Confirm)
            runCurrent()
            assertFalse(viewModel.uiState.value.task!!.confirmed)
            assertTrue(viewModel.uiState.value.unsavedChanges)
            countRepository.hasSaveFailure = false
            viewModel.dispatch(CountAction.RetrySave)
            runCurrent()
            assertTrue(viewModel.uiState.value.task!!.confirmed)
            assertFalse(viewModel.uiState.value.unsavedChanges)
        }

    @Test
    fun missingRouteIdExposesRecoveryInsteadOfHomeOrAnotherTask() =
        runTest(dispatcher) {
            val viewModel =
                CountViewModel(FakeRepository(), SavedStateHandle(mapOf("taskId" to "missing")))
            runCurrent()
            assertTrue(viewModel.uiState.value.unavailable)
            assertNull(viewModel.uiState.value.task)
            assertTrue(viewModel.uiState.value.message!!.contains("기록을 찾을 수 없습니다"))
        }

    @Test
    fun previewFailureKeepsLoadedTaskAndItsMarkers() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            countRepository.storedTask =
                countRepository.storedTask.edit(listOf(Marker("m", .5f, .5f, .02f, true)))
            countRepository.hasPreviewFailure = true
            val viewModel = open(countRepository)
            assertEquals(countRepository.storedTask, viewModel.uiState.value.task)
            assertFalse(viewModel.uiState.value.unavailable)
            assertEquals("사진 읽기 실패", viewModel.uiState.value.message)
        }

    @Test
    fun incompatiblePendingSaveKeepsPersistedTaskPreviewAndClearsFailedRestore() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            countRepository.storedTask =
                countRepository.storedTask.edit(listOf(Marker("m", .5f, .5f, .02f, true)))
            val savedStateHandle =
                SavedStateHandle(mapOf("taskId" to "task", "pendingSave" to """{"id":"task"}"""))
            val viewModel = CountViewModel(countRepository, savedStateHandle)
            runCurrent()

            assertEquals(countRepository.storedTask, countRepository.previewedTask)
            assertEquals(countRepository.storedTask, viewModel.uiState.value.task)
            assertEquals("저장되지 않은 변경을 복원하지 못했습니다.", viewModel.uiState.value.message)
            assertNull(savedStateHandle.get<String>("pendingSave"))
            assertFalse(viewModel.uiState.value.unsavedChanges)
            assertEquals(Operation.NONE, viewModel.uiState.value.operation)
            assertEquals(0, countRepository.saveCount)

            countRepository.previewedTask = null
            val reopened = CountViewModel(countRepository, savedStateHandle)
            runCurrent()
            assertEquals(countRepository.storedTask, countRepository.previewedTask)
            assertNull(reopened.uiState.value.message)
        }

    @Test
    fun failedEditRestoresFromSavedStateAndRetriesWithoutAddingAgain() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val savedStateHandle = SavedStateHandle(mapOf("taskId" to "task"))
            val viewModel = CountViewModel(countRepository, savedStateHandle)
            runCurrent()
            countRepository.hasSaveFailure = true
            viewModel.dispatch(CountAction.Add(.5f, .5f))
            runCurrent()
            val restored =
                CountViewModel(
                    countRepository,
                    SavedStateHandle(
                        mapOf(
                            "taskId" to "task",
                            "pendingSave" to savedStateHandle.get<String>("pendingSave"),
                        )
                    ),
                )
            runCurrent()
            assertEquals(1, restored.uiState.value.task!!.count)
            assertTrue(restored.uiState.value.unsavedChanges)
            countRepository.hasSaveFailure = false
            restored.dispatch(CountAction.RetrySave)
            runCurrent()
            assertEquals(1, countRepository.storedTask.count)
            assertFalse(restored.uiState.value.unsavedChanges)
        }

    @Test
    fun deletionFailureDoesNotSignalNavigationSuccess() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            val viewModel = open(countRepository)
            countRepository.hasDeleteFailure = true
            viewModel.dispatch(CountAction.Delete)
            runCurrent()
            assertFalse(viewModel.uiState.value.deleted)
            assertNotNull(viewModel.uiState.value.task)
            countRepository.hasDeleteFailure = false
            viewModel.dispatch(CountAction.Delete)
            runCurrent()
            assertTrue(viewModel.uiState.value.deleted)
        }

    @Test
    fun confirmedRecordRejectsEditing() =
        runTest(dispatcher) {
            val countRepository = FakeRepository()
            countRepository.storedTask = countRepository.storedTask.confirm(2L)
            val viewModel = open(countRepository)
            viewModel.dispatch(CountAction.Add(.5f, .5f))
            runCurrent()
            assertEquals(0, countRepository.saveCount)
            assertTrue(viewModel.uiState.value.task!!.confirmed)
        }

    @Test
    fun validationGateReturnsErrorWithoutOverwritingWork() =
        runTest(dispatcher) {
            assertFalse(DETECTOR_VALIDATED)
            try {
                ValidationRequiredDetector().detect(FakeRepository().storedTask)
                fail("gate must reject")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("검증 대기"))
            }
        }
}
