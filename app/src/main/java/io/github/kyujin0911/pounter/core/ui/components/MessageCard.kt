package io.github.kyujin0911.pounter.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun MessageCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    NoticeCard(
        NoticeTone.ERROR,
        modifier,
        actions = { TextButton(onClick = onDismiss) { Text("닫기") } },
    ) {
        Text(message)
    }
}
