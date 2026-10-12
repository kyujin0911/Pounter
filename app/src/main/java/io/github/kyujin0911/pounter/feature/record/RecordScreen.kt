package io.github.kyujin0911.pounter.feature.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.feature.shared.ConfirmDialog
import io.github.kyujin0911.pounter.feature.shared.CountSummary
import io.github.kyujin0911.pounter.feature.shared.DetailPhotoActions
import io.github.kyujin0911.pounter.feature.shared.EditMode
import io.github.kyujin0911.pounter.feature.shared.MessageCard
import io.github.kyujin0911.pounter.feature.shared.PhotoImportUiState
import io.github.kyujin0911.pounter.feature.shared.PhotoPanel
import io.github.kyujin0911.pounter.feature.shared.ProgressCard
import io.github.kyujin0911.pounter.feature.shared.TaskHeader

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
        LazyColumn(
            Modifier.fillMaxSize().padding(insets).imePadding().testTag("content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TaskHeader(task, !isBusy, onBack) }
            if (isBusy)
                item { ProgressCard(if (state.isDeleting) "변경 사항 저장 중" else "사진과 기록을 불러오는 중") }
            if (message != null)
                item {
                    MessageCard(message) {
                        dispatch(RecordAction.ClearMessage)
                        onClearInputError()
                    }
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
                        task,
                        state.preview,
                        EditMode.VIEW,
                        showNumbers,
                        canEdit = false,
                        isLoading = isBusy,
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("번호 표시", Modifier.weight(1f))
                        Switch(checked = showNumbers, onCheckedChange = { showNumbers = it })
                    }
                }
                item { DetailPhotoActions(!isBusy, onPick, onCamera) { showDeleteDialog = true } }
            }
        }
    }
    if (showDeleteDialog && task != null)
        ConfirmDialog(
            "기록 삭제",
            "앱 내부 사진과 표시가 삭제됩니다. 원본 갤러리 사진은 유지되며 이 삭제는 되돌릴 수 없습니다.",
            "삭제",
            { showDeleteDialog = false },
        ) {
            dispatch(RecordAction.Delete)
            showDeleteDialog = false
        }
}
