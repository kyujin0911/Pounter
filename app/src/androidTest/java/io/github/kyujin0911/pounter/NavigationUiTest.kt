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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.data.detector.Detection
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.di.appModule
import io.github.kyujin0911.pounter.feature.count.CountAction
import io.github.kyujin0911.pounter.feature.count.CountViewModel
import io.github.kyujin0911.pounter.feature.home.HomeViewModel
import io.github.kyujin0911.pounter.navigation.HomeRoute
import io.github.kyujin0911.pounter.navigation.PipeNavigation
import io.github.kyujin0911.pounter.navigation.RecordRoute
import io.github.kyujin0911.pounter.navigation.WorkRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class NavigationUiTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navController: NavHostController
    private lateinit var testKoin: KoinApplication

    @After
    fun closeKoin() {
        if (::testKoin.isInitialized) testKoin.close()
    }

    private class Repository : CountRepository {
        var storedTask =
            CountTask(
                "draft",
                "photo.jpg",
                900,
                650,
                1,
                analyzed = true,
                markers = listOf(Marker("m", .5f, .5f, .02f, true)),
            )
        override val tasks = MutableStateFlow(listOf(storedTask))
        var hasSaveFailure = false
        var hasDeleteFailure = false
        var importCount = 0
        var importedTask: CountTask? = null

        override suspend fun get(id: String) = tasks.value.firstOrNull { it.id == id }

        override suspend fun preview(task: CountTask): Bitmap? = null

        override suspend fun import(uri: Uri): CountTask {
            importCount++
            val next = importedTask ?: error("테스트 가져오기 실패")
            tasks.value = tasks.value + next
            return next
        }

        override suspend fun detect(task: CountTask): Detection = error("not used")

        override suspend fun save(task: CountTask): CountTask {
            if (hasSaveFailure) error("테스트 저장 실패")
            storedTask = task.copy(revision = task.revision + 1)
            tasks.value = listOf(storedTask)
            return storedTask
        }

        override suspend fun delete(task: CountTask) {
            if (hasDeleteFailure) error("테스트 삭제 실패")
            tasks.value = emptyList()
        }
    }

    private fun start(
        countRepository: Repository,
        inputRegistry: ActivityResultRegistry? = null,
        restorationTester: StateRestorationTester? = null,
    ) {
        testKoin = koinApplication {
            modules(appModule())
            modules(module { single<CountRepository> { countRepository } })
        }
        assertSame(countRepository, testKoin.koin.get<CountRepository>())
        val content: @Composable () -> Unit = {
            KoinIsolatedContext(testKoin) {
                navController = rememberNavController()
                if (inputRegistry == null) PipeTheme { PipeNavigation(navController) }
                else
                    CompositionLocalProvider(
                        LocalActivityResultRegistryOwner provides
                            object : ActivityResultRegistryOwner {
                                override val activityResultRegistry: ActivityResultRegistry =
                                    requireNotNull(inputRegistry)
                            }
                    ) {
                        PipeTheme { PipeNavigation(navController) }
                    }
            }
        }
        if (restorationTester == null) composeRule.setContent(content)
        else restorationTester.setContent(content)
        composeRule.waitForIdle()
    }

    private fun scroll(text: String) {
        composeRule.onNodeWithTag("content").performScrollToNode(hasText(text))
    }

    private inline fun <reified T : Any> assertRoute() {
        composeRule.runOnIdle {
            assertTrue(navController.currentBackStackEntry!!.destination.hasRoute<T>())
        }
    }

    private fun openDraft() {
        scroll("진행 중 · 이어서 확인")
        composeRule.onNodeWithText("진행 중 · 이어서 확인").performClick()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
    }

    private inline fun <reified T : ViewModel> currentViewModel(): T =
        ViewModelProvider(navController.currentBackStackEntry!!)[
            "androidx.lifecycle.ViewModelProvider.DefaultKey:io.github.kyujin0911.pounter.ui.CountViewModel",
            T::class.java,
        ]

    @Test
    fun sameClassUsesSeparateEntriesAndRemovedWorkCreatesNewViewModel() {
        val countRepository = Repository()
        val secondTask = countRepository.storedTask.copy(id = "second", markers = emptyList())
        countRepository.tasks.value = listOf(countRepository.storedTask, secondTask)
        start(countRepository)
        lateinit var homeViewModel: HomeViewModel
        composeRule.runOnIdle {
            homeViewModel = currentViewModel()
            navController.navigate(WorkRoute("draft"))
        }
        composeRule.waitForIdle()
        lateinit var firstViewModel: CountViewModel
        composeRule.runOnIdle {
            firstViewModel = currentViewModel()
            assertEquals("draft", firstViewModel.uiState.value.task?.id)
            navController.navigate(WorkRoute("second"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val secondViewModel = currentViewModel<CountViewModel>()
            assertNotSame(firstViewModel, secondViewModel)
            assertEquals("second", secondViewModel.uiState.value.task?.id)
            assertEquals(0, secondViewModel.uiState.value.task?.count)
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(firstViewModel, currentViewModel<CountViewModel>())
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(homeViewModel, currentViewModel<HomeViewModel>())
            navController.navigate(WorkRoute("draft"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertNotSame(firstViewModel, currentViewModel<CountViewModel>()) }
    }

    @Test
    fun savedNavigationStateKeepsWorkIdAndFailedCorrectionCanRetry() {
        val countRepository = Repository().apply { hasSaveFailure = true }
        val restorationTester = StateRestorationTester(composeRule)
        start(countRepository, restorationTester = restorationTester)
        openDraft()
        composeRule.runOnIdle {
            currentViewModel<CountViewModel>().dispatch(CountAction.Add(.7f, .7f))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(currentViewModel<CountViewModel>().uiState.value.unsavedChanges)
            assertEquals(2, currentViewModel<CountViewModel>().uiState.value.task?.count)
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        composeRule.runOnIdle {
            val viewModel = currentViewModel<CountViewModel>()
            assertEquals("draft", viewModel.uiState.value.task?.id)
            assertEquals(2, viewModel.uiState.value.task?.count)
            assertTrue(viewModel.uiState.value.unsavedChanges)
            countRepository.hasSaveFailure = false
            viewModel.dispatch(CountAction.RetrySave)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(currentViewModel<CountViewModel>().uiState.value.unsavedChanges)
            assertEquals(2, countRepository.storedTask.count)
        }
    }

    @Test
    fun confirmationFailureStaysInWorkAndRetryReplacesEditor() {
        val countRepository = Repository()
        countRepository.hasSaveFailure = true
        start(countRepository)
        openDraft()
        scroll("1개 확인하고 확정")
        composeRule.onNodeWithText("1개 확인하고 확정").performClick()
        composeRule.onNodeWithText("확정 저장").performClick()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        assertFalse(countRepository.storedTask.confirmed)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("저장되지 않은 변경").assertIsDisplayed()
        assertRoute<WorkRoute>()
        composeRule.onNodeWithText("취소").performClick()
        scroll("저장 재시도")
        composeRule.onNodeWithText("저장 재시도").assertIsDisplayed()
        countRepository.hasSaveFailure = false
        composeRule.onNodeWithText("저장 재시도").performClick()
        composeRule.waitForIdle()
        assertRoute<RecordRoute>()
        composeRule.onNodeWithText("수동 표시 시작").assertDoesNotExist()
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        assertRoute<HomeRoute>()
    }

    @Test
    fun backToListPreservesDraftAndReopensIt() {
        val countRepository = Repository()
        start(countRepository)
        openDraft()
        scroll("기록 목록")
        composeRule.onNodeWithText("기록 목록").performClick()
        composeRule.waitForIdle()
        assertRoute<HomeRoute>()
        assertEquals(1, countRepository.storedTask.count)
        openDraft()
        assertEquals(1, countRepository.storedTask.count)
    }

    @Test
    fun unknownIdOffersRecoveryWithoutShowingAnotherTask() {
        start(Repository())
        composeRule.runOnIdle { navController.navigate(WorkRoute("missing")) }
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        composeRule.onNodeWithText("현재 개수").assertDoesNotExist()
        scroll("목록으로 돌아가기")
        composeRule.onNodeWithText("목록으로 돌아가기").performClick()
        composeRule.waitForIdle()
        assertRoute<HomeRoute>()
    }

    @Test
    fun recordRouteIsReadOnlyAndDeleteNavigatesOnlyAfterSuccess() {
        val countRepository = Repository()
        countRepository.storedTask = countRepository.storedTask.confirm(2)
        countRepository.tasks.value = listOf(countRepository.storedTask)
        start(countRepository)
        scroll("사용자 확인 완료")
        composeRule.onNodeWithText("사용자 확인 완료").performClick()
        composeRule.waitForIdle()
        assertRoute<RecordRoute>()
        composeRule.onNodeWithText("표시 보정").assertDoesNotExist()
        countRepository.hasDeleteFailure = true
        scroll("이 기록 삭제")
        composeRule.onNodeWithText("이 기록 삭제").performClick()
        composeRule.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertRoute<RecordRoute>()
        assertEquals(1, countRepository.tasks.value.size)
        countRepository.hasDeleteFailure = false
        scroll("이 기록 삭제")
        composeRule.onNodeWithText("이 기록 삭제").performClick()
        composeRule.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertRoute<HomeRoute>()
        assertTrue(countRepository.tasks.value.isEmpty())
    }

    @Test
    fun wrongRouteKindRedirectsToActualPersistedState() {
        val countRepository = Repository()
        start(countRepository)
        composeRule.runOnIdle { navController.navigate(RecordRoute("draft")) }
        composeRule.waitForIdle()
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
        val countRepository = Repository()
        start(countRepository, registry(Activity.RESULT_CANCELED))
        openDraft()
        scroll("다른 사진 선택")
        composeRule.onNodeWithText("다른 사진 선택").performClick()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        assertEquals(0, countRepository.importCount)
        scroll("새 사진 촬영")
        composeRule.onNodeWithText("새 사진 촬영").performClick()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        assertEquals(0, countRepository.importCount)
        assertEquals(1, countRepository.storedTask.count)
    }

    @Test
    fun successfulImportReplacesDetailAndPreservesPreviousDraft() {
        val countRepository = Repository()
        countRepository.importedTask = countRepository.storedTask.copy(id = "new")
        start(
            countRepository,
            registry(Activity.RESULT_OK, Intent().setData(Uri.parse("content://fixture/photo"))),
        )
        openDraft()
        scroll("다른 사진 선택")
        composeRule.onNodeWithText("다른 사진 선택").performClick()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        composeRule.runOnIdle {
            assertEquals(
                "new",
                navController.currentBackStackEntry!!.arguments!!.getString("taskId"),
            )
        }
        assertEquals(2, countRepository.tasks.value.size)
        assertEquals(1, countRepository.storedTask.count)
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        assertRoute<HomeRoute>()
    }

    @Test
    fun failedImportKeepsOriginAndShowsRetryableError() {
        val countRepository = Repository()
        start(
            countRepository,
            registry(Activity.RESULT_OK, Intent().setData(Uri.parse("content://fixture/photo"))),
        )
        openDraft()
        scroll("다른 사진 선택")
        composeRule.onNodeWithText("다른 사진 선택").performClick()
        composeRule.waitForIdle()
        assertRoute<WorkRoute>()
        assertEquals(1, countRepository.tasks.value.size)
        scroll("테스트 가져오기 실패")
        composeRule.onNodeWithText("테스트 가져오기 실패").assertIsDisplayed()
    }
}
