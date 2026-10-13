package io.github.kyujin0911.pounter.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComponentContractUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun modifierIsAppliedExactlyOnceToNoticeRoot() {
        var clicks = 0
        rule.setContent {
            PipeTheme {
                NoticeCard(
                    NoticeTone.INFORMATION,
                    Modifier.testTag("notice-root").width(240.dp).clickable { clicks++ },
                ) {
                    Text("본문")
                }
            }
        }
        rule.onAllNodesWithTag("notice-root").assertCountEquals(1)
        rule.onNodeWithTag("notice-root").assertWidthIsEqualTo(240.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun toneAndActionsChangesKeepContentRememberState() {
        var tone by mutableStateOf(NoticeTone.INFORMATION)
        var actions by mutableStateOf(false)
        rule.setContent {
            PipeTheme {
                NoticeCard(
                    tone,
                    actions =
                        if (actions) {
                            { Text("조작") }
                        } else null,
                ) {
                    var count by remember { mutableIntStateOf(0) }
                    Button(onClick = { count++ }) { Text("본문 횟수 $count") }
                }
            }
        }
        rule.onNodeWithText("본문 횟수 0").performClick()
        rule.runOnIdle {
            tone = NoticeTone.ERROR
            actions = true
        }
        rule.onAllNodesWithText("본문 횟수 1").assertCountEquals(1)
        rule.runOnIdle { actions = false }
        rule.onAllNodesWithText("본문 횟수 1").assertCountEquals(1)
        rule.onNodeWithText("조작").assertDoesNotExist()
    }

    @Test
    fun noticeSlotsAreOptionalAndSingle() {
        var actions by mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            PipeTheme {
                NoticeCard(
                    NoticeTone.INFORMATION,
                    actions =
                        if (actions) {
                            { TextButton(onClick = { clicks++ }) { Text("재시도") } }
                        } else null,
                ) {
                    Text("본문")
                }
            }
        }
        rule.onAllNodesWithText("본문").assertCountEquals(1)
        rule.onNodeWithText("재시도").assertDoesNotExist()
        rule.runOnIdle { actions = true }
        rule.onNodeWithText("재시도").performClick()
        assertEquals(1, clicks)
        rule.onAllNodesWithText("본문").assertCountEquals(1)
    }
}
