package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import io.github.kyujin0911.pounter.data.*
import io.github.kyujin0911.pounter.model.*
import io.github.kyujin0911.pounter.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CountViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup() {Dispatchers.setMain(dispatcher)}
    @After fun cleanup() {Dispatchers.resetMain()}
    private class FakeRepository : CountRepository {
        var stored=CountTask("task","photo.jpg",1000,1500,1L,analyzed=true)
        override val tasks=MutableStateFlow(listOf(stored))
        var fail=false
        var saves=0
        var failDelete=false
        var failPreview=false
        var imported: CountTask?=null
        var delayResult: CompletableDeferred<Detection>? = null
        var delayedSave: CompletableDeferred<Unit>? = null
        override suspend fun get(id: String)=stored.takeIf {it.id==id}
        override suspend fun preview(task: CountTask): Bitmap? { if(failPreview) error("사진 읽기 실패"); return null }
        override suspend fun import(uri: Uri): CountTask = imported ?: error("import failed")
        override suspend fun save(task: CountTask): CountTask {
            saves++
            delayedSave?.await()
            if(fail) error("저장 공간 부족")
            check(task.revision==stored.revision)
            stored=task.copy(revision=task.revision+1)
            tasks.value=listOf(stored)
            return stored
        }
        override suspend fun delete(task: CountTask) {if(failDelete) error("삭제 실패");tasks.value=emptyList()}
        override suspend fun detect(task: CountTask): Detection {
            val deferred=delayResult ?: return Detection(emptyList(),1L)
            return withContext(NonCancellable) {deferred.await()}
        }
    }
    private suspend fun TestScope.open(repo: FakeRepository): CountViewModel {
        val vm=CountViewModel(repo,SavedStateHandle(mapOf("taskId" to "task")))
        runCurrent();return vm
    }
    @Test fun restoresPersistedTaskAndDoesNotPretendAnalysisIsRunning()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        assertEquals(repo.stored,vm.uiState.value.task);assertEquals(Operation.NONE,vm.uiState.value.operation)
    }
    @Test fun failedSaveKeepsUnsavedMarkersAndRetriesTheSameChange()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        repo.fail=true;vm.dispatch(CountAction.Add(.5f,.5f));runCurrent()
        assertEquals(1,vm.uiState.value.task!!.count);assertEquals("저장 공간 부족",vm.uiState.value.message)
        assertTrue(vm.uiState.value.unsavedChanges);assertEquals(0,repo.stored.count)
        repo.fail=false;vm.dispatch(CountAction.RetrySave);runCurrent()
        assertEquals(1,vm.uiState.value.task!!.count);assertEquals(repo.stored,vm.uiState.value.task)
        assertFalse(vm.uiState.value.unsavedChanges)
    }
    @Test fun duplicateConfirmIsIgnoredDuringDurableSave()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo);repo.delayedSave=CompletableDeferred()
        vm.dispatch(CountAction.Confirm);vm.dispatch(CountAction.Confirm);runCurrent()
        assertFalse(vm.uiState.value.task!!.confirmed);assertEquals(1,repo.saves)
        repo.delayedSave!!.complete(Unit);runCurrent()
        assertTrue(vm.uiState.value.task!!.confirmed)
    }
    @Test fun cancelledLateAnalysisCannotOverwriteManualEdits()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        repo.delayResult=CompletableDeferred()
        vm.dispatch(CountAction.Analyze);runCurrent()
        vm.dispatch(CountAction.CancelAnalysis)
        vm.dispatch(CountAction.Add(.5f,.5f));runCurrent()
        repo.delayResult!!.complete(Detection(listOf(Marker("stale",.2f,.2f,.03f)),100));runCurrent()
        assertEquals(1,vm.uiState.value.task!!.count)
        assertTrue(vm.uiState.value.task!!.markers.single().manual)
        assertEquals(1,repo.saves)
    }
    @Test fun zeroDetectionsRequireExplicitConfirmation()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        vm.dispatch(CountAction.Analyze);runCurrent()
        assertEquals(0,vm.uiState.value.task!!.count);assertFalse(vm.uiState.value.task!!.confirmed)
        assertTrue(vm.uiState.value.message!!.contains("검출된 파이프가 없습니다"))
    }
    @Test fun failedConfirmationDoesNotExposeConfirmedStateUntilRetrySucceeds()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        repo.fail=true;vm.dispatch(CountAction.Confirm);runCurrent()
        assertFalse(vm.uiState.value.task!!.confirmed);assertTrue(vm.uiState.value.unsavedChanges)
        repo.fail=false;vm.dispatch(CountAction.RetrySave);runCurrent()
        assertTrue(vm.uiState.value.task!!.confirmed);assertFalse(vm.uiState.value.unsavedChanges)
    }
    @Test fun missingRouteIdExposesRecoveryInsteadOfHomeOrAnotherTask()=runTest(dispatcher) {
        val vm=CountViewModel(FakeRepository(),SavedStateHandle(mapOf("taskId" to "missing")))
        runCurrent()
        assertTrue(vm.uiState.value.unavailable);assertNull(vm.uiState.value.task)
        assertTrue(vm.uiState.value.message!!.contains("기록을 찾을 수 없습니다"))
    }
    @Test fun previewFailureKeepsLoadedTaskAndItsMarkers()=runTest(dispatcher) {
        val repo=FakeRepository();repo.stored=repo.stored.edit(listOf(Marker("m",.5f,.5f,.02f,true)))
        repo.failPreview=true;val vm=open(repo)
        assertEquals(repo.stored,vm.uiState.value.task);assertFalse(vm.uiState.value.unavailable)
        assertEquals("사진 읽기 실패",vm.uiState.value.message)
    }
    @Test fun failedEditRestoresFromSavedStateAndRetriesWithoutAddingAgain()=runTest(dispatcher) {
        val repo=FakeRepository();val saved=SavedStateHandle(mapOf("taskId" to "task"))
        val vm=CountViewModel(repo,saved);runCurrent()
        repo.fail=true;vm.dispatch(CountAction.Add(.5f,.5f));runCurrent()
        val restored=CountViewModel(repo,SavedStateHandle(mapOf("taskId" to "task","pendingSave" to saved.get<String>("pendingSave"))))
        runCurrent();assertEquals(1,restored.uiState.value.task!!.count)
        assertTrue(restored.uiState.value.unsavedChanges)
        repo.fail=false;restored.dispatch(CountAction.RetrySave);runCurrent()
        assertEquals(1,repo.stored.count);assertFalse(restored.uiState.value.unsavedChanges)
    }
    @Test fun deletionFailureDoesNotSignalNavigationSuccess()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        repo.failDelete=true;vm.dispatch(CountAction.Delete);runCurrent()
        assertFalse(vm.uiState.value.deleted);assertNotNull(vm.uiState.value.task)
        repo.failDelete=false;vm.dispatch(CountAction.Delete);runCurrent()
        assertTrue(vm.uiState.value.deleted)
    }
    @Test fun confirmedRecordRejectsEditing()=runTest(dispatcher) {
        val repo=FakeRepository();repo.stored=repo.stored.confirm(2L)
        val vm=open(repo);vm.dispatch(CountAction.Add(.5f,.5f));runCurrent()
        assertEquals(0,repo.saves);assertTrue(vm.uiState.value.task!!.confirmed)
    }
    @Test fun validationGateReturnsErrorWithoutOverwritingWork()=runTest(dispatcher) {
        assertFalse(DETECTOR_VALIDATED)
        try {ValidationRequiredDetector().detect(FakeRepository().stored);fail("gate must reject")}
        catch(e: IllegalStateException) {assertTrue(e.message!!.contains("검증 대기"))}
    }
}
