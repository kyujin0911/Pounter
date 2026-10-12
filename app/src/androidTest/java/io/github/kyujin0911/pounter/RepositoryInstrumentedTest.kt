package io.github.kyujin0911.pounter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.data.detector.DETECTOR_VALIDATED
import io.github.kyujin0911.pounter.data.detector.HoughPipeDetector
import io.github.kyujin0911.pounter.data.detector.ValidationRequiredDetector
import io.github.kyujin0911.pounter.data.local.database.CountDatabase
import io.github.kyujin0911.pounter.data.local.database.TaskCodec
import io.github.kyujin0911.pounter.data.local.database.TaskDao
import io.github.kyujin0911.pounter.data.local.database.TaskEntity
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import io.github.kyujin0911.pounter.data.repository.LocalCountRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CountDatabase
    private lateinit var countRepository: LocalCountRepository
    private lateinit var photoStore: PhotoStore
    private val importedPhotoNames = mutableListOf<String>()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, CountDatabase::class.java).build()
        photoStore = PhotoStore(context)
        countRepository =
            LocalCountRepository(database.tasks(), photoStore, ValidationRequiredDetector())
    }

    @After
    fun cleanup() {
        importedPhotoNames.forEach { photoStore.delete(it) }
        database.close()
    }

    private suspend fun fixture(): CountTask {
        val image = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(40, 40, 40))
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
        for (y in 0..2) for (x in 0..3) canvas.drawCircle(
            100f + x * 190,
            100f + y * 190,
            50f,
            paint,
        )
        val source = File.createTempFile("test-pipes-", ".png", context.cacheDir)
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        return try {
            countRepository.import(Uri.fromFile(source)).also { importedPhotoNames += it.photoName }
        } finally {
            source.delete()
        }
    }

    @Test
    fun importEditReloadConfirmAndDelete() = runBlocking {
        val initial = fixture()
        assertTrue(photoStore.file(initial.photoName).exists())
        assertEquals(800, initial.width)
        val marker = Marker("manual", .5f, .5f, .02f, true)
        val stored = countRepository.save(initial.copy(analyzed = true).edit(listOf(marker)))
        val reopened =
            LocalCountRepository(database.tasks(), photoStore, ValidationRequiredDetector())
                .get(stored.id)!!
        assertEquals(stored, reopened)
        val confirmed = countRepository.save(reopened.confirm(100))
        assertTrue(countRepository.get(initial.id)!!.confirmed)
        try {
            countRepository.save(confirmed)
            fail("confirmed must be immutable")
        } catch (_: IllegalStateException) {}
        countRepository.delete(confirmed)
        assertNull(countRepository.get(initial.id))
        assertFalse(photoStore.file(initial.photoName).exists())
    }

    @Test
    fun staleRevisionCannotOverwriteSavedChanges() = runBlocking {
        val initial = fixture()
        countRepository.save(initial.copy(analyzed = true))
        try {
            countRepository.save(initial.copy(analyzed = true))
            fail("stale update accepted")
        } catch (_: IllegalStateException) {}
        assertEquals(1L, countRepository.get(initial.id)!!.revision)
    }

    @Test
    fun interruptedDeletionIsCompletedOnStartup() = runBlocking {
        val initial = fixture()
        val deleted = initial.copy(deleted = true, revision = 1)
        database.tasks().update(initial.id, TaskCodec.encode(deleted), 0, 1, 2, true)
        assertNull(countRepository.get(initial.id))
        assertTrue(photoStore.file(initial.photoName).exists())
        countRepository.finishDeletes()
        assertFalse(photoStore.file(initial.photoName).exists())
        assertTrue(database.tasks().tombstones().isEmpty())
    }

    @Test
    fun invalidInputDoesNotReplaceExistingRecord() = runBlocking {
        val initial = fixture()
        val source =
            File.createTempFile("invalid-", ".txt", context.cacheDir).apply {
                writeText("not an image")
            }
        try {
            countRepository.import(Uri.fromFile(source))
            fail("invalid photo accepted")
        } catch (_: java.io.IOException) {} finally {
            source.delete()
        }
        assertEquals(initial, countRepository.get(initial.id))
    }

    @Test
    fun cancellationAfterInsertKeepsReferencedPhoto() = runBlocking {
        val initial = fixture()
        val inserted = CompletableDeferred<TaskEntity>()
        val resume = CompletableDeferred<Unit>()
        val dao = database.tasks()
        val slowDao =
            object : TaskDao by dao {
                override suspend fun insert(entity: TaskEntity) {
                    dao.insert(entity)
                    inserted.complete(entity)
                    resume.await()
                }
            }
        val slowRepo = LocalCountRepository(slowDao, photoStore, ValidationRequiredDetector())
        val job = launch { slowRepo.import(Uri.fromFile(photoStore.file(initial.photoName))) }
        val entity = inserted.await()
        job.cancel()
        resume.complete(Unit)
        job.join()
        val persisted = slowRepo.get(entity.id)!!
        importedPhotoNames += persisted.photoName
        assertTrue(photoStore.file(persisted.photoName).exists())
        assertEquals(persisted, slowRepo.get(entity.id))
    }

    @Test
    fun legacyVersionOneDatabaseAndPayloadReopenWithoutMigration() = runBlocking {
        // SQL/identity/payload are the pre-refactor v1 contract, not generated by new codec.
        val databaseName = "legacy-counts-${java.util.UUID.randomUUID()}.db"
        val payload =
            """{"id":"legacy","photo":"legacy.jpg","width":800,"height":600,"created":1,"updated":2,"revision":3,"region":[0,0,1,1],"markers":[{"id":"m","x":0.5,"y":0.5,"r":0.02,"manual":true}],"automatic":[],"undo":null,"analyzed":true,"millis":null,"confirmed":100,"deleted":false}"""
        context.openOrCreateDatabase(databaseName, 0, null).use { legacyDatabase ->
            legacyDatabase.execSQL(
                "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL)"
            )
            legacyDatabase.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            legacyDatabase.execSQL(
                "INSERT INTO room_master_table VALUES(42, '5e458c13ee203a11ba2f7d79b3debf20')"
            )
            legacyDatabase.execSQL(
                "INSERT INTO tasks VALUES(?, ?, ?, ?, ?)",
                arrayOf<Any>("legacy", payload, 3, 2, 0),
            )
            legacyDatabase.version = 1
        }
        val reopenedDatabase =
            Room.databaseBuilder(context, CountDatabase::class.java, databaseName).build()
        try {
            val repository =
                LocalCountRepository(
                    reopenedDatabase.tasks(),
                    photoStore,
                    ValidationRequiredDetector(),
                )
            val task = repository.get("legacy")!!
            assertEquals(3L, task.revision)
            assertTrue(task.confirmed)
            assertEquals(100L, task.confirmedAt)
            assertEquals(1, task.count)
            assertTrue(task.markers.single().manual)
            assertEquals("legacy.jpg", task.photoName)
            assertEquals(payload, reopenedDatabase.tasks().get("legacy")!!.payload)
        } finally {
            reopenedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun prototypeLoadsNativeLibraryAndDetectsSyntheticCircles() = runBlocking {
        val initial = fixture()
        val detected = HoughPipeDetector(photoStore).detect(initial)
        assertEquals(12, detected.markers.size)
        assertTrue(detected.markers.all { initial.region.contains(it.x, it.y) })
        // Synthetic success is explicitly not evidence of real-world accuracy.
        assertFalse(DETECTOR_VALIDATED)
    }
}
