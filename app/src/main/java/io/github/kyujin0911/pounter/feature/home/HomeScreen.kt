package io.github.kyujin0911.pounter.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.ui.components.MessageCard
import io.github.kyujin0911.pounter.core.ui.components.ProgressCard
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions
import io.github.kyujin0911.pounter.feature.shared.PhotoImportUiState
import io.github.kyujin0911.pounter.feature.shared.ValidationNotice

@Composable
fun HomeScreen(
    state: HomeUiState,
    dispatch: (HomeAction) -> Unit,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    inputError: String? = null,
    onClearInputError: () -> Unit = {},
    photoImportState: PhotoImportUiState = PhotoImportUiState(),
    onOpen: (CountTask) -> Unit = {},
) {
    val isBusy = state.isLoading || photoImportState.isImporting
    BackHandler(photoImportState.isImporting) {}
    val message = inputError ?: photoImportState.message ?: state.message
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
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
                    Text("Pounter", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "사진으로 확인하고, 기록으로 남기세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isBusy) item { ProgressCard("사진과 기록을 불러오는 중") }
                if (message != null)
                    item {
                        MessageCard(
                            message,
                            onDismiss = {
                                dispatch(HomeAction.ClearMessage)
                                onClearInputError()
                            },
                        )
                    }
                item { PhotoInputCard(!isBusy, onCamera, onPick) }
                item { ValidationNotice() }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "내 기록",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${state.records.size}건",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.records.isEmpty() && !isBusy)
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("아직 저장된 기록이 없습니다", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "사진을 선택하거나 촬영해 시작하세요",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                items(state.records, key = { it.id }) { record ->
                    RecordCard(record, !isBusy, onOpen)
                }
                item {
                    Text(
                        "기록은 이 기기에만 보관됩니다. 앱 데이터 삭제 또는 앱 제거 시 사라집니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
