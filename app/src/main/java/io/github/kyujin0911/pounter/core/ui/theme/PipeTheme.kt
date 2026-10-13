package io.github.kyujin0911.pounter.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PipeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) PipeDarkColors else PipeLightColors,
        typography = PipeTypography,
        shapes = PipeShapes,
        content = content,
    )
}
