package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*

/**
 * External development inputs only; evaluates actual PhotoStore/JNI and fixed replay separately.
 */
class BSharedEvaluationInstrumentedTest {
    private fun raw(shapes: JSONArray) =
        DoubleArray(shapes.length() * 6).also { out ->
            for (i in 0 until shapes.length()) {
                val s = shapes.getJSONArray(i)
                out[i * 6] = s.getDouble(0)
                out[i * 6 + 1] = s.getDouble(1)
                out[i * 6 + 3] = s.getDouble(2)
                out[i * 6 + 4] = s.getDouble(3)
                out[i * 6 + 5] = s.getDouble(4) * 180 / PI
            }
        }

    private fun encode(run: BRun) =
        JSONObject()
            .put("raw", run.rawCount)
            .put("gate", run.gateCount)
            .put("quality", run.qualityCount)
            .put("sameRimCount", run.sameRimCount)
            .put(
                "representatives",
                JSONArray(
                    run.representatives.map { r ->
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
                "radial",
                run.radialObject?.let { r ->
                    JSONObject()
                        .put("seedCount", r.seedCount)
                        .put("pairChecks", r.pairChecks)
                        .put("truncated", r.truncated)
                        .put("elapsedNanos", r.elapsedNanos)
                        .put(
                            "decisions",
                            JSONArray(
                                r.decisions.map {
                                    JSONObject()
                                        .put("reason", it.reason)
                                        .put("sourceIds", JSONArray(it.sourceIds))
                                }
                            ),
                        )
                } ?: JSONObject.NULL,
            )

    @Test
    fun fixedAndPhotoStoreNativeTenRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arg = InstrumentationRegistry.getArguments().getString("radialEvaluationRoot")
        assumeTrue("explicit external development inputs required", arg != null)
        val root = File(arg!!)
        val output = File(context.cacheDir, "shared-edge-v1-output").apply { mkdirs() }
        val fixture =
            File(context.cacheDir, "radial-photos-${UUID.randomUUID()}").apply { mkdirs() }
        val photos =
            PhotoStore(
                object : ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val initStarted = System.nanoTime()
        assertTrue(OpenCVLoader.initLocal())
        val initMillis = (System.nanoTime() - initStarted) / 1_000_000.0
        val jniLoadStarted = System.nanoTime()
        System.loadLibrary("pounter_ed_test")
        val jniLoadMillis = (System.nanoTime() - jniLoadStarted) / 1_000_000.0
        val blank = Mat(8, 8, CvType.CV_8UC1, Scalar(0.0))
        val firstNativeStarted = System.nanoTime()
        val firstNativeMillis =
            try {
                assertTrue(EdgeDrawingTestNative.generate(blank).isEmpty())
                (System.nanoTime() - firstNativeStarted) / 1_000_000.0
            } finally {
                blank.release()
            }
        try {
            val rows = JSONArray(File(root, "rows.json").readText())
            for (n in 0 until rows.length()) {
                val row = rows.getJSONObject(n)
                val sample = row.getJSONObject("sample")
                val sid = row.getString("sampleId") + "-" + row.getString("scope")
                val ref = row.getJSONObject("comparisonGray")
                val bytes = File(root, ref.getString("file")).readBytes()
                assertEquals(ref.getString("sha256"), bSha(bytes))
                val gray = Mat(ref.getInt("height"), ref.getInt("width"), CvType.CV_8UC1)
                val region =
                    sample.getJSONArray("region").let {
                        Region(
                            it.getDouble(0).toFloat(),
                            it.getDouble(1).toFloat(),
                            it.getDouble(2).toFloat(),
                            it.getDouble(3).toFloat(),
                        )
                    }
                val source = File(root, sample.getString("photo"))
                assertEquals(sample.getString("sourceSha256"), bSha(source.readBytes()))
                val photo = photos.import(Uri.fromFile(source))
                val task =
                    CountTask(sid, photo.first, photo.second, photo.third, 0, region = region)
                try {
                    gray.put(0, 0, bytes)
                    val geometry = raw(row.getJSONArray("nativeShapes"))
                    val methods =
                        listOf(
                            "BASELINE",
                            "ANNULUS_90",
                            "RADIAL_OBJECTS_V2",
                            "LOCAL_OBJECTS_V1",
                            "SHARED_EDGES_V1",
                        )
                    for (name in methods.drop(n % 5) + methods.take(n % 5)) {
                        val radial = name == "RADIAL_OBJECTS_V2"
                        val local = name == "LOCAL_OBJECTS_V1" || name == "SHARED_EDGES_V1"
                        val shared = name == "SHARED_EDGES_V1"
                        val policy = if (name == "ANNULUS_90") BObjectPolicy.ANNULUS_90 else null
                        val fixed =
                            processBGeometry(
                                gray,
                                geometry,
                                objectPolicy = policy,
                                radialObjects = radial,
                                localObjects = local,
                                sharedEdges = shared,
                            )
                        val fixedTimes = mutableListOf<Double>()
                        repeat(3) {
                            val started = System.nanoTime()
                            val timed =
                                processBGeometry(
                                    gray,
                                    geometry,
                                    entryBudget = 0,
                                    objectPolicy = policy,
                                    radialObjects = radial,
                                    localObjects = local,
                                    sharedEdges = shared,
                                )
                            fixedTimes += (System.nanoTime() - started) / 1_000_000.0
                            assertEquals(fixed.representatives, timed.representatives)
                        }
                        val detector =
                            EdgeDrawingEvaluationDetector(
                                photos,
                                objectPolicy = policy,
                                radialObjects = radial,
                                localObjects = local,
                                sharedEdges = shared,
                            )
                        val actualTimes = mutableListOf<Double>()
                        var result: Detection? = null
                        var reason: String? = null
                        repeat(4) {
                            result = null
                            reason = null
                            val started = System.nanoTime()
                            try {
                                result = detector.detect(task)
                            } catch (e: DetectionFailure) {
                                reason = e.reason.name
                            }
                            actualTimes += (System.nanoTime() - started) / 1_000_000.0
                            assertEquals(ref.getString("sha256"), detector.lastInput!!.graySha256)
                            val live = detector.lastRun!!
                            assertEquals(
                                fixed.representatives.map { it.anchor.id to it.sourceIds },
                                live.representatives.map { it.anchor.id to it.sourceIds },
                            )
                            for ((a, b) in fixed.representatives.zip(live.representatives)) {
                                val x = a.anchor.geometry
                                val y = b.anchor.geometry
                                assertEquals(x.centerXPx, y.centerXPx, 1e-9)
                                assertEquals(x.centerYPx, y.centerYPx, 1e-9)
                                assertEquals(x.semiMajorPx, y.semiMajorPx, 1e-9)
                                assertEquals(x.semiMinorPx, y.semiMinorPx, 1e-9)
                                assertEquals(x.angleRadians, y.angleRadians, 1e-9)
                            }
                        }
                        val live = detector.lastRun!!
                        val roi = row.getJSONArray("roiPixels")
                        val transform =
                            AnalysisTransform(
                                row.getInt("storedWidth"),
                                row.getInt("storedHeight"),
                                0,
                                0,
                                row.getInt("decodeWidth").toDouble() / row.getInt("storedWidth"),
                                row.getInt("decodeHeight").toDouble() / row.getInt("storedHeight"),
                            )
                        val diagnostic =
                            live.representatives
                                .map { r ->
                                    toMarker(
                                        r.anchor.geometry.copy(
                                            centerXPx = r.anchor.geometry.centerXPx + roi.getInt(0),
                                            centerYPx = r.anchor.geometry.centerYPx + roi.getInt(1),
                                        ),
                                        transform,
                                        "b-${r.anchor.id}",
                                    )
                                }
                                .filter { region.contains(it.x, it.y) }
                        if (diagnostic.size > 300) {
                            assertEquals("COUNT_LIMIT", reason)
                            assertNull(result)
                        } else {
                            assertNull(reason)
                            assertEquals(diagnostic, result!!.markers)
                        }
                        val expected =
                            sample.getJSONArray("expected").let { a ->
                                (0 until a.length()).map {
                                    a.getJSONObject(it).let { e ->
                                        Marker(
                                            e.getString("id"),
                                            e.getDouble("x").toFloat(),
                                            e.getDouble("y").toFloat(),
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
                                diagnostic,
                                sample.getInt("referenceWidth"),
                                sample.getInt("referenceHeight"),
                                tolerances,
                            )
                        val report =
                            encode(live)
                                .put("sample", sid)
                                .put("policy", name)
                                .put("graySha256", detector.lastInput!!.graySha256)
                                .put("actualStatus", detector.status)
                                .put("failureReason", reason ?: JSONObject.NULL)
                                .put("returnedCount", result?.markers?.size ?: JSONObject.NULL)
                                .put("photoStoreNativeExecuted", true)
                                .put("openCvInitInvocationMillis", initMillis)
                                .put("jniLibraryLoadMillis", jniLoadMillis)
                                .put("firstBlankNativeInvocationMillis", firstNativeMillis)
                                .put(
                                    "methodOrder",
                                    JSONArray(methods.drop(n % 5) + methods.take(n % 5)),
                                )
                                .put("fixedPostprocessMillis", JSONArray(fixedTimes))
                                .put("actualFirstRepeatMillis", JSONArray(actualTimes))
                                .put(
                                    "markers",
                                    JSONArray(
                                        diagnostic.map {
                                            JSONObject()
                                                .put("id", it.id)
                                                .put("x", it.x.toDouble())
                                                .put("y", it.y.toDouble())
                                        }
                                    ),
                                )
                                .put(
                                    "metrics",
                                    JSONObject()
                                        .put("M", matched.metrics.matched)
                                        .put("FN", matched.metrics.missing)
                                        .put("FP", matched.metrics.falsePositive)
                                        .put("C", matched.metrics.correctionCount)
                                        .put("count", diagnostic.size)
                                        .put("pairs", JSONObject(matched.expectedToActualIds)),
                                )
                        File(output, "$sid-$name.json").writeText(report.toString(2))
                    }
                } finally {
                    gray.release()
                    photos.file(photo.first).delete()
                }
            }
        } finally {
            fixture.deleteRecursively()
        }
    }
}
