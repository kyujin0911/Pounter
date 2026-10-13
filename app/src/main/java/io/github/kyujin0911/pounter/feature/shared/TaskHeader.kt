package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kyujin0911.pounter.core.model.CountTask

@Composable
fun TaskHeader(
    task: CountTask?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (task == null) "작업 불러오기" else if (task.confirmed) "검수 기록" else "사진 확인",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                task?.let { "${formatTaskTimestamp(it.createdAt)} · 기기 내부 저장" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onBack, enabled = canGoBack) { Text("기록 목록") }
    }
}
