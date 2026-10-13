package io.github.kyujin0911.pounter.feature.count

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Region
import io.github.kyujin0911.pounter.core.ui.components.ConfirmDialog
import io.github.kyujin0911.pounter.core.ui.components.MessageCard
import io.github.kyujin0911.pounter.core.ui.components.ProgressCard
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions
import io.github.kyujin0911.pounter.feature.shared.*

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
    BackHandler(isBusy || state.unsavedChanges) { goBack() }
    val message = inputError ?: photoImportState.message ?: state.message
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val safeHeight =
            with(density) {
                (WindowInsets.safeDrawing.getTop(this) + WindowInsets.safeDrawing.getBottom(this))
                    .toDp()
            }
        val contentHeightDp = (maxHeight - safeHeight).value.coerceAtLeast(1f)
        val contentWidthDp =
            (minOf(maxWidth, PipeDimensions.readingMaxWidth) - PipeDimensions.screenInset * 2)
                .value
                .coerceAtLeast(1f)
        val pinConfirm = shouldPinConfirmAction(contentHeightDp, density.fontScale)
        val showConfirm = task != null && task.analyzed && !task.confirmed
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (pinConfirm && showConfirm && task != null)
                    Surface {
                        Box(
                            Modifier.fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(PipeDimensions.screenInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            ConfirmAction(
                                task.count,
                                canEdit,
                                state.operation == Operation.SAVING,
                                { dialog = "confirm" },
                                Modifier.widthIn(max = PipeDimensions.readingMaxWidth),
                            )
                        }
                    }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = PipeDimensions.readingMaxWidth)
                        .fillMaxSize()
                        .imePadding()
                        .testTag("content"),
                    contentPadding = PaddingValues(PipeDimensions.screenInset),
                    verticalArrangement = Arrangement.spacedBy(PipeDimensions.groupGap),
                ) {
                    item {
                        TaskHeader(
                            task,
                            !isBusy || state.operation == Operation.ANALYZING,
                            ::goBack,
                        )
                    }
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
                        item { RecoveryNotice("저장되지 않은 변경", { dispatch(CountAction.RetrySave) }) }
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
                        item(key = "summary-${task.id}") { CountSummary(task) }
                        item(key = "photo-${task.id}") {
                            PhotoPanel(
                                task,
                                state.preview,
                                mode,
                                showNumbers,
                                canEdit && !task.confirmed && task.analyzed,
                                isBusy,
                                viewportHeight =
                                    photoViewportHeightDp(
                                            contentHeightDp,
                                            contentWidthDp,
                                            task.width.toFloat() / task.height,
                                        )
                                        .dp,
                                onAddMarker = { x, y -> dispatch(CountAction.Add(x, y)) },
                                onRemoveMarkers = { ids -> dispatch(CountAction.Remove(ids)) },
                            )
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "번호 표시",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Switch(
                                    showNumbers,
                                    { showNumbers = it },
                                    modifier = Modifier.semantics { contentDescription = "번호 표시" },
                                )
                            }
                        }
                        if (!task.confirmed) {
                            item {
                                FlowRow(
                                    horizontalArrangement =
                                        Arrangement.spacedBy(PipeDimensions.controlGap),
                                    verticalArrangement =
                                        Arrangement.spacedBy(PipeDimensions.controlGap),
                                ) {
                                    OutlinedButton(
                                        onClick = { dialog = "region" },
                                        enabled = canEdit,
                                    ) {
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
                                            modifier =
                                                Modifier.heightIn(
                                                    min = PipeDimensions.primaryActionMinHeight
                                                ),
                                        ) {
                                            Text("수동 표시 시작")
                                        }
                                }
                            }
                            item { ValidationNotice() }
                            if (task.analyzed) {
                                item {
                                    CorrectionToolbar(
                                        mode,
                                        canEdit,
                                        task.undo != null,
                                        { mode = it },
                                        { dispatch(CountAction.Undo) },
                                    )
                                }
                                if (!pinConfirm)
                                    item {
                                        ConfirmAction(
                                            task.count,
                                            canEdit,
                                            state.operation == Operation.SAVING,
                                            { dialog = "confirm" },
                                        )
                                    }
                            }
                        }
                        item { DetailPhotoActions(canEdit, onPick, onCamera) { dialog = "delete" } }
                    }
                }
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
