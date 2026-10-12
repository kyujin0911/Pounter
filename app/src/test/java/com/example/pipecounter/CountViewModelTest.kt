package com.example.pipecounter

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.pipecounter.data.*
import com.example.pipecounter.model.*
import com.example.pipecounter.ui.*
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
        var delayResult: CompletableDeferred<Detection>? = null
        var delayedSave: CompletableDeferred<Unit>? = null
        override suspend fun get(id: String)=stored.takeIf {it.id==id}
        override suspend fun preview(task: CountTask): Bitmap? = null
        override suspend fun import(uri: Uri): CountTask = error("import failed")
        override suspend fun save(task: CountTask): CountTask {
            saves++
            delayedSave?.await()
            if(fail) error("저장 공간 부족")
            check(task.revision==stored.revision)
            stored=task.copy(revision=task.revision+1)
            tasks.value=listOf(stored)
            return stored
        }
        override suspend fun delete(task: CountTask) {tasks.value=emptyList()}
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
    @Test fun failedSaveKeepsPreviousMarkersAndAllowsRetry()=runTest(dispatcher) {
        val repo=FakeRepository();val vm=open(repo)
        repo.fail=true;vm.dispatch(CountAction.Add(.5f,.5f));runCurrent()
        assertEquals(0,vm.uiState.value.task!!.count);assertEquals("저장 공간 부족",vm.uiState.value.message)
        repo.fail=false;vm.dispatch(CountAction.Add(.5f,.5f));runCurrent()
        assertEquals(1,vm.uiState.value.task!!.count);assertEquals(repo.stored,vm.uiState.value.task)
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
    @Test fun validationGateReturnsErrorWithoutOverwritingWork()=runTest(dispatcher) {
        assertFalse(DETECTOR_VALIDATED)
        try {ValidationRequiredDetector().detect(FakeRepository().stored);fail("gate must reject")}
        catch(e: IllegalStateException) {assertTrue(e.message!!.contains("검증 대기"))}
    }
}
