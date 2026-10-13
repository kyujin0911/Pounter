package io.github.kyujin0911.pounter.feature.count

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Region
import io.github.kyujin0911.pounter.core.ui.components.ConfirmDialog
import io.github.kyujin0911.pounter.core.ui.components.MessageCard
import io.github.kyujin0911.pounter.core.ui.components.ProgressCard
import io.github.kyujin0911.pounter.feature.shared.CountSummary
import io.github.kyujin0911.pounter.feature.shared.DetailPhotoActions
import io.github.kyujin0911.pounter.feature.shared.EditMode
import io.github.kyujin0911.pounter.feature.shared.PhotoImportUiState
import io.github.kyujin0911.pounter.feature.shared.PhotoPanel
import io.github.kyujin0911.pounter.feature.shared.TaskHeader
import io.github.kyujin0911.pounter.feature.shared.ValidationNotice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountWorkScreen(
    state: CountUiState,
    dispatch: (CountAction) -> Unit,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    inputError: String? = null,
    onClearInputError: () -> Unit = {},
    onBack: () -> Unit = {},
    photoImportState: PhotoImportUiState = PhotoImportUiState(),
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(EditMode.VIEW) }
    var showNumbers by rememberSaveable { mutableStateOf(true) }
    val task = state.task
    val isBusy = state.isBusy || photoImportState.isImporting
    val canEdit = !isBusy && !state.unsavedChanges
    fun goBack() {
        when {
            state.operation == Operation.ANALYZING -> dispatch(CountAction.CancelAnalysis)
            isBusy -> Unit
            state.unsavedChanges -> dialog = "unsaved"
            else -> onBack()
        }
    }
    // NavHost owns ordinary Back. Intercept only operations that must not lose work.
    BackHandler(isBusy || state.unsavedChanges) { goBack() }
    val message = inputError ?: photoImportState.message ?: state.message
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        LazyColumn(
            Modifier.fillMaxSize().padding(insets).imePadding().testTag("content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TaskHeader(task, !isBusy || state.operation == Operation.ANALYZING, ::goBack) }
            if (isBusy)
                item {
                    ProgressCard(
                        when (state.operation) {
                            Operation.ANALYZING -> "분석 중"
                            Operation.SAVING -> "변경 사항 저장 중"
                            else -> "사진과 기록을 불러오는 중"
                        },
                        onCancel =
                            if (state.operation == Operation.ANALYZING) {
                                { dispatch(CountAction.CancelAnalysis) }
                            } else null,
                    )
                }
            if (state.unsavedChanges && !isBusy)
                item {
                    Text("저장되지 않은 변경이 있습니다. 저장을 재시도한 뒤 이동해주세요.")
                    Button(onClick = { dispatch(CountAction.RetrySave) }) { Text("저장 재시도") }
                }
            if (message != null)
                item {
                    MessageCard(
                        message,
                        onDismiss = {
                            dispatch(CountAction.ClearMessage)
                            onClearInputError()
                        },
                    )
                }
            if (task == null) {
                if (!isBusy)
                    item {
                        Text(state.message ?: "작업을 표시할 수 없습니다.")
                        Button(onClick = onBack) { Text("목록으로 돌아가기") }
                    }
            } else {
                item { CountSummary(task) }
                item {
                    PhotoPanel(
                        task = task,
                        bitmap = state.preview,
                        mode = mode,
                        showNumbers = showNumbers,
                        canEdit = canEdit && !task.confirmed && task.analyzed,
                        isLoading = isBusy,
                        onAddMarker = { normalizedX, normalizedY ->
                            dispatch(CountAction.Add(normalizedX, normalizedY))
                        },
                        onRemoveMarkers = { markerIds -> dispatch(CountAction.Remove(markerIds)) },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("번호 표시", Modifier.weight(1f))
                        Switch(checked = showNumbers, onCheckedChange = { showNumbers = it })
                    }
                }
                if (!task.confirmed) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { dialog = "region" }, enabled = canEdit) {
                                Text("계수 영역")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (task.markers.isNotEmpty()) dialog = "reanalyze"
                                    else dispatch(CountAction.Analyze)
                                },
                                enabled = canEdit,
                            ) {
                                Text("자동 계수 상태")
                            }
                            if (!task.analyzed)
                                Button(
                                    onClick = {
                                        dispatch(CountAction.BeginManual)
                                        mode = EditMode.ADD
                                    },
                                    enabled = canEdit,
                                ) {
                                    Text("수동 표시 시작")
                                }
                        }
                    }
                    item { ValidationNotice() }
                    if (task.analyzed) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "표시 보정",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = mode == EditMode.VIEW,
                                        onClick = { mode = EditMode.VIEW },
                                        label = { Text("이동") },
                                        enabled = canEdit,
                                    )
                                    FilterChip(
                                        selected = mode == EditMode.ADD,
                                        onClick = { mode = EditMode.ADD },
                                        label = { Text("추가") },
                                        enabled = canEdit,
                                    )
                                    FilterChip(
                                        selected = mode == EditMode.DELETE,
                                        onClick = { mode = EditMode.DELETE },
                                        label = { Text("삭제") },
                                        enabled = canEdit,
                                    )
                                    OutlinedButton(
                                        onClick = { dispatch(CountAction.Undo) },
                                        enabled = canEdit && task.undo != null,
                                    ) {
                                        Text("되돌리기")
                                    }
                                }
                                Text(
                                    when (mode) {
                                        EditMode.ADD ->
                                            "단면 중심을 눌러 표시를 추가하세요. 원형은 자동, 사각형은 수동 표시입니다."
                                        EditMode.DELETE -> "삭제할 표시를 누르세요. 겹친 표시는 목록에서 선택할 수 있습니다."
                                        else -> "두 손가락으로 확대하고 사진을 이동할 수 있습니다."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        item {
                            Button(
                                onClick = { dialog = "confirm" },
                                enabled = canEdit,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) {
                                Text("${task.count}개 확인하고 확정")
                            }
                        }
                    }
                }
                item { DetailPhotoActions(canEdit, onPick, onCamera) { dialog = "delete" } }
            }
        }
    }
    if (dialog == "unsaved") {
        ConfirmDialog(
            "저장되지 않은 변경",
            "현재 변경을 유지하고 있습니다. 저장을 재시도한 뒤 목록으로 이동해주세요.",
            "저장 재시도",
            { dialog = null },
        ) {
            dialog = null
            dispatch(CountAction.RetrySave)
        }
    }
    if (task != null && dialog != null) {
        when (dialog) {
            "region" ->
                RegionDialog(
                    task,
                    onDismiss = { dialog = null },
                    onApply = { region ->
                        dispatch(CountAction.SetRegion(region))
                        mode = EditMode.VIEW
                        dialog = null
                    },
                )
            "confirm" ->
                ConfirmDialog(
                    "개수 확정",
                    "사진의 모든 단면과 표시를 확인했나요? ${task.count}개로 저장하며 확정된 기록은 수정할 수 없습니다.",
                    "확정 저장",
                    { dialog = null },
                ) {
                    dispatch(CountAction.Confirm)
                    mode = EditMode.VIEW
                    dialog = null
                }
            "delete" ->
                ConfirmDialog(
                    "기록 삭제",
                    "앱 내부 사진과 표시가 삭제됩니다. 원본 갤러리 사진은 유지되며 이 삭제는 되돌릴 수 없습니다.",
                    "삭제",
                    { dialog = null },
                ) {
                    dispatch(CountAction.Delete)
                    dialog = null
                }
            "reanalyze" ->
                ConfirmDialog(
                    "자동 계수 상태",
                    "자동 계수는 정면 사진 검증 대기 중입니다. 현재 수동 표시는 유지됩니다.",
                    "확인",
                    { dialog = null },
                ) {
                    dispatch(CountAction.Analyze)
                    dialog = null
                }
        }
    }
}

