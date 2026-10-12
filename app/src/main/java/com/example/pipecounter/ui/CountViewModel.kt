package com.example.pipecounter.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pipecounter.data.CountRepository
import com.example.pipecounter.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

sealed interface CountAction {
    data class Import(val uri: Uri): CountAction
    data class Open(val id: String): CountAction
    data object Home: CountAction
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
) { val busy: Boolean get() = operation != Operation.NONE }

class CountViewModel(private val repository: CountRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable=MutableStateFlow(CountUiState())
    val uiState: StateFlow<CountUiState> = mutable.asStateFlow()
    private var analysis: Job? = null
    private var generation=0L
    init {
        viewModelScope.launch {
            repository.tasks.catch { e -> mutable.update { it.copy(message=e.message ?: "기록을 읽을 수 없습니다.") } }
                .collect { records -> mutable.update { it.copy(records=records) } }
        }
        viewModelScope.launch {
            try {
                val id=saved.get<String>("taskId")
                if(id!=null) {
                    val task=repository.get(id)
                    val preview=task?.let { repository.preview(it) }
                    mutable.update { it.copy(task=task,preview=preview) }
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
        when(action) {
            CountAction.Home -> {
                generation++; saved["taskId"]=null
                mutable.update { it.copy(task=null,preview=null,message=null) }
            }
            is CountAction.Open -> runOperation(Operation.LOADING) {
                val task=repository.get(action.id) ?: error("기록을 찾을 수 없습니다.")
                val bitmap=repository.preview(task)
                saved["taskId"]=task.id
                mutable.update { it.copy(task=task,preview=bitmap,message=null) }
            }
            is CountAction.Import -> runOperation(Operation.LOADING) {
                val task=repository.import(action.uri)
                // Persist selection before decoding: an interrupted preview still has a recoverable task.
                saved["taskId"]=task.id
                mutable.update { it.copy(task=task,preview=null,message=null) }
                val bitmap=repository.preview(task)
                mutable.update { it.copy(preview=bitmap) }
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
                    saved["taskId"]=null
                    mutable.update { it.copy(task=null,preview=null,message="기록을 삭제했습니다.") }
                }
            }
            else -> Unit
        }
    }
    private fun modify(transform: (CountTask)->CountTask) {
        val task=mutable.value.task ?: return
        if(task.confirmed) return
        runOperation(Operation.SAVING) {
            val next=transform(task)
            if(next==task) return@runOperation
            val stored=repository.save(next)
            mutable.update { it.copy(task=stored,message=null) }
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
