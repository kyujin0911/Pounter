package io.github.kyujin0911.pounter.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ProgressCard(
    label: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    NoticeCard(
        NoticeTone.INFORMATION,
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        actions =
            if (onCancel != null) {
                { TextButton(onClick = onCancel) { Text("취소") } }
            } else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(label, Modifier.weight(1f))
        }
    }
}
