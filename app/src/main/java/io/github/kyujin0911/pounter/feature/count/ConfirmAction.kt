package io.github.kyujin0911.pounter.feature.count

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions

@Composable
internal fun ConfirmAction(
    count: Int,
    canConfirm: Boolean,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onConfirm,
        enabled = canConfirm && !isSaving,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PipeDimensions.primaryActionMinHeight)
                .testTag("confirm-action"),
    ) {
        Text(if (isSaving) "저장 중" else "${count}개 확인하고 확정")
    }
}
