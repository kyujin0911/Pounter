package io.github.kyujin0911.pounter

import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.AnalysisPhoto
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import io.github.kyujin0911.pounter.data.local.photo.readOwnedAnalysisPhoto
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisPhotoInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val photos = PhotoStore(context)

    private suspend fun withPhoto(orientation: Int = 1, block: suspend (String) -> Unit) {
        val image = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.BLACK)
            drawRect(10f, 10f, 30f, 30f, Paint().apply { color = Color.WHITE })
        }
        val source = File.createTempFile("exif-", ".jpg", context.cacheDir)
        source.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        image.recycle()
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        val imported = photos.import(Uri.fromFile(source))
        try {
            block(imported.first)
        } finally {
            photos.delete(imported.first)
            source.delete()
        }
    }

    @Test
    fun cancellationBeforeDecodeDeliveryReleasesOwnedBitmap() = runBlocking {
        val pending = LinkedBlockingQueue<Runnable>()
        val deliveryDispatcher =
            object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    pending.put(block)
                }
            }
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        var delivered = false
        val job =
            launch(deliveryDispatcher) {
                readOwnedAnalysisPhoto { AnalysisPhoto(bitmap, 32, 32) }.use { delivered = true }
            }
        try {
            pending.poll(5, TimeUnit.SECONDS)!!.run() // Start acquisition; IO read is asynchronous.
            val delivery = pending.poll(5, TimeUnit.SECONDS)!! // IO completed, caller resume held.
            assertFalse(bitmap.isRecycled)
            job.cancel()
            delivery.run()
            job.join()
            assertFalse(delivered)
            assertTrue(
                "Undelivered decode must be released at prompt cancellation",
                bitmap.isRecycled,
            )
        } finally {
            job.cancel()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    @Test
    fun allExifOrientationsPreserveLandmarks() = runBlocking {
        val centers =
            listOf(
                20 to 20,
                100 to 20,
                100 to 60,
                20 to 60,
                20 to 20,
                60 to 20,
                60 to 100,
                20 to 100,
            )
        for (orientation in 1..8) withPhoto(orientation) { name ->
            photos.analysisPhoto(name, 200, 50000).use { photo ->
                assertEquals(if (orientation <= 4) 120 else 80, photo.photoWidthPx)
                assertEquals(if (orientation <= 4) 80 else 120, photo.photoHeightPx)
                val (x, y) = centers[orientation - 1]
                assertTrue("EXIF $orientation", Color.red(photo.bitmap.getPixel(x, y)) > 200)
            }
        }
    }

    @Test
    fun analysisUsesOwnResolution() = runBlocking {
        withPhoto { name ->
            val preview = photos.bitmap(name, 40)
            photos.analysisPhoto(name, 200, 50000).use {
                assertEquals(120, it.bitmap.width)
                assertEquals(80, it.bitmap.height)
                assertTrue(preview.width < it.bitmap.width)
            }
            preview.recycle()
        }
    }

    @Test
    fun oversizedInputIsExplicitFailure() = runBlocking {
        withPhoto { name ->
            try {
                photos.analysisPhoto(name, 200, 100)
                fail("pixel budget ignored")
            } catch (f: DetectionFailure) {
                assertEquals(DetectionFailureReason.INVALID_INPUT, f.reason)
            }
        }
    }

    @Test
    fun analysisPhotoReleasesOwnedBitmap() = runBlocking {
        withPhoto { name ->
            val photo = photos.analysisPhoto(name, 200, 50000)
            photo.close()
            photo.close()
            assertTrue(photo.bitmap.isRecycled)
        }
    }
}
