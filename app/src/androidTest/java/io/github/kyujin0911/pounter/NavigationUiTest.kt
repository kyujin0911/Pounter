package io.github.kyujin0911.pounter

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.data.*
import io.github.kyujin0911.pounter.model.*
import io.github.kyujin0911.pounter.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

class NavigationUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var nav: NavHostController

    private class Repository : CountRepository {
        var stored =
            CountTask(
                "draft",
                "photo.jpg",
                900,
                650,
                1,
                analyzed = true,
                markers = listOf(Marker("m", .5f, .5f, .02f, true)),
            )
        override val tasks = MutableStateFlow(listOf(stored))
        var failSave = false
        var failDelete = false
        var importCount = 0
        var imported: CountTask? = null

        override suspend fun get(id: String) = tasks.value.firstOrNull { it.id == id }

        override suspend fun preview(task: CountTask): Bitmap? = null

        override suspend fun import(uri: Uri): CountTask {
            importCount++
            val next = imported ?: error("테스트 가져오기 실패")
            tasks.value = tasks.value + next
            return next
        }

        override suspend fun detect(task: CountTask): Detection = error("not used")

        override suspend fun save(task: CountTask): CountTask {
            if (failSave) error("테스트 저장 실패")
            stored = task.copy(revision = task.revision + 1)
            tasks.value = listOf(stored)
            return stored
        }

        override suspend fun delete(task: CountTask) {
            if (failDelete) error("테스트 삭제 실패")
            tasks.value = emptyList()
        }
    }

    private fun start(repo: Repository, inputRegistry: ActivityResultRegistry? = null) {
        rule.setContent {
            nav = rememberNavController()
            if (inputRegistry == null) PipeTheme { PipeNavigation(repo, nav) }
            else
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides
                        object : ActivityResultRegistryOwner {
                            override val activityResultRegistry: ActivityResultRegistry =
                                requireNotNull(inputRegistry)
                        }
                ) {
                    PipeTheme { PipeNavigation(repo, nav) }
                }
        }
        rule.waitForIdle()
    }

    private fun scroll(text: String) {
        rule.onNodeWithTag("content").performScrollToNode(hasText(text))
    }

    private inline fun <reified T : Any> assertRoute() {
        rule.runOnIdle { assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<T>()) }
    }

    private fun openDraft() {
        scroll("진행 중 · 이어서 확인")
        rule.onNodeWithText("진행 중 · 이어서 확인").performClick()
        rule.waitForIdle()
        assertRoute<WorkRoute>()
    }

    @Test
    fun confirmationFailureStaysInWorkAndRetryReplacesEditor() {
        val repo = Repository()
        repo.failSave = true
        start(repo)
        openDraft()
        scroll("1개 확인하고 확정")
        rule.onNodeWithText("1개 확인하고 확정").performClick()
        rule.onNodeWithText("확정 저장").performClick()
        rule.waitForIdle()
        assertRoute<WorkRoute>()
        assertFalse(repo.stored.confirmed)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        rule.onNodeWithText("저장되지 않은 변경").assertIsDisplayed()
        assertRoute<WorkRoute>()
        rule.onNodeWithText("취소").performClick()
        scroll("저장 재시도")
        rule.onNodeWithText("저장 재시도").assertIsDisplayed()
        repo.failSave = false
        rule.onNodeWithText("저장 재시도").performClick()
        rule.waitForIdle()
        assertRoute<RecordRoute>()
        rule.onNodeWithText("수동 표시 시작").assertDoesNotExist()
        rule.runOnIdle { nav.popBackStack() }
        rule.waitForIdle()
        assertRoute<HomeRoute>()
    }

    @Test
    fun backToListPreservesDraftAndReopensIt() {
        val repo = Repository()
        start(repo)
        openDraft()
        scroll("기록 목록")
        rule.onNodeWithText("기록 목록").performClick()
        rule.waitForIdle()
        assertRoute<HomeRoute>()
        assertEquals(1, repo.stored.count)
        openDraft()
        assertEquals(1, repo.stored.count)
    }

    @Test
    fun unknownIdOffersRecoveryWithoutShowingAnotherTask() {
        start(Repository())
        rule.runOnIdle { nav.navigate(WorkRoute("missing")) }
        rule.waitForIdle()
        assertRoute<WorkRoute>()
        rule.onNodeWithText("현재 개수").assertDoesNotExist()
        scroll("목록으로 돌아가기")
        rule.onNodeWithText("목록으로 돌아가기").performClick()
        rule.waitForIdle()
        assertRoute<HomeRoute>()
    }

    @Test
    fun recordRouteIsReadOnlyAndDeleteNavigatesOnlyAfterSuccess() {
        val repo = Repository()
        repo.stored = repo.stored.confirm(2)
        repo.tasks.value = listOf(repo.stored)
        start(repo)
        scroll("확정됨 · 1개")
        rule.onNodeWithText("확정됨 · 1개").performClick()
        rule.waitForIdle()
        assertRoute<RecordRoute>()
        rule.onNodeWithText("표시 보정").assertDoesNotExist()
        repo.failDelete = true
        scroll("이 기록 삭제")
        rule.onNodeWithText("이 기록 삭제").performClick()
        rule.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertRoute<RecordRoute>()
        assertEquals(1, repo.tasks.value.size)
        repo.failDelete = false
        scroll("이 기록 삭제")
        rule.onNodeWithText("이 기록 삭제").performClick()
        rule.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertRoute<HomeRoute>()
        assertTrue(repo.tasks.value.isEmpty())
    }

    @Test
    fun wrongRouteKindRedirectsToActualPersistedState() {
        val repo = Repository()
        start(repo)
        rule.runOnIdle { nav.navigate(RecordRoute("draft")) }
        rule.waitForIdle()
        assertRoute<WorkRoute>()
    }

    private fun registry(resultCode: Int, data: Intent? = null) =
        object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, resultCode, data)
            }
        }

    @Test
    fun cancelledPhotoAndCameraInputKeepOriginRouteAndTask() {
        val repo = Repository()
        start(repo, registry(Activity.RESULT_CANCELED))
        openDraft()
        scroll("다른 사진 선택")
        rule.onNodeWithText("다른 사진 선택").performClick()
        rule.waitForIdle()
        assertRoute<WorkRoute>()
        assertEquals(0, repo.importCount)
        scroll("새 사진 촬영")
        rule.onNodeWithText("새 사진 촬영").performClick()
        rule.waitForIdle()
        assertRoute<WorkRoute>()
        assertEquals(0, repo.importCount)
        assertEquals(1, repo.stored.count)
    }

    @Test
    fun successfulImportReplacesDetailAndPreservesPreviousDraft() {
        val repo = Repository()
        repo.imported = repo.stored.copy(id = "new")
        start(
            repo,
            registry(Activity.RESULT_OK, Intent().setData(Uri.parse("content://fixture/photo"))),
        )
        openDraft()
        scroll("다른 사진 선택")
        rule.onNodeWithText("다른 사진 선택").performClick()
        rule.waitForIdle()
        assertRoute<WorkRoute>()
        rule.runOnIdle {
            assertEquals("new", nav.currentBackStackEntry!!.arguments!!.getString("taskId"))
        }
        assertEquals(2, repo.tasks.value.size)
        assertEquals(1, repo.stored.count)
        rule.runOnIdle { nav.popBackStack() }
        rule.waitForIdle()
        assertRoute<HomeRoute>()
    }

    @Test
    fun failedImportKeepsOriginAndShowsRetryableError() {
        val repo = Repository()
        start(
            repo,
            registry(Activity.RESULT_OK, Intent().setData(Uri.parse("content://fixture/photo"))),
        )
        openDraft()
        scroll("다른 사진 선택")
        rule.onNodeWithText("다른 사진 선택").performClick()
        rule.waitForIdle()
        assertRoute<WorkRoute>()
        assertEquals(1, repo.tasks.value.size)
        scroll("테스트 가져오기 실패")
        rule.onNodeWithText("테스트 가져오기 실패").assertIsDisplayed()
    }
}
