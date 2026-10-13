package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.model.CountTask

internal enum class TaskStatus {
    UNANALYZED,
    REVIEW_REQUIRED,
    CONFIRMED,
}

internal fun taskStatus(task: CountTask): TaskStatus =
    when {
        task.confirmed -> TaskStatus.CONFIRMED
        task.analyzed -> TaskStatus.REVIEW_REQUIRED
        else -> TaskStatus.UNANALYZED
    }

@Composable
internal fun StatusLabel(status: TaskStatus, modifier: Modifier = Modifier) {
    val completed = status == TaskStatus.CONFIRMED
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color =
            if (completed) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor =
            if (completed) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            when (status) {
                TaskStatus.UNANALYZED -> "분석 전"
                TaskStatus.REVIEW_REQUIRED -> "확인 전"
                TaskStatus.CONFIRMED -> "사용자 확인 완료"
            },
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
