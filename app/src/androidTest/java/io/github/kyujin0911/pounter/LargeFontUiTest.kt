package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.feature.count.CountUiState
import io.github.kyujin0911.pounter.feature.count.CountWorkScreen
import io.github.kyujin0911.pounter.feature.count.Operation
import io.github.kyujin0911.pounter.feature.home.HomeScreen
import io.github.kyujin0911.pounter.feature.home.HomeUiState
import io.github.kyujin0911.pounter.feature.shared.designBitmap
import io.github.kyujin0911.pounter.feature.shared.designTask
import java.io.File
import org.junit.Assert.assertTrue
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

    @Test
    fun lightAndDarkWorkAtTwoHundredPercentKeepActionsWithinBounds() {
        val density = rule.density.density
        var dark by mutableStateOf(false)
        var failed by mutableStateOf(false)
        val task = designTask(300, true).copy(undo = emptyList())
        val bitmap = designBitmap()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                PipeTheme(dark = dark) {
                    CountWorkScreen(
                        CountUiState(
                            task = task,
                            preview = bitmap,
                            operation = Operation.NONE,
                            unsavedChanges = failed,
                        ),
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        for (darkMode in listOf(false, true)) {
            rule.runOnIdle {
                dark = darkMode
                failed = false
            }
            rule.onNodeWithTag("content").performScrollToNode(hasTestTag("count-value"))
            rule.onNodeWithTag("count-value").assertTextEquals("300개").assertIsDisplayed()
            val bounds = rule.onNodeWithTag("count-value").fetchSemanticsNode().boundsInRoot
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(
                bounds.left >= root.left &&
                    bounds.right <= root.right &&
                    bounds.top >= root.top &&
                    bounds.bottom <= root.bottom
            )
            screenshot("design-review-${if(darkMode) "dark" else "light"}-large-font")
            rule.onNodeWithTag("content").performScrollToNode(hasText("되돌리기"))
            rule.onNodeWithText("되돌리기").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            rule.onNodeWithTag("content").performScrollToNode(hasTestTag("confirm-action"))
            rule
                .onNodeWithTag("confirm-action")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(56.dp)
                .performClick()
            rule.onNodeWithText("확정 저장").assertIsDisplayed()
            rule.onNodeWithText("취소").performClick()
            rule.runOnIdle { failed = true }
            rule.onNodeWithTag("content").performScrollToNode(hasText("저장 재시도"))
            rule.onNodeWithText("저장 재시도").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            screenshot("design-recovery-${if(darkMode) "dark" else "light"}-large-font")
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file =
            File(context.getExternalFilesDir(null), "qa/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use {
            rule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
