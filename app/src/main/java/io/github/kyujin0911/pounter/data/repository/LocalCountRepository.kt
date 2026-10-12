package io.github.kyujin0911.pounter.data.repository

import android.net.Uri
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.data.detector.PipeDetector
import io.github.kyujin0911.pounter.data.local.database.TaskCodec
import io.github.kyujin0911.pounter.data.local.database.TaskDao
import io.github.kyujin0911.pounter.data.local.database.TaskEntity
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalCountRepository(
    private val dao: TaskDao,
    private val photoStore: PhotoStore,
    private val pipeDetector: PipeDetector,
) : CountRepository {
    private val writeMutex = Mutex()
    override val tasks =
        dao.observe().map { rows -> rows.map { row -> TaskCodec.decode(row.payload) } }

    override suspend fun get(id: String) = dao.get(id)?.let { TaskCodec.decode(it.payload) }

    override suspend fun import(uri: Uri): CountTask =
        writeMutex.withLock {
            withContext(kotlinx.coroutines.NonCancellable) {
                val (name, width, height) = photoStore.import(uri)
                val task =
                    CountTask(
                        UUID.randomUUID().toString(),
                        name,
                        width,
                        height,
                        System.currentTimeMillis(),
                    )
                try {
                    dao.insert(
                        TaskEntity(task.id, TaskCodec.encode(task), task.revision, task.updatedAt)
                    )
                    task
                } catch (e: Exception) {
                    withContext(Dispatchers.IO) { photoStore.delete(name) }
                    throw e
                }
            }
        }

    override suspend fun save(task: CountTask): CountTask =
        writeMutex.withLock {
            val persistedTask = get(task.id) ?: error("작업이 삭제되었습니다.")
            check(!persistedTask.confirmed) { "확정된 기록은 변경할 수 없습니다." }
            val taskToPersist =
                task.copy(revision = task.revision + 1, updatedAt = System.currentTimeMillis())
            check(
                dao.update(
                    task.id,
                    TaskCodec.encode(taskToPersist),
                    task.revision,
                    taskToPersist.revision,
                    taskToPersist.updatedAt,
                    taskToPersist.deleted,
                ) == 1
            ) {
                "작업이 변경되었습니다. 다시 열어주세요."
            }
            taskToPersist
        }

    override suspend fun delete(task: CountTask) =
        writeMutex.withLock {
            // Tombstone first; interrupted file cleanup is retried at next startup.
            val tombstoneTask =
                task.copy(
                    deleted = true,
                    revision = task.revision + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            if (dao.getAny(task.id)?.deleted != true) {
                check(
                    dao.update(
                        task.id,
                        TaskCodec.encode(tombstoneTask),
                        task.revision,
                        tombstoneTask.revision,
                        tombstoneTask.updatedAt,
                        true,
                    ) == 1
                ) {
                    "삭제할 기록이 변경되었습니다. 다시 열어주세요."
                }
            }
            withContext(Dispatchers.IO) { photoStore.delete(task.photoName) }
            dao.purge(task.id)
        }

    override suspend fun preview(task: CountTask) = photoStore.bitmap(task.photoName)

    override suspend fun detect(task: CountTask) = pipeDetector.detect(task)

    suspend fun finishDeletes() =
        writeMutex.withLock {
            dao.tombstones().forEach { row ->
                withContext(Dispatchers.IO) {
                    photoStore.delete(TaskCodec.decode(row.payload).photoName)
                }
                dao.purge(row.id)
            }
            val referencedPhotoNames =
                dao.all().map { TaskCodec.decode(it.payload).photoName }.toSet()
            withContext(Dispatchers.IO) { photoStore.removeOrphans(referencedPhotoNames) }
        }
}
