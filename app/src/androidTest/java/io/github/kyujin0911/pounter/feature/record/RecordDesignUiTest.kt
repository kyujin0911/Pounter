package io.github.kyujin0911.pounter.feature.record

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.feature.shared.designTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordDesignUiTest {
    @get:Rule val rule = createComposeRule()

    private fun scroll(text: String) {
        rule.onNodeWithTag("content").performScrollToNode(hasText(text))
    }

    @Test
    fun confirmedRecordIsReadOnlyEvenWhenPhotoFails() {
        rule.setContent {
            PipeTheme {
                RecordScreen(
                    RecordUiState(task = designTask(12, true, true), isLoading = false),
                    {},
                    {},
                    {},
                )
            }
        }
        rule.onNodeWithText("사용자 확인 완료").assertExists()
        rule.onNodeWithTag("count-value").assertTextEquals("12개")
        rule.onNodeWithText("수동 표시 시작").assertDoesNotExist()
        rule.onNodeWithText("추가").assertDoesNotExist()
        rule.onNodeWithTag("confirm-action").assertDoesNotExist()
        scroll("사진을 표시할 수 없습니다. 기록 목록에서 다시 열어주세요.")
        rule.onNodeWithText("사진을 표시할 수 없습니다. 기록 목록에서 다시 열어주세요.").assertIsDisplayed()
        scroll("표시 목록 12개")
        rule.onNodeWithText("표시 목록 12개").performClick()
        rule
            .onNode(hasText("삭제") and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun deleteRequiresDialogAndCancelDoesNotDispatch() {
        val actions = mutableListOf<RecordAction>()
        rule.setContent {
            PipeTheme {
                RecordScreen(
                    RecordUiState(task = designTask(12, true, true), isLoading = false),
                    { actions += it },
                    {},
                    {},
                )
            }
        }
        scroll("이 기록 삭제")
        rule.onNodeWithText("이 기록 삭제").performClick()
        rule.onNodeWithTag("record-delete-dialog").assertExists()
        rule.onNodeWithText("원본 갤러리 사진은 유지", substring = true).assertExists()
        rule.onNodeWithText("취소").performClick()
        assertEquals(emptyList<RecordAction>(), actions)
        rule.onNodeWithText("이 기록 삭제").performClick()
        rule.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        assertEquals(listOf(RecordAction.Delete), actions)
    }

    @Test
    fun busyRecordDisablesDeleteAndSeparateInput() {
        rule.setContent {
            PipeTheme {
                RecordScreen(
                    RecordUiState(
                        task = designTask(12, true, true),
                        isLoading = false,
                        isDeleting = true,
                    ),
                    {},
                    {},
                    {},
                )
            }
        }
        scroll("이 기록 삭제")
        rule.onNodeWithText("이 기록 삭제").assertIsNotEnabled()
        rule.onNodeWithText("다른 사진 선택").assertIsNotEnabled()
        rule.onNodeWithText("새 사진 촬영").assertIsNotEnabled()
    }
}
