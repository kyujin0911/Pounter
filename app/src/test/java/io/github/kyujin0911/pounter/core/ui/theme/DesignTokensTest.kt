package io.github.kyujin0911.pounter.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    @Test
    fun activeTextPairsMeetContrastInBothThemes() {
        for (scheme in listOf(PipeLightColors, PipeDarkColors)) {
            val pairs =
                listOf(
                    scheme.onPrimary to scheme.primary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondary to scheme.secondary,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onTertiary to scheme.tertiary,
                    scheme.onTertiaryContainer to scheme.tertiaryContainer,
                    scheme.onBackground to scheme.background,
                    scheme.onSurface to scheme.surface,
                    scheme.onSurfaceVariant to scheme.surfaceVariant,
                    scheme.onError to scheme.error,
                    scheme.onErrorContainer to scheme.errorContainer,
                    scheme.inverseOnSurface to scheme.inverseSurface,
                    scheme.inversePrimary to scheme.inverseSurface,
                ) +
                    surfaces(scheme).flatMap { surface ->
                        listOf(scheme.onSurface to surface, scheme.onSurfaceVariant to surface)
                    }
            for ((foreground, background) in pairs) {
                val ratio = contrast(foreground, background)
                assertTrue("$foreground / $background: $ratio", ratio >= 4.5)
            }
        }
    }

    private fun surfaces(scheme: ColorScheme) =
        listOf(
            scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
            scheme.surfaceDim,
            scheme.surfaceBright,
        )

    private fun contrast(foreground: Color, background: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(value: Float): Double =
                if (value <= .04045f) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            return .2126 * linear(color.red) +
                .7152 * linear(color.green) +
                .0722 * linear(color.blue)
        }
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
