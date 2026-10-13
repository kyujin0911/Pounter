package io.github.kyujin0911.pounter.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions
import io.github.kyujin0911.pounter.feature.shared.StatusLabel
import io.github.kyujin0911.pounter.feature.shared.formatTaskTimestamp
import io.github.kyujin0911.pounter.feature.shared.taskStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordCard(
    task: CountTask,
    canOpen: Boolean,
    onOpen: (CountTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onOpen(task) },
        enabled = canOpen,
        modifier = modifier.fillMaxWidth().testTag("record-${task.id}"),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        FlowRow(
            Modifier.padding(PipeDimensions.cardInset),
            horizontalArrangement = Arrangement.spacedBy(PipeDimensions.groupGap),
            verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
        ) {
            Text(
                if (task.analyzed) "${task.count}개" else "—",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap)) {
                StatusLabel(taskStatus(task))
                if (!task.confirmed)
                    Text("진행 중 · 이어서 확인", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatTaskTimestamp(task.updatedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (task.analysisMillis == null) "수동 확인 / 자동 계수 미검증" else "자동 분석 후 확인",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