@Composable
private fun RegionDialog(task: CountTask, onDismiss: () -> Unit, onApply: (Region) -> Unit) {
    var selectedRegion by remember { mutableStateOf(task.region) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("계수 영역") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("사진 전체를 0~100%로 표시합니다. 영역을 바꾸면 기존 표시와 보정이 초기화됩니다.")
                RegionSlider("왼쪽", selectedRegion.left, 0f..(selectedRegion.right - .05f)) {
                    selectedRegion = selectedRegion.copy(left = it)
                }
                RegionSlider("위쪽", selectedRegion.top, 0f..(selectedRegion.bottom - .05f)) {
                    selectedRegion = selectedRegion.copy(top = it)
                }
                RegionSlider("오른쪽", selectedRegion.right, (selectedRegion.left + .05f)..1f) {
                    selectedRegion = selectedRegion.copy(right = it)
                }
                RegionSlider("아래쪽", selectedRegion.bottom, (selectedRegion.top + .05f)..1f) {
                    selectedRegion = selectedRegion.copy(bottom = it)
                }
                TextButton(onClick = { selectedRegion = Region() }) { Text("사진 전체로 초기화") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedRegion) }) {
                Text(if (task.markers.isEmpty()) "영역 적용" else "표시 초기화하고 적용")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun RegionSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text("$label ${kotlin.math.round(value * 100).toInt()}%")
    Slider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        valueRange = range,
        modifier = Modifier.semantics { contentDescription = "계수 영역 $label" },
    )
}
