package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Real PhotoStore/detector with explicitly injected fixed raw geometry; NOT a native accuracy test.
 */
class BDetectorMergeContractInstrumentedTest {
    private suspend fun fixture(block: suspend (PhotoStore, CountTask) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Unregistered fixture images must not share the app's startup orphan-cleanup directory.
        val testFiles =
            File(context.cacheDir, "merge-contract-${UUID.randomUUID()}").apply { mkdirs() }
        val photos =
            PhotoStore(
                object : ContextWrapper(context) {
                    override fun getFilesDir(): File = testFiles
                }
            )
        val bitmap = Bitmap.createBitmap(850, 650, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 30, 30))
        val paint =
            Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
        for (n in 0 until 301) canvas.drawCircle(
            25f + (n % 20) * 40,
            25f + (n / 20) * 40,
            12f,
            paint,
        )
        val file = photos.file("merge-contract-${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        try {
            block(photos, CountTask("merge-contract", file.name, 850, 650, 0))
        } finally {
            file.delete()
            testFiles.deleteRecursively()
        }
    }

    private fun raw(n: Int) =
        DoubleArray(n * 6).also { out ->
            for (i in 0 until n) {
                out[i * 6] = 25.0 + (i % 20) * 40
                out[i * 6 + 1] = 25.0 + (i / 20) * 40
                out[i * 6 + 2] = 12.0
            }
        }

    @Test
    fun actualDetectorZero300301SaturationFailureRetryWithFixedGenerator() = runBlocking {
        fixture { photos, task ->
            var count = 0
            val detector =
                EdgeDrawingEvaluationDetector(
                    photos,
                    objectPolicy = BObjectPolicy.ANNULUS_90,
                    generateGeometry = { raw(count) },
                )
            for (n in listOf(0, 300)) {
                count = n
                assertEquals(n, detector.detect(task).markers.size)
                assertEquals("SUCCESS", detector.status)
            }
            count = 301
            val e =
                try {
                    detector.detect(task)
                    error("301 accepted")
                } catch (e: DetectionFailure) {
                    e
                }
            assertEquals(DetectionFailureReason.COUNT_LIMIT, e.reason)
            assertEquals("COUNT_LIMIT", detector.status)
            assertEquals(301, detector.lastRun!!.representatives.size)
            val saturated =
                EdgeDrawingEvaluationDetector(
                    photos,
                    objectPolicy = BObjectPolicy.ANNULUS_90,
                    maxPairChecks = 0,
                    generateGeometry = { raw(2) },
                )
            try {
                saturated.detect(task)
                fail("pair exhaustion became success")
            } catch (e: DetectionFailure) {
                assertEquals(DetectionFailureReason.CANDIDATE_SATURATION, e.reason)
            }
            assertEquals("CANDIDATE_SATURATION", saturated.status)
            assertNull(saturated.lastRun)
            count = 0
            assertTrue(detector.detect(task).markers.isEmpty())
            try {
                detector.detect(task.copy(width = 849))
                fail("invalid input accepted")
            } catch (e: DetectionFailure) {
                assertEquals(DetectionFailureReason.INVALID_INPUT, e.reason)
            }
            assertNull(detector.lastRun)
            assertTrue(detector.detect(task).markers.isEmpty())
            assertEquals("SUCCESS", detector.status)
        }
    }

    @Test
    fun actualDetectorCancellationDiscardsLateFixedResultThenRetries() = runBlocking {
        fixture { photos, task ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var first = true
            val detector =
                EdgeDrawingEvaluationDetector(
                    photos,
                    objectPolicy = BObjectPolicy.ANNULUS_90,
                    generateGeometry = {
                        if (first) {
                            first = false
                            entered.complete(Unit)
                            withContext(NonCancellable) { release.await() }
                        }
                        raw(1)
                    },
                )
            var delivered = false
            val job = launch {
                detector.detect(task)
                delivered = true
            }
            entered.await()
            job.cancel()
            release.complete(Unit)
            job.join()
            assertFalse(delivered)
            assertEquals("CANCELLED", detector.status)
            assertNull(detector.lastRun)
            assertEquals(1, detector.detect(task).markers.size)
            assertEquals("SUCCESS", detector.status)
        }
    }
}
