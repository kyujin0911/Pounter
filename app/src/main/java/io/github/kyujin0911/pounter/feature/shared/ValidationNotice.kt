package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ValidationNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("자동 계수 · 검증 대기", fontWeight = FontWeight.Bold)
            Text(
                "추가 정면 사진으로 정확도를 검증한 뒤 제공합니다. 현재는 사진에 수동으로 표시하고 기록을 남길 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
