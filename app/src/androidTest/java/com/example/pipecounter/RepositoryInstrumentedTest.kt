package com.example.pipecounter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pipecounter.data.*
import com.example.pipecounter.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CountDatabase
    private lateinit var repo: LocalCountRepository
    private lateinit var photos: PhotoStore
    private val imported=mutableListOf<String>()
    @Before fun setup() {
        database=Room.inMemoryDatabaseBuilder(context,CountDatabase::class.java).build()
        photos=PhotoStore(context)
        repo=LocalCountRepository(database.tasks(),photos,ValidationRequiredDetector())
    }
    @After fun cleanup() {imported.forEach {photos.delete(it)};database.close()}
    private suspend fun fixture(): CountTask {
        val image=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(Color.rgb(40,40,40))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=5f}
        for(y in 0..2) for(x in 0..3) canvas.drawCircle(100f+x*190,100f+y*190,50f,paint)
        val source=File.createTempFile("test-pipes-",".png",context.cacheDir)
        source.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        image.recycle()
        return try {repo.import(Uri.fromFile(source)).also {imported+=it.photoName}} finally {source.delete()}
    }
    @Test fun importEditReloadConfirmAndDelete()=runBlocking {
        val initial=fixture()
        assertTrue(photos.file(initial.photoName).exists());assertEquals(800,initial.width)
        val marker=Marker("manual",.5f,.5f,.02f,true)
        val stored=repo.save(initial.copy(analyzed=true).edit(listOf(marker)))
        val reopened=LocalCountRepository(database.tasks(),photos,ValidationRequiredDetector()).get(stored.id)!!
        assertEquals(stored,reopened)
        val confirmed=repo.save(reopened.confirm(100))
        assertTrue(repo.get(initial.id)!!.confirmed)
        try {repo.save(confirmed);fail("confirmed must be immutable")} catch(_: IllegalStateException) {}
        repo.delete(confirmed)
        assertNull(repo.get(initial.id));assertFalse(photos.file(initial.photoName).exists())
    }
    @Test fun staleRevisionCannotOverwriteSavedChanges()=runBlocking {
        val initial=fixture();repo.save(initial.copy(analyzed=true))
        try {repo.save(initial.copy(analyzed=true));fail("stale update accepted")} catch(_: IllegalStateException) {}
        assertEquals(1L,repo.get(initial.id)!!.revision)
    }
    @Test fun interruptedDeletionIsCompletedOnStartup()=runBlocking {
        val initial=fixture()
        val deleted=initial.copy(deleted=true,revision=1)
        database.tasks().update(initial.id,TaskCodec.encode(deleted),0,1,2,true)
        assertNull(repo.get(initial.id));assertTrue(photos.file(initial.photoName).exists())
        repo.finishDeletes()
        assertFalse(photos.file(initial.photoName).exists());assertTrue(database.tasks().tombstones().isEmpty())
    }
    @Test fun invalidInputDoesNotReplaceExistingRecord()=runBlocking {
        val initial=fixture()
        val source=File.createTempFile("invalid-",".txt",context.cacheDir).apply {writeText("not an image")}
        try {repo.import(Uri.fromFile(source));fail("invalid photo accepted")} catch(_: java.io.IOException) {} finally {source.delete()}
        assertEquals(initial,repo.get(initial.id))
    }
    @Test fun cancellationAfterInsertKeepsReferencedPhoto()=runBlocking {
        val initial=fixture()
        val inserted=CompletableDeferred<TaskEntity>()
        val resume=CompletableDeferred<Unit>()
        val dao=database.tasks()
        val slowDao=object: TaskDao by dao {
            override suspend fun insert(entity: TaskEntity) {
                dao.insert(entity)
                inserted.complete(entity)
                resume.await()
            }
        }
        val slowRepo=LocalCountRepository(slowDao,photos,ValidationRequiredDetector())
        val job=launch {slowRepo.import(Uri.fromFile(photos.file(initial.photoName)))}
        val entity=inserted.await()
        job.cancel()
        resume.complete(Unit)
        job.join()
        val persisted=slowRepo.get(entity.id)!!
        imported+=persisted.photoName
        assertTrue(photos.file(persisted.photoName).exists())
        assertEquals(persisted,slowRepo.get(entity.id))
    }
    @Test fun prototypeLoadsNativeLibraryAndDetectsSyntheticCircles()=runBlocking {
        val initial=fixture()
        val detected=HoughPipeDetector(photos).detect(initial)
        assertEquals(12,detected.markers.size)
        assertTrue(detected.markers.all {initial.region.contains(it.x,it.y)})
        // Synthetic success is explicitly not evidence of real-world accuracy.
        assertFalse(DETECTOR_VALIDATED)
    }
}
