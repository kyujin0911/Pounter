package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import kotlin.math.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class EdgeDrawingInstrumentedTest {
    @Test
    fun rawSixCanonicalSemiaxesDegreesAndInvalidValues() {
        val c = canonicalEdgeDrawing(doubleArrayOf(20.0, 30.0, 8.0, 0.0, 0.0, 0.0), 0)
        assertEquals(EllipseGeometry(20.0, 30.0, 8.0, 8.0, 0.0), c)
        val e = canonicalEdgeDrawing(doubleArrayOf(20.0, 30.0, 0.0, 10.0, 20.0, 0.0), 0)
        assertEquals(20.0, e.semiMajorPx, 1e-9)
        assertEquals(10.0, e.semiMinorPx, 1e-9)
        assertEquals(PI / 2, e.angleRadians, 1e-9)
        val turned = canonicalEdgeDrawing(doubleArrayOf(20.0, 30.0, 0.0, 20.0, 10.0, 450.0), 0)
        assertEquals(e, turned)
        for (s in
            listOf(
                doubleArrayOf(1.0, 2.0, 0.0, 0.0, 0.0, 0.0),
                doubleArrayOf(1.0, 2.0, -1.0, 2.0, 1.0, 0.0),
                doubleArrayOf(Double.NaN, 2.0, 1.0, 0.0, 0.0, 0.0),
                doubleArrayOf(1.0, 2.0, 0.0, 2.0, 1.0, Double.POSITIVE_INFINITY),
            )) {
            assertThrows(IllegalArgumentException::class.java) { canonicalEdgeDrawing(s, 0) }
        }
    }

    @Test
    fun anchorsKeepNeighborsChainAndStableSources() {
        fun observation(id: Int, x: Double, s: Double = 1.0) =
            BObservation(id, EllipseGeometry(x, 50.0, 20.0, 20.0, 0.0), s)
        val input =
            listOf(
                observation(0, 50.0),
                observation(1, 51.5),
                observation(2, 53.0),
                observation(3, 90.0),
            )
        val r = mergeBObservations(input)
        assertEquals(listOf(0, 2, 3), r.map { it.anchor.id })
        assertEquals(listOf(0, 1), r[0].sourceIds)
        assertEquals(r, mergeBObservations(input.reversed()))
        assertEquals(50.0, r[0].anchor.geometry.centerXPx, 0.0)
        assertEquals(
            1,
            mergeBObservations(listOf(observation(0, 50.0, .8), observation(1, 51.0, .9)))[0]
                .anchor
                .id,
        )
    }

    @Test
    fun supportGateAndTraceBudgetDoNotChangeResults() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 240, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.circle(gray, Point(80.0, 80.0), 20, Scalar(220.0), 3)
            val raw =
                doubleArrayOf(80.0, 80.0, 20.0, 0.0, 0.0, 0.0, 80.0, 80.0, 0.0, 65.0, 15.0, 25.0)
            val full = processBGeometry(gray, raw, 25000)
            val bounded = processBGeometry(gray, raw, 0)
            assertEquals(2, full.rawCount)
            assertEquals(1, full.gateCount)
            assertEquals(1, full.qualityCount)
            assertEquals(1.0, full.observations[0].support!!, 0.0)
            assertEquals(full.representatives, bounded.representatives)
            assertEquals(full.rawCount, bounded.rawCount)
            assertEquals(full.gateCount, bounded.gateCount)
            assertTrue(bounded.truncated)
            assertTrue(bounded.observations.isEmpty())
            assertThrows(DetectionFailure::class.java) { processBGeometry(gray, raw, 1, 1) }
            assertThrows(IllegalArgumentException::class.java) {
                processBGeometry(gray, doubleArrayOf(1.0), 1)
            }
        } finally {
            gray.release()
        }
    }

    @Test
    fun detectorNormalZeroInvalidPhotoAndRetryPreserveStatus() = runBlocking {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val fixture =
            java.io.File(context.cacheDir, "ed-fixture-${java.util.UUID.randomUUID()}").apply {
                mkdirs()
            }
        val photos =
            io.github.kyujin0911.pounter.data.local.photo.PhotoStore(
                object : android.content.ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val file = photos.file("b-regression.jpg")
        val bitmap =
            android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.DKGRAY)
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
        }
        bitmap.recycle()
        val detector = EdgeDrawingEvaluationDetector(photos)
        try {
            val task =
                io.github.kyujin0911.pounter.core.model.CountTask(
                    id = "b-regression",
                    photoName = file.name,
                    width = 320,
                    height = 240,
                    createdAt = 0,
                )
            assertTrue(detector.detect(task).markers.isEmpty())
            assertEquals("SUCCESS", detector.status)
            try {
                detector.detect(task.copy(width = 319))
                fail("invalid dimensions accepted")
            } catch (e: DetectionFailure) {
                assertEquals(DetectionFailureReason.INVALID_INPUT, e.reason)
            }
            assertEquals("INVALID_INPUT", detector.status)
            assertNull(detector.lastRun)
            assertTrue(detector.detect(task).markers.isEmpty())
            assertEquals("SUCCESS", detector.status)
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun cancellationAfterNativeDiscardsLateCopyAndSerializesRetry() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 320, CvType.CV_8UC1, Scalar(30.0))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val nextEntered = java.util.concurrent.atomic.AtomicBoolean(false)
        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            val first =
                launch(Dispatchers.Default) {
                    EdgeDrawingTestNative.generate(
                        gray,
                        afterNative = {
                            entered.countDown()
                            check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        },
                    )
                    delivered.set(true)
                }
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            first.cancel()
            val next =
                async(Dispatchers.Default) {
                    EdgeDrawingTestNative.generate(gray, probe = { nextEntered.set(true) })
                }
            delay(100)
            assertFalse(nextEntered.get())
            release.countDown()
            first.join()
            assertTrue(next.await().isEmpty())
            assertFalse(delivered.get())
        } finally {
            release.countDown()
            gray.release()
        }
    }

    @Test
    fun allocatedNativeFailureIsDistinctAndNextDetectorRunRetries() = runBlocking {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val fixture =
            java.io.File(context.cacheDir, "ed-fixture-${java.util.UUID.randomUUID()}").apply {
                mkdirs()
            }
        val photos =
            io.github.kyujin0911.pounter.data.local.photo.PhotoStore(
                object : android.content.ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val file = photos.file("b-exception.jpg")
        val bitmap =
            android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.DKGRAY)
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
        }
        bitmap.recycle()
        var fail = true
        val detector =
            EdgeDrawingEvaluationDetector(
                photos,
                nativeProbe = { if (fail) EdgeDrawingTestNative.failAfterAllocation() },
            )
        val task =
            io.github.kyujin0911.pounter.core.model.CountTask("b-error", file.name, 320, 240, 0)
        try {
            try {
                detector.detect(task)
                fail("native error swallowed")
            } catch (e: DetectionFailure) {
                assertEquals(DetectionFailureReason.PROCESSING, e.reason)
            }
            assertEquals("NATIVE_EXCEPTION", detector.status)
            assertNull(detector.lastRun)
            fail = false
            assertTrue(detector.detect(task).markers.isEmpty())
            assertEquals("SUCCESS", detector.status)
            val allocation =
                EdgeDrawingEvaluationDetector(
                    photos,
                    nativeProbe = { throw OutOfMemoryError("injected allocation failure") },
                )
            try {
                allocation.detect(task)
                fail("allocation swallowed")
            } catch (e: DetectionFailure) {
                assertTrue(e.cause is OutOfMemoryError)
            }
            assertEquals("ALLOCATION_FAILURE", allocation.status)
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun losslessJpegAndOddDecodeScaleKeepSmallLargeAndCropContracts() = runBlocking {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val fixture =
            java.io.File(context.cacheDir, "ed-fixture-${java.util.UUID.randomUUID()}").apply {
                mkdirs()
            }
        val photos =
            io.github.kyujin0911.pounter.data.local.photo.PhotoStore(
                object : android.content.ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val bitmap =
            android.graphics.Bitmap.createBitmap(
                2421,
                1203,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        val paint =
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 6f
            }
        canvas.drawCircle(700f, 500f, 100f, paint)
        canvas.drawCircle(2000f, 800f, 20f, paint)
        val evidence = org.json.JSONArray()
        try {
            for ((label, format) in
                listOf(
                    "lossless" to android.graphics.Bitmap.CompressFormat.PNG,
                    "jpeg95" to android.graphics.Bitmap.CompressFormat.JPEG,
                )) {
                val source = java.io.File(context.cacheDir, "b-scale-$label.source")
                source.outputStream().use { bitmap.compress(format, 95, it) }
                var imported: String? = null
                try {
                    val stored = photos.import(android.net.Uri.fromFile(source))
                    imported = stored.first
                    val task =
                        io.github.kyujin0911.pounter.core.model.CountTask(
                            "scale",
                            stored.first,
                            stored.second,
                            stored.third,
                            0,
                        )
                    val detector = EdgeDrawingEvaluationDetector(photos)
                    val full = detector.detect(task)
                    val input = detector.lastInput!!
                    assertEquals(1211, input.decodeWidth)
                    assertEquals(602, input.decodeHeight)
                    assertEquals(1211.0 / 2421, input.scaleX, 0.0)
                    assertEquals(602.0 / 1203, input.scaleY, 0.0)
                    assertTrue(
                        full.markers.any {
                            hypot((it.x - 700f / 2421) * 2421.0, (it.y - 500f / 1203) * 1203.0) < 3
                        }
                    )
                    assertTrue(
                        full.markers.any {
                            hypot((it.x - 2000f / 2421) * 2421.0, (it.y - 800f / 1203) * 1203.0) < 3
                        }
                    )
                    val crop =
                        detector.detect(
                            task.copy(
                                region =
                                    io.github.kyujin0911.pounter.core.model.Region(.7f, .5f, 1f, 1f)
                            )
                        )
                    assertTrue(crop.markers.isNotEmpty())
                    assertTrue(
                        crop.markers.all {
                            hypot((it.x - 2000f / 2421) * 2421.0, (it.y - 800f / 1203) * 1203.0) < 3
                        }
                    )
                    assertTrue(
                        detector.lastRun!!.representatives.all {
                            it.anchor.geometry.semiMinorPx >= 7 &&
                                it.anchor.geometry.semiMajorPx <= 80
                        }
                    )
                    evidence.put(
                        org.json
                            .JSONObject()
                            .put("label", label)
                            .put("sourceSha256", bSha(source.readBytes()))
                            .put("storedSha256", bSha(photos.file(stored.first).readBytes()))
                            .put("fullGraySha256", input.graySha256)
                            .put("fullMarkers", full.markers.size)
                            .put("cropMarkers", crop.markers.size)
                            .put("scaleX", input.scaleX)
                            .put("scaleY", input.scaleY)
                    )
                } finally {
                    imported?.let { photos.file(it).delete() }
                    source.delete()
                }
            }
            java.io
                .File(context.cacheDir, "b-compression-scale-task4.json")
                .writeText(evidence.toString(2))
        } finally {
            bitmap.recycle()
            fixture.deleteRecursively()
        }
    }

    @Test
    fun outsideSupportSamplesStayInDenominatorAndRimAnglesAreCanonical() {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(180, 240, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.circle(gray, Point(0.0, 80.0), 20, Scalar(220.0), 3)
            val run = processBGeometry(gray, doubleArrayOf(0.0, 80.0, 20.0, 0.0, 0.0, 0.0))
            assertEquals(37.0 / 72, run.observations.single().support!!, 0.0)
            assertEquals(0, run.qualityCount)
            fun o(id: Int, g: EllipseGeometry) = BObservation(id, g, 1.0)
            val a = canonicalEdgeDrawing(doubleArrayOf(50.0, 50.0, 0.0, 30.0, 15.0, 25.0), 0)
            val b = canonicalEdgeDrawing(doubleArrayOf(50.0, 50.0, 0.0, 15.0, 30.0, 115.0), 0)
            assertEquals(1, mergeBObservations(listOf(o(0, a), o(1, b))).size)
            assertEquals(
                2,
                mergeBObservations(
                        listOf(o(0, a), o(1, a.copy(angleRadians = a.angleRadians + PI / 2)))
                    )
                    .size,
            )
            val circle = EllipseGeometry(50.0, 50.0, 20.0, 20.0, 0.0)
            assertEquals(
                1,
                mergeBObservations(listOf(o(0, circle), o(1, circle.copy(angleRadians = 1.4)))).size,
            )
        } finally {
            gray.release()
        }
    }

    @Test
    fun allOwnedNativeAllocationsReleaseOnSuccessAndEveryInjectedFailure() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 320, CvType.CV_8UC1, Scalar(30.0))
        try {
            Imgproc.ellipse(
                gray,
                Point(160.0, 120.0),
                Size(65.0, 35.0),
                0.0,
                0.0,
                360.0,
                Scalar(220.0),
                3,
            )
            EdgeDrawingTestNative.generate(
                gray
            ) // load same native library before invoking test entry
            val pixels = ByteArray(320 * 240)
            gray.get(0, 0, pixels)
            val laterAllocation = EdgeDrawingTestNative.auditOwnership(pixels, 320, 240, 7)
            assertEquals(1, laterAllocation[2])
            assertEquals("Join scratch leaks when later allocation throws", 0, laterAllocation[0])
            val success = EdgeDrawingTestNative.auditOwnership(pixels, 320, 240, -1)
            assertEquals("normal-run owned scratch allocation leak", 0, success[0])
            assertEquals(0, success[2])
            assertTrue(success[1] > 10)
            for (nth in 1..success[1]) {
                val failure = EdgeDrawingTestNative.auditOwnership(pixels, 320, 240, nth)
                assertEquals("scratch leak at allocation $nth", 0, failure[0])
                assertEquals("fault not reached at allocation $nth", 1, failure[2])
            }
            val retry = EdgeDrawingTestNative.auditOwnership(pixels, 320, 240, -1)
            assertEquals(0, retry[0])
            assertEquals(success[1], retry[1])
            assertEquals(0, retry[2])
            java.io
                .File(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .targetContext
                        .cacheDir,
                    "b-native-ownership-task4.json",
                )
                .writeText(
                    org.json
                        .JSONObject()
                        .put("attempts", success[1])
                        .put("faultsChecked", success[1])
                        .put("liveDelta", retry[0])
                        .put(
                            "scope",
                            "all instrumented ED new/object/array sites reached by fixed ellipse fixture; not OpenCV allocator/real OS OOM",
                        )
                        .toString()
                )
        } finally {
            gray.release()
        }
    }

    @Test
    fun jniBlankRepeatAndAllocatedExceptionRetry() = runBlocking {
        assertTrue(OpenCVLoader.initLocal())
        val gray = Mat(240, 320, CvType.CV_8UC1, Scalar(30.0))
        try {
            assertTrue(EdgeDrawingTestNative.generate(gray).isEmpty())
            Imgproc.ellipse(
                gray,
                Point(160.0, 120.0),
                Size(65.0, 35.0),
                0.0,
                0.0,
                360.0,
                Scalar(220.0),
                3,
            )
            val raw = EdgeDrawingTestNative.generate(gray)
            assertTrue(raw.isNotEmpty())
            assertTrue(
                (0 until raw.size step 6).any { hypot(raw[it] - 160, raw[it + 1] - 120) < 3 }
            )
            repeat(20) {
                assertThrows(IllegalArgumentException::class.java) {
                    EdgeDrawingTestNative.detect(byteArrayOf(0), 320, 240)
                }
                assertThrows(RuntimeException::class.java) {
                    EdgeDrawingTestNative.failAfterAllocation()
                }
                assertArrayEquals(raw, EdgeDrawingTestNative.generate(gray), 0.0)
            }
        } finally {
            gray.release()
        }
    }
}
