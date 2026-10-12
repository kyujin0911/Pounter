package io.github.kyujin0911.pounter

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.ui.*
import android.graphics.Bitmap
import java.io.File
import org.junit.*

class LargeFontUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun darkHomeAtTwoHundredPercentKeepsActionsReachable() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f,2f)) {
                PipeTheme(dark=true) {HomeScreen(CountUiState(operation=Operation.NONE),{}, {}, {})}
            }
        }
        rule.onNodeWithTag("content").performScrollToNode(hasText("사진 촬영"))
        rule.onNodeWithText("사진 촬영").assertIsDisplayed()
        rule.onNodeWithTag("content").performScrollToNode(hasText("사진 선택"))
        rule.onNodeWithText("사진 선택").assertIsDisplayed()
        rule.onNodeWithTag("content").performScrollToNode(hasText("자동 계수 · 검증 대기"))
        rule.onNodeWithText("자동 계수 · 검증 대기").assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"qa/dark-large-font.png").apply {parentFile!!.mkdirs()}
        file.outputStream().use {rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
