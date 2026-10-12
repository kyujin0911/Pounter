package io.github.kyujin0911.pounter.data

import android.net.Uri
import io.github.kyujin0911.pounter.model.CountTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

interface CountRepository {
    val tasks: Flow<List<CountTask>>
    suspend fun get(id: String): CountTask?
    suspend fun import(uri: Uri): CountTask
    suspend fun save(task: CountTask): CountTask
    suspend fun delete(task: CountTask)
    suspend fun preview(task: CountTask): android.graphics.Bitmap?
    suspend fun detect(task: CountTask): Detection
}

class LocalCountRepository(private val dao: TaskDao, private val photos: PhotoStore, private val detector: PipeDetector) : CountRepository {
    private val writes=Mutex()
    override val tasks=dao.observe().map { rows -> rows.map { TaskCodec.decode(it.payload) } }
    override suspend fun get(id: String) = dao.get(id)?.let { TaskCodec.decode(it.payload) }
    override suspend fun import(uri: Uri): CountTask = writes.withLock {
        withContext(kotlinx.coroutines.NonCancellable) {
        val (name,width,height)=photos.import(uri)
        val task=CountTask(UUID.randomUUID().toString(),name,width,height,System.currentTimeMillis())
        try { dao.insert(TaskEntity(task.id,TaskCodec.encode(task),task.revision,task.updatedAt)); task }
        catch(e: Exception) { withContext(Dispatchers.IO) { photos.delete(name) }; throw e }
        }
    }
    override suspend fun save(task: CountTask): CountTask = writes.withLock {
        val persisted=get(task.id) ?: error("작업이 삭제되었습니다.")
        check(!persisted.confirmed) { "확정된 기록은 변경할 수 없습니다." }
        val next=task.copy(revision=task.revision+1,updatedAt=System.currentTimeMillis())
        check(dao.update(task.id,TaskCodec.encode(next),task.revision,next.revision,next.updatedAt,next.deleted)==1) {
            "작업이 변경되었습니다. 다시 열어주세요."
        }
        next
    }
    override suspend fun delete(task: CountTask) = writes.withLock {
        // Tombstone first; interrupted file cleanup is retried at next startup.
        val next=task.copy(deleted=true,revision=task.revision+1,updatedAt=System.currentTimeMillis())
        if(dao.getAny(task.id)?.deleted != true) {
            check(dao.update(task.id,TaskCodec.encode(next),task.revision,next.revision,next.updatedAt,true)==1) { "삭제할 기록이 변경되었습니다. 다시 열어주세요." }
        }
        withContext(Dispatchers.IO) { photos.delete(task.photoName) }
        dao.purge(task.id)
    }
    override suspend fun preview(task: CountTask) = photos.bitmap(task.photoName)
    override suspend fun detect(task: CountTask) = detector.detect(task)
    suspend fun finishDeletes() = writes.withLock {
        dao.tombstones().forEach { row ->
            withContext(Dispatchers.IO) { photos.delete(TaskCodec.decode(row.payload).photoName) }
            dao.purge(row.id)
        }
        val referenced=dao.all().map {TaskCodec.decode(it.payload).photoName}.toSet()
        withContext(Dispatchers.IO) {photos.removeOrphans(referenced)}
    }
}
