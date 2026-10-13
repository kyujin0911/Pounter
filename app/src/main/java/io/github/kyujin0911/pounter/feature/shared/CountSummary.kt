package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kyujin0911.pounter.core.model.CountTask

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountSummary(task: CountTask, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            buildAnnotatedString {
                if (task.analyzed) {
                    append(task.count.toString())
                    withStyle(SpanStyle(fontSize = 16.sp)) { append("개") }
                } else append("—")
            },
            Modifier.testTag("count-value"),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (task.confirmed) "확인한 개수" else "현재 개수",
                style = MaterialTheme.typography.titleMedium,
            )
            StatusLabel(taskStatus(task))
            if (task.confirmed)
                Text(
                    formatTaskTimestamp(requireNotNull(task.confirmedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            Text(
                if (task.analysisMillis == null) "수동 표시" else "자동 분석 ${task.analysisMillis}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
