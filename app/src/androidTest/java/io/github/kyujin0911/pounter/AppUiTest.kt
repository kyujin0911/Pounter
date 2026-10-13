package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.kyujin0911.pounter.core.model.CountTask
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AppUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app
        get() = rule.activity.application as PipeApplication

    private val ids = mutableListOf<String>()

    @After
    fun cleanup() = runBlocking {
        ids.forEach { id -> app.repository.get(id)?.let { app.repository.delete(it) } }
    }

    private fun importFixture(): CountTask = runBlocking {
        val image = Bitmap.createBitmap(900, 650, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(65, 77, 90))
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(215, 224, 235)
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
        for (y in 0..2) for (x in 0..3) canvas.drawCircle(
            130f + x * 200,
            120f + y * 180,
            64f,
            paint,
        )
        val source = File.createTempFile("qa-pipes-", ".png", app.cacheDir)
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        try {
            app.repository.import(Uri.fromFile(source)).also { ids += it.id }
        } finally {
            source.delete()
        }
    }

    private fun scrollTo(text: String) {
        rule.onNodeWithTag("content").performScrollToNode(hasText(text))
    }

    private fun screenshot(name: String) {
        val folder = File(app.getExternalFilesDir(null), "qa").apply { mkdirs() }
        rule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun manualCorrectionConfirmAndReopen() {
        rule.onNodeWithText("자동 계수 · 검증 대기").assertIsDisplayed()
        screenshot("home")
        val task = importFixture()
        rule.waitUntil(5000) {
            rule.onAllNodesWithText("진행 중 · 이어서 확인").fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("진행 중 · 이어서 확인")
        rule.onNodeWithText("진행 중 · 이어서 확인").performClick()
        scrollTo("수동 표시 시작")
        rule.onNodeWithText("수동 표시 시작").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.analyzed == true } }
        scrollTo("이 위치에 추가")
        rule.onNodeWithText("이 위치에 추가").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.count == 1 } }
        scrollTo("위치 오른쪽")
        rule.onNodeWithText("위치 오른쪽").performClick()
        scrollTo("이 위치에 추가")
        rule.onNodeWithText("이 위치에 추가").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.count == 2 } }
        scrollTo("되돌리기")
        rule.onNodeWithText("되돌리기").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.count == 1 } }
        // Shared photo callback must still reach Count VM and durable repository storage.
        scrollTo("표시 목록 1개")
        rule.onNodeWithText("표시 목록 1개").performClick()
        rule
            .onNode(hasText("삭제") and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            .performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.count == 0 } }
        scrollTo("이 위치에 추가")
        rule.onNodeWithText("이 위치에 추가").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.count == 1 } }
        scrollTo("현재 개수")
        rule.activityRule.scenario.recreate()
        scrollTo("현재 개수")
        rule.onNodeWithText("현재 개수").assertIsDisplayed()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.count == 1 } }
        screenshot("review")
        scrollTo("1개 확인하고 확정")
        rule.onNodeWithText("1개 확인하고 확정").performClick()
        rule.onNodeWithText("확정 저장").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id)?.confirmed == true } }
        rule.activityRule.scenario.recreate()
        scrollTo("검수 기록")
        rule.onNodeWithText("검수 기록").assertIsDisplayed()
        rule.onNodeWithText("수동 표시 시작").assertDoesNotExist()
        screenshot("confirmed")
        scrollTo("표시 목록 1개")
        rule.onNodeWithText("표시 목록 1개").performClick()
        rule
            .onNode(hasText("삭제") and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            .assertDoesNotExist()
        rule.onNodeWithText("닫기").performClick()
        scrollTo("기록 목록")
        rule.onNodeWithText("기록 목록").performClick()
        rule.onNodeWithTag("content").performScrollToNode(hasTestTag("record-${task.id}"))
        rule.onNodeWithTag("record-${task.id}").performClick()
        scrollTo("검수 기록")
        rule.onNodeWithText("검수 기록").assertIsDisplayed()
        scrollTo("이 기록 삭제")
        rule.onNodeWithText("이 기록 삭제").performClick()
        rule.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.get(task.id) == null } }
    }
}
