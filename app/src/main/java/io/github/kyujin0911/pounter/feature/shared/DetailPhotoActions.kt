package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailPhotoActions(
    canAct: Boolean,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onPick, enabled = canAct) { Text("다른 사진 선택") }
        OutlinedButton(onClick = onCamera, enabled = canAct) { Text("새 사진 촬영") }
        TextButton(onClick = onDelete, enabled = canAct) {
            Text("이 기록 삭제", color = MaterialTheme.colorScheme.error)
        }
    }
    Text("새 사진은 별도 작업으로 저장되며 이 기록은 유지됩니다.", style = MaterialTheme.typography.bodySmall)
}
