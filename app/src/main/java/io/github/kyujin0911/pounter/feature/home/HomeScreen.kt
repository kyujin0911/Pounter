package io.github.kyujin0911.pounter.feature.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.ui.components.MessageCard
import io.github.kyujin0911.pounter.core.ui.components.ProgressCard
import io.github.kyujin0911.pounter.feature.shared.PhotoImportUiState
import io.github.kyujin0911.pounter.feature.shared.ValidationNotice
import io.github.kyujin0911.pounter.feature.shared.formatTaskTimestamp

@OptIn(ExperimentalLayoutApi::class)
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
        LazyColumn(
            Modifier.fillMaxSize().padding(insets).imePadding().testTag("content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Pounter", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "사진으로 확인하고, 기록으로 남기세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBusy) item { ProgressCard("사진과 기록을 불러오는 중") }
            if (message != null)
                item {
                    MessageCard(message) {
                        dispatch(HomeAction.ClearMessage)
                        onClearInputError()
                    }
                }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "새 물량 확인",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("파이프 단면 전체가 보이도록 정면에서 촬영해주세요. 한 묶음, 비슷한 지름, 가림 없는 사진이 기준입니다.")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onCamera,
                                enabled = !isBusy,
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) {
                                Text("사진 촬영")
                            }
                            OutlinedButton(
                                onClick = onPick,
                                enabled = !isBusy,
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) {
                                Text("사진 선택")
                            }
                        }
                    }
                }
            }
            item { ValidationNotice() }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "내 기록",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.records.size}건",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.records.isEmpty() && !isBusy)
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("아직 저장된 기록이 없습니다", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "사진을 선택하거나 촬영해 시작하세요",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            items(state.records, key = { it.id }) { record ->
                Card(
                    onClick = { if (!isBusy) onOpen(record) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                if (record.analyzed) "${record.count}" else "—",
                                Modifier.padding(16.dp),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (record.confirmed) "확정됨 · ${record.count}개" else "진행 중 · 이어서 확인",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                formatTaskTimestamp(record.updatedAt),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (record.analysisMillis == null) "수동 확인 / 자동 계수 미검증"
                                else "자동 분석 후 확인",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "기록은 이 기기에만 보관됩니다. 앱 데이터 삭제 또는 앱 제거 시 사라집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
