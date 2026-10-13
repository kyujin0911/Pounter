package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignStatusUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun unanalyzedIsNotZero() {
        rule.setContent { PipeTheme { CountSummary(designTask()) } }
        rule.onNodeWithText("분석 전").assertExists()
        rule.onNodeWithText("—").assertExists()
        rule.onNodeWithText("0개").assertDoesNotExist()
        rule.onNodeWithText("사용자 확인 완료").assertDoesNotExist()
    }

    @Test
    fun analyzedZeroIsNotConfirmed() {
        rule.setContent { PipeTheme { CountSummary(designTask(analyzed = true)) } }
        rule.onNodeWithTag("count-value").assertTextEquals("0개")
        rule.onNodeWithText("확인 전").assertExists()
        rule.onNodeWithText("사용자 확인 완료").assertDoesNotExist()
    }

    @Test
    fun confirmedSummaryKeepsTimestampAndHasNoFalseAutomaticClaim() {
        rule.setContent { PipeTheme { CountSummary(designTask(12, true, true)) } }
        rule.onNodeWithTag("count-value").assertTextEquals("12개")
        rule.onNodeWithText("사용자 확인 완료").assertExists()
        rule.onNodeWithText("확인한 개수").assertExists()
        rule.onNodeWithText("수동 표시").assertExists()
        rule.onNodeWithText(formatTaskTimestamp(1000L), substring = true).assertExists()
    }

    @Test
    fun recoveryRemainsVisibleAndDispatchesRetryOnce() {
        var retries = 0
        rule.setContent { PipeTheme { RecoveryNotice("저장되지 않은 변경", { retries++ }) } }
        rule.onNodeWithText("저장되지 않은 변경").assertIsDisplayed()
        rule.onNodeWithText("저장 재시도").performClick()
        assertEquals(1, retries)
        rule.onNodeWithText("저장되지 않은 변경").assertIsDisplayed()
    }
}
