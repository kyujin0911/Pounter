package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.feature.home.HomeScreen
import io.github.kyujin0911.pounter.feature.home.HomeUiState
import java.io.File
import org.junit.Rule
import org.junit.Test

class LargeFontUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun darkHomeAtTwoHundredPercentKeepsActionsReachable() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 2f)) {
                PipeTheme(dark = true) { HomeScreen(HomeUiState(isLoading = false), {}, {}, {}) }
            }
        }
        rule.onNodeWithTag("content").performScrollToNode(hasText("사진 촬영"))
        rule.onNodeWithText("사진 촬영").assertIsDisplayed()
        rule.onNodeWithTag("content").performScrollToNode(hasText("사진 선택"))
        rule.onNodeWithText("사진 선택").assertIsDisplayed()
        rule.onNodeWithTag("content").performScrollToNode(hasText("자동 계수 · 검증 대기"))
        rule.onNodeWithText("자동 계수 · 검증 대기").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file =
            File(context.getExternalFilesDir(null), "qa/dark-large-font.png").apply {
                parentFile!!.mkdirs()
            }
        file.outputStream().use {
            rule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
