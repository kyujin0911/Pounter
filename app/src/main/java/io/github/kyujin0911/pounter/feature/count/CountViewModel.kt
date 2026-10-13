package io.github.kyujin0911.pounter.feature.count

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.data.detector.AnalysisStage
import io.github.kyujin0911.pounter.data.detector.DetectionFailure
import io.github.kyujin0911.pounter.data.detector.DetectionFailureReason
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.feature.shared.PhotoImportHandler
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class CountViewModel(
    private val countRepository: CountRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CountUiState())
    val uiState: StateFlow<CountUiState> = _uiState.asStateFlow()
    private var analysisJob: Job? = null
    private var analysisGeneration = 0L
    private var taskPendingSave: CountTask? = null

    val photoImport =
        PhotoImportHandler(countRepository, savedStateHandle, viewModelScope) {
            !uiState.value.isBusy && !uiState.value.unsavedChanges
        }

    init {
        viewModelScope.launch {
            try {
                val taskId = savedStateHandle.get<String>("taskId")
                if (taskId != null) {
                    val task = countRepository.get(taskId)
                    _uiState.update {
                        it.copy(
                            task = task,
                            unavailable = task == null,
                            message = if (task == null) "기록을 찾을 수 없습니다. 삭제된 작업일 수 있습니다." else null,
                        )
                    }
                    val restoredPendingTask =
                        savedStateHandle.get<String>("pendingSave")?.let { encoded ->
                            runCatching { Json.decodeFromString<CountTask>(encoded) }
                                .onFailure {
                                    savedStateHandle["pendingSave"] = null
                                    _uiState.update { it.copy(message = "저장되지 않은 변경을 복원하지 못했습니다.") }
                                }
                                .getOrNull()
                        }
                    if (task != null && restoredPendingTask?.id == task.id) {
                        val alreadyStored =
                            restoredPendingTask.copy(
                                revision = task.revision,
                                updatedAt = task.updatedAt,
                            ) == task
                        if (alreadyStored) savedStateHandle["pendingSave"] = null
                        else {
                            taskPendingSave = restoredPendingTask
                            _uiState.update {
                                it.copy(
                                    task =
                                        if (restoredPendingTask.confirmed) task
                                        else restoredPendingTask,
                                    unsavedChanges = true,
                                    message = "저장되지 않은 변경을 복원했습니다. 저장을 재시도해주세요.",
                                )
                            }
                        }
                    }
                    val preview = task?.let { countRepository.preview(it) }
                    _uiState.update { it.copy(preview = preview) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.message ?: "작업 복원에 실패했습니다.") }
            } finally {
                _uiState.update { it.copy(operation = Operation.NONE) }
            }
        }
    }

    fun dispatch(action: CountAction) {
        if (action == CountAction.ClearMessage) {
            _uiState.update { it.copy(message = null) }
            return
        }
        if (action == CountAction.CancelAnalysis) {
            if (_uiState.value.operation == Operation.ANALYZING) {
                analysisGeneration++
                analysisJob?.cancel()
                analysisJob = null
                _uiState.update {
                    it.copy(
                        operation = Operation.NONE,
                        analysisWaiting = false,
                        message = "분석을 취소했습니다. 사진과 이전 표시를 유지합니다.",
                    )
                }
            }
            return
        }
        if (_uiState.value.isBusy || photoImport.uiState.value.isImporting) return
        if (_uiState.value.unsavedChanges && action != CountAction.RetrySave) return
        when (action) {
            CountAction.CancelAnalysis,
            CountAction.ClearMessage -> Unit
            CountAction.RetrySave -> taskPendingSave?.let { persist(it) }
            CountAction.Analyze -> startAnalysis()
            CountAction.BeginManual ->
                modify { task -> task.copy(analyzed = true, analysisMillis = null) }
            is CountAction.SetRegion -> modify { it.reset(action.region) }
            is CountAction.Add ->
                modify { task ->
                    require(task.analyzed) { "먼저 수동 표시를 시작해주세요." }
                    require(task.region.contains(action.normalizedX, action.normalizedY)) {
                        "계수 영역 안쪽을 선택해주세요."
                    }
                    require(task.count < 300) { "이 버전은 최대 300개 표시를 지원합니다." }
                    task.edit(
                        task.markers +
                            Marker(
                                UUID.randomUUID().toString(),
                                action.normalizedX,
                                action.normalizedY,
                                0.015f,
                                true,
                            )
                    )
                }
            is CountAction.Remove ->
                modify { task -> task.edit(task.markers.filterNot { it.id in action.markerIds }) }
            CountAction.Undo -> modify { it.undoEdit() }
            CountAction.Confirm -> modify { it.confirm(System.currentTimeMillis()) }
            CountAction.Delete -> {
                val task = _uiState.value.task ?: return
                runOperation(Operation.SAVING) {
                    countRepository.delete(task)
                    _uiState.update { it.copy(deleted = true, message = "기록을 삭제했습니다.") }
                }
            }
        }
    }

    private fun modify(transform: (CountTask) -> CountTask) {
        val task = _uiState.value.task ?: return
        if (task.confirmed) return
        try {
            val editedTask = transform(task)
            if (editedTask == task) return
            // Confirmation is visible only after durable success; edits remain visible on failure.
            if (!editedTask.confirmed) _uiState.update { it.copy(task = editedTask) }
            persist(editedTask)
        } catch (failure: Exception) {
            _uiState.update { it.copy(message = failure.message ?: "변경할 수 없습니다.") }
        }
    }

    private fun persist(editedTask: CountTask, successMessage: String? = null) {
        taskPendingSave = editedTask
        savedStateHandle["pendingSave"] = Json.encodeToString(editedTask)
        _uiState.update { it.copy(unsavedChanges = true) }
        runOperation(Operation.SAVING) {
            val savedTask = countRepository.save(editedTask)
            taskPendingSave = null
            savedStateHandle["pendingSave"] = null
            _uiState.update {
                it.copy(task = savedTask, message = successMessage, unsavedChanges = false)
            }
        }
    }

    private fun startAnalysis() {
        val task = _uiState.value.task ?: return
        if (task.confirmed) return
        val resultGeneration = ++analysisGeneration
        _uiState.update {
            it.copy(operation = Operation.ANALYZING, analysisWaiting = false, message = null)
        }
        analysisJob =
            viewModelScope.launch {
                try {
                    val detection =
                        countRepository.detect(task) { stage ->
                            viewModelScope.launch {
                                if (
                                    resultGeneration == analysisGeneration &&
                                        _uiState.value.operation == Operation.ANALYZING &&
                                        sameAnalysisSnapshot(task, _uiState.value.task)
                                )
                                    _uiState.update {
                                        it.copy(analysisWaiting = stage == AnalysisStage.WAITING)
                                    }
                            }
                        }
                    ensureActive()
                    if (
                        resultGeneration != analysisGeneration ||
                            !sameAnalysisSnapshot(task, _uiState.value.task)
                    )
                        return@launch
                    val editedTask =
                        task.copy(
                            markers = detection.markers,
                            automatic = detection.markers,
                            undo = null,
                            analyzed = true,
                            analysisMillis = detection.millis,
                            analysisMetadata = detection.analysisMetadata,
                        )
                    _uiState.update { it.copy(task = editedTask, analysisWaiting = false) }
                    persist(
                        editedTask,
                        if (editedTask.count == 0) "검출된 파이프가 없습니다. 촬영 조건과 영역을 확인하거나 수동으로 표시해주세요."
                        else null,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    if (resultGeneration == analysisGeneration)
                        _uiState.update { it.copy(message = analysisFailureMessage(failure)) }
                } finally {
                    if (resultGeneration == analysisGeneration)
                        _uiState.update {
                            it.copy(
                                operation =
                                    if (it.operation == Operation.ANALYZING) Operation.NONE
                                    else it.operation,
                                analysisWaiting = false,
                            )
                        }
                }
            }
    }

    private fun sameAnalysisSnapshot(start: CountTask, current: CountTask?): Boolean =
        current != null &&
            start.id == current.id &&
            start.revision == current.revision &&
            start.photoName == current.photoName &&
            start.region == current.region &&
            start.width == current.width &&
            start.height == current.height

    private fun analysisFailureMessage(failure: Exception): String =
        if (failure is DetectionFailure)
            when (failure.reason) {
                DetectionFailureReason.PHOTO_READ -> "사진을 읽을 수 없습니다. 다시 선택해주세요."
                DetectionFailureReason.INITIALIZATION -> "검출 라이브러리를 시작하지 못했습니다. 다시 시도해주세요."
                DetectionFailureReason.INVALID_INPUT -> "분석할 사진 크기나 영역을 확인해주세요. 사진을 다시 선택할 수 있습니다."
                DetectionFailureReason.PROCESSING -> "사진 분석에 실패했습니다. 재시도하거나 사진을 교체해주세요."
                DetectionFailureReason.CANDIDATE_SATURATION ->
                    "분석 후보가 처리 상한에 도달했습니다. 영역과 촬영 조건을 확인해주세요."
                DetectionFailureReason.COUNT_LIMIT -> "300개를 초과하는 단면 후보입니다. 영역과 촬영 조건을 확인해주세요."
            }
        else failure.message ?: "분석에 실패했습니다. 재시도해주세요."

    private fun runOperation(operation: Operation, block: suspend () -> Unit) {
        _uiState.update { it.copy(operation = operation) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.message ?: "처리에 실패했습니다. 다시 시도해주세요.") }
            } finally {
                _uiState.update { it.copy(operation = Operation.NONE) }
            }
        }
    }
}
