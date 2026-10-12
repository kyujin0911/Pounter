package io.github.kyujin0911.pounter.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kyujin0911.pounter.data.CountRepository
import io.github.kyujin0911.pounter.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlinx.serialization.json.Json

sealed interface CountAction {
    data class Import(val uri: Uri): CountAction
    data object ImportHandled: CountAction
    data object RetrySave: CountAction
    data object Analyze: CountAction
    data object CancelAnalysis: CountAction
    data object BeginManual: CountAction
    data class SetRegion(val region: Region): CountAction
    data class Add(val x: Float,val y: Float): CountAction
    data class Remove(val ids: Set<String>): CountAction
    data object Undo: CountAction
    data object Confirm: CountAction
    data object Delete: CountAction
    data object ClearMessage: CountAction
}
enum class Operation { NONE, LOADING, ANALYZING, SAVING }
data class CountUiState(
    val records: List<CountTask> = emptyList(), val task: CountTask? = null,
    val preview: Bitmap? = null, val operation: Operation = Operation.LOADING,
    val message: String? = null,
    val importedTaskId: String? = null, val unavailable: Boolean = false,
    val deleted: Boolean = false, val unsavedChanges: Boolean = false,
) { val busy: Boolean get() = operation != Operation.NONE }

class CountViewModel(private val repository: CountRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable=MutableStateFlow(CountUiState())
    val uiState: StateFlow<CountUiState> = mutable.asStateFlow()
    private var analysis: Job? = null
    private var generation=0L
    private var pendingSave: CountTask? = null
    init {
        viewModelScope.launch {
            repository.tasks.catch { e -> mutable.update { it.copy(message=e.message ?: "기록을 읽을 수 없습니다.") } }
                .collect { records -> mutable.update { it.copy(records=records) } }
        }
        viewModelScope.launch {
            try {
                mutable.update { it.copy(importedTaskId=saved["importedTaskId"]) }
                val id=saved.get<String>("taskId")
                if(id!=null) {
                    val task=repository.get(id)
                    mutable.update { it.copy(task=task,unavailable=task==null,
                        message=if(task==null) "기록을 찾을 수 없습니다. 삭제된 작업일 수 있습니다." else null) }
                    val pending=saved.get<String>("pendingSave")?.let { Json.decodeFromString<CountTask>(it) }
                    if(task!=null && pending?.id==task.id) {
                        val alreadyStored=pending.copy(revision=task.revision,updatedAt=task.updatedAt)==task
                        if(alreadyStored) saved["pendingSave"]=null
                        else {
                            pendingSave=pending
                            mutable.update { it.copy(task=if(pending.confirmed) task else pending,unsavedChanges=true,
                                message="저장되지 않은 변경을 복원했습니다. 저장을 재시도해주세요.") }
                        }
                    }
                    val preview=task?.let { repository.preview(it) }
                    mutable.update { it.copy(preview=preview) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(message=e.message ?: "작업 복원에 실패했습니다.") } }
            finally { mutable.update { it.copy(operation=Operation.NONE) } }
        }
    }
    fun dispatch(action: CountAction) {
        if(action==CountAction.ClearMessage) { mutable.update { it.copy(message=null) }; return }
        if(action==CountAction.CancelAnalysis) {
            if(mutable.value.operation==Operation.ANALYZING) {
                generation++; analysis?.cancel(); analysis=null
                mutable.update { it.copy(operation=Operation.NONE,message="분석을 취소했습니다. 사진과 이전 표시를 유지합니다.") }
            }
            return
        }
        if(mutable.value.busy) return
        if(mutable.value.unsavedChanges && action!=CountAction.RetrySave && action!=CountAction.ImportHandled) return
        when(action) {
            CountAction.ImportHandled -> {
                saved["importedTaskId"]=null
                mutable.update { it.copy(importedTaskId=null) }
            }
            CountAction.RetrySave -> pendingSave?.let { persist(it) }
            is CountAction.Import -> runOperation(Operation.LOADING) {
                val task=repository.import(action.uri)
                saved["importedTaskId"]=task.id
                mutable.update { it.copy(importedTaskId=task.id,message=null) }
            }
            CountAction.Analyze -> startAnalysis()
            CountAction.BeginManual -> modify { task ->
                task.copy(analyzed=true,analysisMillis=null)
            }
            is CountAction.SetRegion -> modify { it.reset(action.region) }
            is CountAction.Add -> modify { task ->
                require(task.analyzed) { "먼저 수동 표시를 시작해주세요." }
                require(task.region.contains(action.x,action.y)) { "계수 영역 안쪽을 선택해주세요." }
                require(task.count<300) { "이 버전은 최대 300개 표시를 지원합니다." }
                task.edit(task.markers + Marker(UUID.randomUUID().toString(),action.x,action.y,0.015f,true))
            }
            is CountAction.Remove -> modify { task -> task.edit(task.markers.filterNot { it.id in action.ids }) }
            CountAction.Undo -> modify { it.undoEdit() }
            CountAction.Confirm -> modify { it.confirm(System.currentTimeMillis()) }
            CountAction.Delete -> {
                val task=mutable.value.task ?: return
                runOperation(Operation.SAVING) {
                    repository.delete(task)
                    mutable.update { it.copy(deleted=true,message="기록을 삭제했습니다.") }
                }
            }
            else -> Unit
        }
    }
    private fun modify(transform: (CountTask)->CountTask) {
        val task=mutable.value.task ?: return
        if(task.confirmed) return
        try {
            val next=transform(task)
            if(next==task) return
            // Confirmation is visible only after durable success; edits remain visible on failure.
            if(!next.confirmed) mutable.update { it.copy(task=next) }
            persist(next)
        } catch(e: Exception) { mutable.update { it.copy(message=e.message ?: "변경할 수 없습니다.") } }
    }
    private fun persist(next: CountTask) {
        pendingSave=next
        saved["pendingSave"]=Json.encodeToString(next)
        mutable.update { it.copy(unsavedChanges=true) }
        runOperation(Operation.SAVING) {
            val stored=repository.save(next)
            pendingSave=null
            saved["pendingSave"]=null
            mutable.update { it.copy(task=stored,message=null,unsavedChanges=false) }
        }
    }
    private fun startAnalysis() {
        val task=mutable.value.task ?: return
        if(task.confirmed) return
        val token=++generation
        mutable.update { it.copy(operation=Operation.ANALYZING,message=null) }
        analysis=viewModelScope.launch {
            try {
                val result=repository.detect(task)
                ensureActive()
                if(token!=generation || mutable.value.task?.id!=task.id) return@launch
                // Once commit starts, cancellation is no longer offered; preserve durable result.
                mutable.update { it.copy(operation=Operation.SAVING) }
                val next=repository.save(task.copy(markers=result.markers,automatic=result.markers,undo=null,analyzed=true,analysisMillis=result.millis))
                mutable.update { it.copy(task=next,message=if(next.count==0) "검출된 파이프가 없습니다. 촬영 조건과 영역을 확인하거나 수동으로 표시해주세요." else null) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(token==generation) mutable.update { it.copy(message=e.message ?: "분석에 실패했습니다. 재시도해주세요.") } }
            finally { if(token==generation) mutable.update { it.copy(operation=Operation.NONE) } }
        }
    }
    private fun runOperation(operation: Operation, block: suspend ()->Unit) {
        mutable.update { it.copy(operation=operation) }
        viewModelScope.launch {
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(message=e.message ?: "처리에 실패했습니다. 다시 시도해주세요.") } }
            finally { mutable.update { it.copy(operation=Operation.NONE) } }
        }
    }
}
