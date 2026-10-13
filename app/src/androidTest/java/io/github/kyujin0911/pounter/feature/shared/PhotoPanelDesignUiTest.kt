package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoPanelDesignUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun correctionKeepsZoomForSameTask() {
        var task by mutableStateOf(designTask(analyzed = true))
        val bitmap = designBitmap()
        rule.setContent {
            PipeTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PhotoPanel(task, bitmap, EditMode.VIEW, false, true, false)
                }
            }
        }
        rule.onNodeWithText("확대").performClick()
        rule
            .onNodeWithTag("photo-viewport")
            .assert(
                SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "확대 1.5배",
                )
            )
        rule.runOnIdle { task = task.copy(markers = listOf(Marker("added", .5f, .5f, .01f, true))) }
        rule
            .onNodeWithTag("photo-viewport")
            .assert(
                SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "확대 1.5배",
                )
            )
    }

    @Test
    fun resizedViewportAddsAtCorrectNormalizedPoint() {
        var height by mutableStateOf(240.dp)
        val added = mutableListOf<Pair<Float, Float>>()
        val task = designTask(analyzed = true)
        val bitmap = designBitmap()
        rule.setContent {
            PipeTheme {
                PhotoPanel(
                    task,
                    bitmap,
                    EditMode.ADD,
                    false,
                    true,
                    false,
                    viewportHeight = height,
                    onAddMarker = { x, y -> added += x to y },
                )
            }
        }
        rule.onNodeWithTag("photo-viewport").performTouchInput { click(center) }
        rule.runOnIdle { height = 180.dp }
        rule.onNodeWithTag("photo-viewport").performTouchInput { click(center) }
        assertEquals(2, added.size)
        added.forEach {
            assertEquals(.5f, it.first, .01f)
            assertEquals(.5f, it.second, .01f)
        }
    }

    @Test
    fun overlapSelectionAndAlternativePositionControlsRemainAvailable() {
        var mode by mutableStateOf(EditMode.DELETE)
        val removed = mutableListOf<Set<String>>()
        var added = 0
        val task =
            designTask(analyzed = true)
                .copy(
                    markers =
                        listOf(Marker("a", .5f, .5f, .05f, true), Marker("b", .5f, .5f, .05f, true))
                )
        val bitmap = designBitmap()
        rule.setContent {
            PipeTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PhotoPanel(
                        task,
                        bitmap,
                        mode,
                        false,
                        true,
                        false,
                        onAddMarker = { _, _ -> added++ },
                        onRemoveMarkers = { removed += it },
                    )
                }
            }
        }
        rule.onNodeWithTag("photo-viewport").performTouchInput { click(center) }
        rule.onNodeWithText("겹친 표시 선택").assertIsDisplayed()
        rule
            .onAllNodes(hasText("삭제") and hasAnyAncestor(isDialog()), useUnmergedTree = true)[0]
            .performClick()
        assertEquals(listOf(setOf("a")), removed)
        rule.runOnIdle { mode = EditMode.ADD }
        rule.onNodeWithText("이 위치에 추가").performScrollTo().performClick()
        assertEquals(1, added)
    }
}
