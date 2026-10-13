package io.github.kyujin0911.pounter.feature.home

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.feature.shared.PhotoImportUiState
import io.github.kyujin0911.pounter.feature.shared.designTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeDesignUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun inputActionsDispatchOnceAndDisableDuringImport() {
        var camera = 0
        var pick = 0
        val openedRecords = mutableListOf<CountTask>()
        val input = mutableStateOf(PhotoImportUiState())
        rule.setContent {
            PipeTheme {
                HomeScreen(
                    HomeUiState(listOf(designTask()), false),
                    {},
                    { pick++ },
                    { camera++ },
                    photoImportState = input.value,
                    onOpen = { openedRecords += it },
                )
            }
        }
        rule.onNodeWithText("사진 촬영").performClick()
        rule.onNodeWithText("사진 선택").performClick()
        assertEquals(1, camera)
        assertEquals(1, pick)
        rule.runOnIdle { input.value = PhotoImportUiState(isImporting = true) }
        rule.onNodeWithTag("content").performScrollToNode(hasText("사진 촬영"))
        rule.onNodeWithText("사진 촬영").assertIsNotEnabled()
        rule.onNodeWithText("사진 선택").assertIsNotEnabled()
        rule.onNodeWithTag("content").performScrollToNode(hasTestTag("record-design-fixture"))
        rule.onNodeWithTag("record-design-fixture").assertIsNotEnabled().performClick()
        assertEquals(0, openedRecords.size)
    }

    @Test
    fun recordsSeparateUnanalyzedZeroAndConfirmed() {
        val records =
            listOf(
                designTask().copy(id = "new"),
                designTask(analyzed = true).copy(id = "zero"),
                designTask(12, true, true).copy(id = "done"),
            )
        rule.setContent { PipeTheme { HomeScreen(HomeUiState(records, false), {}, {}, {}) } }
        fun check(id: String, text: String) {
            rule.onNodeWithTag("content").performScrollToNode(hasTestTag("record-$id"))
            rule
                .onNode(
                    hasText(text) and hasAnyAncestor(hasTestTag("record-$id")),
                    useUnmergedTree = true,
                )
                .assertExists()
        }
        check("new", "분석 전")
        check("zero", "0개")
        check("zero", "확인 전")
        check("done", "사용자 확인 완료")
    }

    @Test
    fun rootKeepsCallerTagWidthAndSingleClick() {
        var clicks = 0
        rule.setContent {
            PipeTheme {
                RecordCard(
                    designTask(),
                    true,
                    { clicks++ },
                    Modifier.width(240.dp).testTag("caller-root"),
                )
            }
        }
        rule.onNodeWithTag("caller-root").assertWidthIsEqualTo(240.dp).performClick()
        assertEquals(1, clicks)
    }
}
