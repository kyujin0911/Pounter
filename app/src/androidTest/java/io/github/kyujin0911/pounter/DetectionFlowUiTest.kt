package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.feature.count.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DetectionFlowUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var container: KoinApplication

    @After
    fun close() {
        if (::container.isInitialized) container.close()
    }

    private class Detector : PipeDetector {
        var result =
            Detection(listOf(Marker("new", .3f, .4f, .02f)), 1, AnalysisMetadata("evaluation-v1"))
        var gate: CompletableDeferred<Unit>? = null
        var callback: ((AnalysisStage) -> Unit)? = null

        override suspend fun detect(task: CountTask) = detect(task) {}

        override suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit): Detection =
            withContext(Dispatchers.Default) {
                callback = onStage
                onStage(AnalysisStage.WAITING)
                gate?.await()
                onStage(AnalysisStage.PROCESSING)
                result
            }
    }

    private class Repository(val detector: PipeDetector) : CountRepository {
        var stored = CountTask("t", "t.jpg", 100, 80, 1)
        override val tasks = MutableStateFlow(listOf(stored))
        var failSave = false
        var saves = 0

        override suspend fun get(id: String) = stored

        override suspend fun import(uri: Uri) = error("unused")

        override suspend fun preview(task: CountTask): Bitmap? = null

        override suspend fun delete(task: CountTask) = Unit

        override suspend fun detect(task: CountTask) = detector.detect(task)

        override suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit) =
            detector.detect(task, onStage)

        override suspend fun save(task: CountTask): CountTask {
            if (failSave) error("테스트 저장 실패")
            saves++
            stored = task.copy(revision = task.revision + 1)
            return stored
        }
    }

    private fun start(repo: Repository): CountViewModel {
        container = koinApplication {
            modules(
                module {
                    single<PipeDetector> { repo.detector }
                    single<CountRepository> { repo }
                }
            )
        }
        val vm = CountViewModel(container.koin.get(), SavedStateHandle(mapOf("taskId" to "t")))
        rule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            PipeTheme { CountWorkScreen(state, vm::dispatch, {}, {}) }
        }
        rule.waitUntil { vm.uiState.value.operation == Operation.NONE }
        return vm
    }

    private fun click(text: String) {
        rule.onNodeWithTag("content").performScrollToNode(hasText(text))
        rule.onNodeWithText(text).performClick()
    }

    @Test
    fun failedAnalysisResultIsVisibleAndRetrySavesSameIds() {
        val detector = Detector()
        val repo = Repository(detector).apply { failSave = true }
        val vm = start(repo)
        click("자동 계수 상태")
        rule.waitUntil {
            vm.uiState.value.unsavedChanges && vm.uiState.value.operation == Operation.NONE
        }
        rule.onNodeWithTag("content").performScrollToNode(hasText("저장 재시도"))
        rule.onNodeWithText("저장 재시도").assertIsDisplayed()
        assertEquals("new", vm.uiState.value.task!!.markers.single().id)
        assertEquals("evaluation-v1", vm.uiState.value.task!!.analysisMetadata?.detectorVersion)
        rule.runOnIdle { repo.failSave = false }
        rule.onNodeWithText("저장 재시도").performClick()
        rule.waitUntil { !vm.uiState.value.unsavedChanges }
        assertEquals("new", repo.stored.markers.single().id)
        assertEquals(1, repo.saves)
        click("자동 계수 상태")
        rule.onNodeWithText("재분석").assertExists()
    }

    @Test
    fun waitingCancellationPreservesPreviousMarkersAndRejectsOldStage() {
        val detector = Detector().apply { gate = CompletableDeferred() }
        val repo =
            Repository(detector).apply {
                stored =
                    stored.copy(
                        analyzed = true,
                        markers = listOf(Marker("old", .4f, .4f, .02f, true)),
                    )
            }
        val vm = start(repo)
        rule.runOnIdle { vm.dispatch(CountAction.Analyze) }
        rule.waitUntil { vm.uiState.value.analysisWaiting }
        rule.onNodeWithText("이전 분석을 정리하고 있습니다.").assertExists()
        val old = detector.callback!!
        rule.runOnIdle {
            vm.dispatch(CountAction.CancelAnalysis)
            old(AnalysisStage.PROCESSING)
        }
        rule.waitUntil { vm.uiState.value.operation == Operation.NONE }
        assertEquals("old", vm.uiState.value.task!!.markers.single().id)
        assertFalse(vm.uiState.value.analysisWaiting)
        assertEquals(0, repo.saves)
    }

    @Test
    fun zeroResultRequiresExplicitReviewAndConfirmation() {
        val detector = Detector().apply { result = Detection(emptyList(), 1) }
        val repo = Repository(detector)
        val vm = start(repo)
        click("자동 계수 상태")
        rule.waitUntil {
            vm.uiState.value.task?.analyzed == true && vm.uiState.value.operation == Operation.NONE
        }
        assertFalse(vm.uiState.value.task!!.confirmed)
        click("0개 확인하고 확정")
        rule.onNodeWithText("확정 저장").assertExists()
        assertFalse(repo.stored.confirmed)
    }
}
