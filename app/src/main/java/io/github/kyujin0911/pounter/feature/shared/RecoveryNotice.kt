package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kyujin0911.pounter.core.ui.components.NoticeCard
import io.github.kyujin0911.pounter.core.ui.components.NoticeTone

@Composable
internal fun RecoveryNotice(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    NoticeCard(
        NoticeTone.ERROR,
        modifier,
        title = message,
        actions = { TextButton(onClick = onRetry) { Text("저장 재시도") } },
    ) {
        Text("현재 보정은 유지하고 있습니다. 저장을 재시도한 뒤 이동하거나 확정해주세요.")
    }
}
