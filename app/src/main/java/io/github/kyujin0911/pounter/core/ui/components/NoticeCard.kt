package io.github.kyujin0911.pounter.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.kyujin0911.pounter.core.ui.theme.PipeDimensions

internal enum class NoticeTone {
    INFORMATION,
    ERROR,
}

@Composable
internal fun NoticeCard(
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().semantics {
            if (tone == NoticeTone.ERROR) liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.medium,
        color = if (tone == NoticeTone.ERROR) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (tone == NoticeTone.ERROR) colors.onErrorContainer else colors.onSurface,
    ) {
        Column(
            Modifier.padding(PipeDimensions.cardInset),
            verticalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
            if (actions != null)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PipeDimensions.controlGap),
                    content = actions,
                )
        }
    }
}
