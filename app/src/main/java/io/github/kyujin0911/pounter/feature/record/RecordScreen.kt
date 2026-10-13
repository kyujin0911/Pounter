package io.github.kyujin0911.pounter.feature.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.ui.components.*
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions
import io.github.kyujin0911.pounter.feature.shared.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(
    state: RecordUiState,
    dispatch: (RecordAction) -> Unit,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    inputError: String? = null,
    onClearInputError: () -> Unit = {},
    onBack: () -> Unit = {},
    photoImportState: PhotoImportUiState = PhotoImportUiState(),
) {
    var showNumbers by rememberSaveable { mutableStateOf(true) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val task = state.task
    val isBusy = state.isBusy || photoImportState.isImporting
    val message = inputError ?: photoImportState.message ?: state.message
    BackHandler(isBusy) {}
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val contentHeightDp = maxHeight.value.coerceAtLeast(1f)
            val contentWidthDp =
                (minOf(maxWidth, PipeDimensions.readingMaxWidth) - PipeDimensions.screenInset * 2)
                    .value
                    .coerceAtLeast(1f)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = PipeDimensions.readingMaxWidth)
                        .fillMaxSize()
                        .imePadding()
                        .testTag("content"),
                    contentPadding = PaddingValues(PipeDimensions.screenInset),
                    verticalArrangement = Arrangement.spacedBy(PipeDimensions.groupGap),
                ) {
                    item { TaskHeader(task, !isBusy, onBack) }
                    if (isBusy)
                        item { ProgressCard(if (state.isDeleting) "기록 삭제 중" else "사진과 기록을 불러오는 중") }
                    if (message != null)
                        item {
                            MessageCard(
                                message,
                                onDismiss = {
                                    dispatch(RecordAction.ClearMessage)
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
                                EditMode.VIEW,
                                showNumbers,
                                canEdit = false,
                                isLoading = isBusy,
                                viewportHeight =
                                    photoViewportHeightDp(
                                            contentHeightDp,
                                            contentWidthDp,
                                            task.width.toFloat() / task.height,
                                        )
                                        .dp,
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
                        item {
                            DetailPhotoActions(
                                !isBusy,
                                onPick,
                                onCamera,
                                onDelete = { showDeleteDialog = true },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showDeleteDialog && task != null)
        ConfirmDialog(
            "기록 삭제",
            "앱 내부 사진과 표시가 삭제됩니다. 원본 갤러리 사진은 유지되며 이 삭제는 되돌릴 수 없습니다.",
            "삭제",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                dispatch(RecordAction.Delete)
                showDeleteDialog = false
            },
            modifier = Modifier.testTag("record-delete-dialog"),
        )
}
