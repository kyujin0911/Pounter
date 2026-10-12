package io.github.kyujin0911.pounter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PipeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable ()->Unit) {
    val colors=if(dark) darkColorScheme(primary=Color(0xFF93C5FD),onPrimary=Color(0xFF0F172A),primaryContainer=Color(0xFF1E3A5F),onPrimaryContainer=Color(0xFFDBEAFE),background=Color(0xFF0F172A),surface=Color(0xFF0F172A),onBackground=Color(0xFFF1F5F9),onSurface=Color(0xFFF1F5F9),surfaceContainer=Color(0xFF1E293B))
        else lightColorScheme(primary=Color(0xFF1D4ED8),onPrimary=Color.White,primaryContainer=Color(0xFFDBEAFE),onPrimaryContainer=Color(0xFF172554),background=Color(0xFFF8FAFC),surface=Color(0xFFF8FAFC),onBackground=Color(0xFF0F172A),onSurface=Color(0xFF0F172A),surfaceContainer=Color(0xFFEFF3F8))
    MaterialTheme(colorScheme=colors,content=content)
}
