package io.github.kyujin0911.pounter.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoInputCard(
    canStart: Boolean,
    onCamera: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.padding(PipeDimensions.inputCardInset),
            verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
        ) {
            Text("새 물량 확인", style = MaterialTheme.typography.headlineSmall)
            Text("한 묶음의 모든 단면이 보이도록 정면에서 촬영하세요.", style = MaterialTheme.typography.bodyLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
                verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
            ) {
                Button(
                    onClick = onCamera,
                    enabled = canStart,
                    modifier = Modifier.heightIn(min = PipeDimensions.primaryActionMinHeight),
                ) {
                    Text("사진 촬영")
                }
                OutlinedButton(
                    onClick = onPick,
                    enabled = canStart,
                    modifier = Modifier.heightIn(min = PipeDimensions.primaryActionMinHeight),
                ) {
                    Text("사진 선택")
                }
            }
        }
    }
}
