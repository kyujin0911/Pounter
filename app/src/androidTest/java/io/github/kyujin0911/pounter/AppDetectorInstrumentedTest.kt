package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.opencv.core.*

/** App APK classes/native are used directly. Inputs/truth/output live outside the app package. */
class AppDetectorInstrumentedTest {
    @Test
    fun appApkPhotoStoreNativeTenRowsPreserveMarkersAndSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root =
            File(
                requireNotNull(
                    InstrumentationRegistry.getArguments().getString("radialEvaluationRoot")
                )
            )
        val output = File(context.cacheDir, "app-integration-output").apply { mkdirs() }
        val fixture = File(context.cacheDir, "app-parity-${UUID.randomUUID()}").apply { mkdirs() }
        val photos =
            PhotoStore(
                object : ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val app = SupportedFacesPipeDetector(photos)
        val expectedCounts =
            listOf(
                listOf(151, 0, 90, 90),
                listOf(151, 0, 19, 19),
                listOf(54, 1, 4, 5),
                listOf(54, 1, 2, 3),
                listOf(37, 3, 38, 41),
                listOf(37, 3, 33, 36),
                listOf(18, 0, 7, 7),
                listOf(18, 0, 8, 8),
                listOf(19, 18, 14, 32),
                listOf(19, 18, 14, 32),
            )
        val rows = JSONArray(File(root, "rows.json").readText())
        assertEquals(10, rows.length())
        try {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val sample = row.getJSONObject("sample")
                val sid = row.getString("sampleId") + "-" + row.getString("scope")
                val source = File(root, sample.getString("photo"))
                assertEquals(sample.getString("sourceSha256"), bSha(source.readBytes()))
                val imported = photos.import(Uri.fromFile(source))
                val region =
                    sample.getJSONArray("region").let {
                        Region(
                            it.getDouble(0).toFloat(),
                            it.getDouble(1).toFloat(),
                            it.getDouble(2).toFloat(),
                            it.getDouble(3).toFloat(),
                        )
                    }
                val task =
                    CountTask(
                        UUID.randomUUID().toString(),
                        imported.first,
                        imported.second,
                        imported.third,
                        0,
                        region = region,
                    )
                val times = mutableListOf<Double>()
                var result: Detection? = null
                val stages = mutableListOf<AnalysisStage>()
                repeat(4) {
                    val start = System.nanoTime()
                    val next = app.detect(task) { stages += it }
                    times += (System.nanoTime() - start) / 1_000_000.0
                    if (result != null) assertEquals(result!!.markers, next.markers)
                    result = next
                }
                assertEquals(
                    List(4) { listOf(AnalysisStage.WAITING, AnalysisStage.PROCESSING) }.flatten(),
                    stages,
                )
                val evaluation =
                    EdgeDrawingEvaluationDetector(
                        photos,
                        localObjects = true,
                        sharedEdges = true,
                        supportedFaces = true,
                    )
                val reference = evaluation.detect(task)
                assertEquals(
                    reference.markers,
                    result!!.markers,
                ) // includes radius/manual/id and exact Float coordinates
                assertEquals(reference.analysisMetadata, result!!.analysisMetadata)
                val ref = row.getJSONObject("comparisonGray")
                assertEquals(ref.getString("sha256"), evaluation.lastInput!!.graySha256)
                val gray = Mat(ref.getInt("height"), ref.getInt("width"), CvType.CV_8UC1)
                val representatives =
                    try {
                        val bytes = File(root, ref.getString("file")).readBytes()
                        assertEquals(ref.getString("sha256"), bSha(bytes))
                        gray.put(0, 0, bytes)
                        // Independently invoke the app native library at the gray boundary.
                        val native = EdgeDrawingNative.generate(gray)
                        val shapes = row.getJSONArray("nativeShapes")
                        assertEquals(shapes.length(), native.size / 6)
                        for (n in 0 until shapes.length()) {
                            val g = canonicalEdgeDrawing(native, n * 6)
                            val s = shapes.getJSONArray(n)
                            listOf(
                                    g.centerXPx,
                                    g.centerYPx,
                                    g.semiMajorPx,
                                    g.semiMinorPx,
                                    g.angleRadians,
                                )
                                .forEachIndexed { k, v -> assertEquals(s.getDouble(k), v, 1e-9) }
                        }
                        selectSupportedFacesGeometry(gray, native, {})
                    } finally {
                        gray.release()
                    }
                assertEquals(evaluation.lastRun!!.representatives, representatives)
                val expected =
                    sample.getJSONArray("expected").let { a ->
                        (0 until a.length()).map { n ->
                            a.getJSONObject(n).let {
                                Marker(
                                    it.getString("id"),
                                    it.getDouble("x").toFloat(),
                                    it.getDouble("y").toFloat(),
                                    .01f,
                                )
                            }
                        }
                    }
                val tolerances =
                    sample.getJSONObject("tolerancesPx").let { t ->
                        t.keys().asSequence().associateWith { t.getDouble(it) }
                    }
                val matched =
                    matchDetectionsInReferencePixels(
                        expected,
                        result!!.markers,
                        sample.getInt("referenceWidth"),
                        sample.getInt("referenceHeight"),
                        tolerances,
                    )
                val m = matched.metrics
                assertEquals(
                    expectedCounts[i],
                    listOf(m.matched, m.missing, m.falsePositive, m.correctionCount),
                )
                val report =
                    JSONObject()
                        .put("sample", sid)
                        .put("status", "SUCCESS")
                        .put("failureReason", JSONObject.NULL)
                        .put("actualFirstRepeatMillis", JSONArray(times))
                        .put("reportedAnalysisMillis", result!!.millis)
                        .put(
                            "markers",
                            JSONArray(
                                result!!.markers.map {
                                    JSONObject()
                                        .put("id", it.id)
                                        .put("x", it.x.toDouble())
                                        .put("y", it.y.toDouble())
                                        .put("radius", it.radius.toDouble())
                                        .put("manual", it.manual)
                                }
                            ),
                        )
                        .put(
                            "representatives",
                            JSONArray(
                                representatives.map { r ->
                                    val g = r.anchor.geometry
                                    JSONObject()
                                        .put("anchorId", r.anchor.id)
                                        .put("sourceIds", JSONArray(r.sourceIds))
                                        .put(
                                            "geometry",
                                            JSONArray(
                                                listOf(
                                                    g.centerXPx,
                                                    g.centerYPx,
                                                    g.semiMajorPx,
                                                    g.semiMinorPx,
                                                    g.angleRadians,
                                                )
                                            ),
                                        )
                                        .put("support", r.anchor.support)
                                }
                            ),
                        )
                        .put(
                            "metrics",
                            JSONObject()
                                .put("M", m.matched)
                                .put("FN", m.missing)
                                .put("FP", m.falsePositive)
                                .put("C", m.correctionCount)
                                .put("pairs", JSONObject(matched.expectedToActualIds)),
                        )
                        .put("graySha256", evaluation.lastInput!!.graySha256)
                        .put("evaluationMarkersExact", true)
                        .put("nativeGeometryAndSourcesExact", true)
                File(output, "$sid.json").writeText(report.toString(2))
                photos.file(imported.first).delete()
            }
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun appZeroReadInvalidCancelRetryAndExplicitLimits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "app-contract-${UUID.randomUUID()}").apply { mkdirs() }
        val photos =
            PhotoStore(
                object : ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val bitmap =
            Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.DKGRAY)
            }
        photos.file("blank.jpg").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        bitmap.recycle()
        val app = SupportedFacesPipeDetector(photos)
        val task = CountTask("blank", "blank.jpg", 320, 240, 0)
        suspend fun failure(reason: DetectionFailureReason, block: suspend () -> Unit) {
            try {
                block()
                fail("expected $reason")
            } catch (e: DetectionFailure) {
                assertEquals(reason, e.reason)
            }
        }
        try {
            assertTrue(app.detect(task).markers.isEmpty())
            failure(DetectionFailureReason.PHOTO_READ) {
                app.detect(task.copy(photoName = "missing.jpg"))
            }
            failure(DetectionFailureReason.INVALID_INPUT) { app.detect(task.copy(width = 319)) }
            val cancelled = async(start = CoroutineStart.LAZY) { app.detect(task) }
            cancelled.cancel()
            try {
                cancelled.await()
                fail("cancel ignored")
            } catch (_: CancellationException) {}
            assertTrue(app.detect(task).markers.isEmpty())
            val g = EllipseGeometry(100.0, 100.0, 10.0, 10.0, 0.0)
            val reps = List(301) { BRepresentative(BObservation(it, g, 1.0), listOf(it)) }
            // Deliberate selection injection tests the shared count/failure contract, not image
            // quality.
            failure(DetectionFailureReason.COUNT_LIMIT) {
                analyzeEdgeDrawing(
                    photos,
                    task,
                    "contract",
                    { DoubleArray(0) },
                    { _, _, _ -> reps },
                )
            }
            val gray = Mat(64, 64, CvType.CV_8UC1, Scalar(0.0))
            try {
                failure(DetectionFailureReason.CANDIDATE_SATURATION) {
                    processBBaseGeometry(gray, DoubleArray(10001 * 6))
                }
            } finally {
                gray.release()
            }
            assertTrue(app.detect(task).markers.isEmpty())
        } finally {
            fixture.deleteRecursively()
        }
    }
}
