package io.github.kyujin0911.pounter.feature.count

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.feature.shared.EditMode
import io.github.kyujin0911.pounter.feature.shared.designTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CountDesignUiTest {
    @get:Rule val rule = createComposeRule()

    private fun scroll(tag: String) {
        if (runCatching { rule.onNodeWithTag(tag).assertIsDisplayed() }.isFailure)
            rule.onNodeWithTag("content").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun failedSaveKeepsCountAndRequiresRetry() {
        val dispatched = mutableListOf<CountAction>()
        rule.setContent {
            PipeTheme {
                CountWorkScreen(
                    CountUiState(
                        task = designTask(12, true),
                        operation = Operation.NONE,
                        unsavedChanges = true,
                    ),
                    { dispatched += it },
                    {},
                    {},
                )
            }
        }
        rule.onNodeWithTag("count-value").assertTextEquals("12개")
        rule.onNodeWithText("저장 재시도").performClick()
        assertEquals(listOf(CountAction.RetrySave), dispatched)
        scroll("confirm-action")
        rule.onNodeWithTag("confirm-action").assertIsNotEnabled()
        rule.onNodeWithText("추가").assertIsNotEnabled()
    }

    @Test
    fun savingNeverShowsConfirmedLabel() {
        rule.setContent {
            PipeTheme {
                CountWorkScreen(
                    CountUiState(task = designTask(12, true), operation = Operation.SAVING),
                    {},
                    {},
                    {},
                )
            }
        }
        rule.onNodeWithText("사용자 확인 완료").assertDoesNotExist()
        scroll("confirm-action")
        rule.onNodeWithTag("confirm-action").assertIsNotEnabled()
    }

    @Test
    fun zeroReviewStillRequiresConfirmation() {
        rule.setContent {
            PipeTheme {
                CountWorkScreen(
                    CountUiState(task = designTask(analyzed = true), operation = Operation.NONE),
                    {},
                    {},
                    {},
                )
            }
        }
        rule.onNodeWithTag("count-value").assertTextEquals("0개")
        rule.onNodeWithText("사용자 확인 완료").assertDoesNotExist()
        scroll("confirm-action")
        rule.onNodeWithTag("confirm-action").performClick()
        rule.onNodeWithText("확정 저장").assertIsDisplayed()
    }

    @Test
    fun modeRemainsCallerControlled() {
        var mode by mutableStateOf(EditMode.VIEW)
        var canEdit by mutableStateOf(true)
        val requested = mutableListOf<EditMode>()
        rule.setContent {
            PipeTheme { CorrectionToolbar(mode, canEdit, true, { requested += it }, {}) }
        }
        rule.onNodeWithText("추가").performClick()
        assertEquals(listOf(EditMode.ADD), requested)
        rule.onNodeWithText("이동").assertIsSelected()
        rule.onNodeWithText("추가").assertIsNotSelected()
        rule.runOnIdle { mode = EditMode.ADD }
        rule.onNodeWithText("추가").assertIsSelected()
        rule.runOnIdle { canEdit = false }
        rule.onNodeWithText("제외").assertIsNotEnabled().performClick()
        assertEquals(listOf(EditMode.ADD), requested)
    }

    @Test
    fun largeFontReviewKeepsConfirmRetryAndUndoReachable() {
        val density = rule.density.density
        var failed by mutableStateOf(false)
        val actions = mutableListOf<CountAction>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                PipeTheme {
                    CountWorkScreen(
                        CountUiState(
                            task = designTask(300, true).copy(undo = emptyList()),
                            operation = Operation.NONE,
                            unsavedChanges = failed,
                        ),
                        { actions += it },
                        {},
                        {},
                    )
                }
            }
        }
        rule.onNodeWithTag("content").performScrollToNode(hasText("되돌리기"))
        rule.onNodeWithText("되돌리기").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        scroll("confirm-action")
        rule.onNodeWithTag("confirm-action").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        rule.runOnIdle { failed = true }
        rule.onNodeWithTag("content").performScrollToNode(hasText("저장 재시도"))
        rule.onNodeWithText("저장 재시도").assertIsDisplayed().performClick()
        assertEquals(listOf(CountAction.RetrySave), actions)
    }

    @Test
    fun rootKeepsCallerTagWidthAndSingleClick() {
        var clicks = 0
        rule.setContent {
            PipeTheme {
                ConfirmAction(
                    12,
                    true,
                    false,
                    { clicks++ },
                    Modifier.width(240.dp).testTag("caller-root"),
                )
            }
        }
        rule.onNodeWithTag("caller-root").assertWidthIsEqualTo(240.dp).performClick()
        assertEquals(1, clicks)
    }
}
