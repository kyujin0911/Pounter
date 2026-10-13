package io.github.kyujin0911.pounter.feature.count

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions
import io.github.kyujin0911.pounter.feature.shared.EditMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CorrectionToolbar(
    mode: EditMode,
    canEdit: Boolean,
    canUndo: Boolean,
    onModeChange: (EditMode) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap)) {
        Text("표시 보정", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
            verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
        ) {
            listOf(EditMode.VIEW to "이동", EditMode.ADD to "추가", EditMode.DELETE to "제외").forEach {
                (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { onModeChange(value) },
                    label = { Text(label) },
                    enabled = canEdit,
                    modifier = Modifier.heightIn(min = PipeDimensions.touchTargetMinSize),
                )
            }
            OutlinedButton(
                onClick = onUndo,
                enabled = canEdit && canUndo,
                modifier = Modifier.heightIn(min = PipeDimensions.touchTargetMinSize),
            ) {
                Text("되돌리기")
            }
        }
        Text(
            when (mode) {
                EditMode.ADD -> "단면 중심을 눌러 표시를 추가하세요. 원형은 자동, 사각형은 수동 표시입니다."
                EditMode.DELETE -> "제외할 표시를 누르세요. 겹친 표시는 목록에서 선택할 수 있습니다."
                EditMode.VIEW -> "두 손가락으로 확대하고 사진을 이동할 수 있습니다."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
