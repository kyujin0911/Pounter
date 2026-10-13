package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompositeDetectorInstrumentedTest {
    private val photos =
        PhotoStore(
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun getFilesDir() =
                    File(super.getCacheDir(), "detector-fixtures").apply { mkdirs() }
            }
        )

    private suspend fun fixture(count: Int, block: suspend (CountTask) -> Unit) {
        val width = if (count > 2) 850 else 400
        val height = if (count > 2) 650 else 240
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(30, 30, 30))
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
        for (index in 0 until count) {
            val x = if (count > 2) 25f + (index % 20) * 40 else 100f + index * 190
            val y = if (count > 2) 25f + (index / 20) * 40 else 110f
            paint.color = if (index % 2 == 0) Color.YELLOW else Color.LTGRAY
            val rx = if (count > 2) 12f else 38f
            val ry = if (count > 2) 12f else 28f
            canvas.drawOval(x - rx, y - ry, x + rx, y + ry, paint)
        }
        val name = "${UUID.randomUUID()}.jpg"
        photos.file(name).outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        image.recycle()
        try {
            block(CountTask("t", name, width, height, 1))
        } finally {
            photos.delete(name)
        }
    }

    @Test
    fun ringsAndMetalEllipsesProduceOneMarkerEach() = runBlocking {
        fixture(2) { task ->
            val detected = CompositePipeDetector(photos, DetectionConfig.synthetic()).detect(task)
            assertEquals(2, detected.markers.size)
            assertEquals("synthetic-v1", detected.analysisMetadata?.detectorVersion)
            assertTrue(detected.markers.any { kotlin.math.abs(it.x - .25f) < .01f })
        }
    }

    @Test
    fun emptyImageReturnsZero() = runBlocking {
        fixture(0) { task ->
            assertTrue(
                CompositePipeDetector(photos, DetectionConfig.synthetic())
                    .detect(task)
                    .markers
                    .isEmpty()
            )
        }
    }

    @Test
    fun saturationAndCountOverflowAreDistinct() = runBlocking {
        fixture(2) { task ->
            expectFailure(DetectionFailureReason.CANDIDATE_SATURATION) {
                CompositePipeDetector(photos, DetectionConfig.synthetic().copy(maxCandidates = 1))
                    .detect(task)
            }
        }
        fixture(301) { task ->
            expectFailure(DetectionFailureReason.COUNT_LIMIT) {
                CompositePipeDetector(photos, DetectionConfig.synthetic()).detect(task)
            }
        }
    }

    @Test
    fun initializationFailureIsNotZero() = runBlocking {
        fixture(0) { task ->
            expectFailure(DetectionFailureReason.INITIALIZATION) {
                CompositePipeDetector(photos, DetectionConfig.synthetic(), initialize = { false })
                    .detect(task)
            }
        }
    }

    @Test
    fun cancelThenRestartHasOneNativeOwner() = runBlocking {
        fixture(0) { task ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val exits = AtomicInteger()
            val detector =
                CompositePipeDetector(
                    photos,
                    DetectionConfig.synthetic(),
                    nativeProbe = {
                        val owners = active.incrementAndGet()
                        maximum.updateAndGet { maxOf(it, owners) }
                        entered.countDown()
                        try {
                            check(release.await(5, TimeUnit.SECONDS))
                        } finally {
                            active.decrementAndGet()
                            exits.incrementAndGet()
                        }
                    },
                )
            val first = async(Dispatchers.Default) { detector.detect(task) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            first.cancel()
            val second = async(Dispatchers.Default) { detector.detect(task) }
            delay(100)
            assertFalse(second.isCompleted)
            release.countDown()
            first.join()
            second.await()
            assertEquals(1, maximum.get())
            assertEquals(2, exits.get())
            assertTrue(first.isCancelled)
        }
    }

    private suspend fun expectFailure(reason: DetectionFailureReason, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (f: DetectionFailure) {
            assertEquals(reason, f.reason)
        }
    }
}
