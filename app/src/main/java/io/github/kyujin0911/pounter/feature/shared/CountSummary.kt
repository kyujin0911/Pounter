package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kyujin0911.pounter.core.model.CountTask

@Composable
fun CountSummary(task: CountTask) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "${task.count}",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text("현재 개수", fontWeight = FontWeight.Bold)
            Text(
                if (task.confirmed) "확정됨 · ${formatTaskTimestamp(task.confirmedAt!!)}"
                else "확인 전 · 표시와 단면을 대조해주세요",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (task.analysisMillis == null) "수동 표시" else "자동 분석 ${task.analysisMillis}ms",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
