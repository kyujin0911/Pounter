package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import android.net.Uri
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.data.detector.Detection
import io.github.kyujin0911.pounter.data.repository.CountRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class ScreenTestRepository : CountRepository {
    var storedTask = CountTask("task", "photo.jpg", 1000, 1500, 1L, analyzed = true)
    val storedTasks = MutableStateFlow(listOf(storedTask))
    var taskStream: Flow<List<CountTask>> = storedTasks
    var listAccessCount = 0
    override val tasks: Flow<List<CountTask>>
        get() {
            listAccessCount++
            return taskStream
        }

    var hasPreviewFailure = false
    var hasDeleteFailure = false
    var deletionGate: CompletableDeferred<Unit>? = null
    var deleteCount = 0

    override suspend fun get(id: String) = storedTask.takeIf { it.id == id }

    override suspend fun preview(task: CountTask): Bitmap? {
        if (hasPreviewFailure) error("사진 읽기 실패")
        return null
    }

    override suspend fun import(uri: Uri): CountTask = error("not used")

    override suspend fun save(task: CountTask): CountTask = error("not used")

    override suspend fun detect(task: CountTask): Detection = error("not used")

    override suspend fun delete(task: CountTask) {
        deleteCount++
        deletionGate?.await()
        if (hasDeleteFailure) error("삭제 실패")
        storedTasks.value = emptyList()
    }
}
