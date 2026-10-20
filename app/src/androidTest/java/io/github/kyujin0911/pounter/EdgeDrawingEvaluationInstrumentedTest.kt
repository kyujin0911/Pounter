package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import kotlin.math.PI
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*

private fun geometryJson(g: EllipseGeometry) =
    JSONArray(listOf(g.centerXPx, g.centerYPx, g.semiMajorPx, g.semiMinorPx, g.angleRadians))

private fun markerJson(m: Marker) =
    JSONObject()
        .put("id", m.id)
        .put("x", m.x.toDouble())
        .put("y", m.y.toDouble())
        .put("radius", m.radius.toDouble())

private fun runJson(r: BRun) =
    JSONObject()
        .put("rawCount", r.rawCount)
        .put("gateCount", r.gateCount)
        .put("qualityCount", r.qualityCount)
        .put("truncated", r.truncated)
        .put(
            "observations",
            JSONArray(
                r.observations.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("geometry", geometryJson(it.geometry))
                        .put("support", it.support ?: JSONObject.NULL)
                        .put("stage", it.stage)
                }
            ),
        )
        .put(
            "representatives",
            JSONArray(
                r.representatives.map {
                    JSONObject()
                        .put("anchorId", it.anchor.id)
                        .put("sourceIds", JSONArray(it.sourceIds))
                        .put("geometry", geometryJson(it.anchor.geometry))
                        .put("support", it.anchor.support)
                }
            ),
        )

/** Only explicit local input arguments activate these development runs. */
class EdgeDrawingEvaluationInstrumentedTest {
    @Test
    fun evaluateFixedGrayAndPhotoStoreTenRows() = runBlocking {
        val rootArg = InstrumentationRegistry.getArguments().getString("edgeDrawingRoot")
        assumeTrue("external development inputs required", rootArg != null)
        val root = File(rootArg!!)
        val rows = JSONArray(File(root, "rows.json").readText())
        val context =
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun getFilesDir() =
                    File(super.getCacheDir(), "edge-drawing-evaluation").apply { mkdirs() }
            }
        assertTrue(OpenCVLoader.initLocal())
        val photos = PhotoStore(context)
        val detector = EdgeDrawingEvaluationDetector(photos)
        val summary = JSONArray()
        val output = File(context.filesDir, "task4-output").apply { mkdirs() }
        for (n in 0 until rows.length()) {
            val row = rows.getJSONObject(n)
            val sid = row.getString("sampleId") + "-" + row.getString("scope")
            val report =
                JSONObject()
                    .put("sampleId", sid)
                    .put("version", B_VERSION)
                    .put("annotation", "v6 DRAFT/frozen=false")
            fun publish() {
                File(output, "summary.json").writeText(summary.toString(2))
            }
            summary.put(report)
            publish()
            var imported: String? = null
            try {
                val grayRef = row.getJSONObject("comparisonGray")
                val bytes = File(root, grayRef.getString("file")).readBytes()
                assertEquals(grayRef.getString("sha256"), bSha(bytes))
                val gray = Mat(grayRef.getInt("height"), grayRef.getInt("width"), CvType.CV_8UC1)
                val comparison =
                    recordEvaluationStage(report, "fixedGray", ::publish) {
                        try {
                            gray.put(0, 0, bytes)
                            val shapes = row.getJSONArray("nativeShapes")
                            val raw = DoubleArray(shapes.length() * 6)
                            for (i in 0 until shapes.length()) {
                                val s = shapes.getJSONArray(i)
                                raw[i * 6] = s.getDouble(0)
                                raw[i * 6 + 1] = s.getDouble(1)
                                raw[i * 6 + 3] = s.getDouble(2)
                                raw[i * 6 + 4] = s.getDouble(3)
                                raw[i * 6 + 5] = s.getDouble(4) * 180 / PI
                            }
                            val fixed = processBGeometry(gray, raw)
                            val actualRaw = EdgeDrawingTestNative.generate(gray)
                            assertEquals(shapes.length() * 6, actualRaw.size)
                            for (i in 0 until shapes.length()) {
                                val e = geometryJson(canonicalEdgeDrawing(actualRaw, i * 6))
                                val old = shapes.getJSONArray(i)
                                for (j in 0 until 5) assertEquals(
                                    "$sid native $i/$j",
                                    old.getDouble(j),
                                    e.getDouble(j),
                                    1e-9,
                                )
                            }
                            val live = processBGeometry(gray, actualRaw)
                            File(output, "$sid-fixed.json").writeText(runJson(fixed).toString())
                            File(output, "$sid-live.json").writeText(runJson(live).toString())
                            JSONObject()
                                .put("inputSha256", bSha(bytes))
                                .put("rawCount", live.rawCount)
                                .put("count", live.representatives.size)
                                .put("nativeParity", "PASS")
                        } finally {
                            gray.release()
                        }
                    }
                val sample = row.getJSONObject("sample")
                val region =
                    sample.getJSONArray("region").let {
                        Region(
                            it.getDouble(0).toFloat(),
                            it.getDouble(1).toFloat(),
                            it.getDouble(2).toFloat(),
                            it.getDouble(3).toFloat(),
                        )
                    }
                val importedStage =
                    recordEvaluationStage(report, "import", ::publish) {
                        val source = File(root, sample.getString("photo"))
                        assertEquals(sample.getString("sourceSha256"), bSha(source.readBytes()))
                        val photo = photos.import(Uri.fromFile(source))
                        imported = photo.first
                        JSONObject()
                            .put("photo", photo.first)
                            .put("width", photo.second)
                            .put("height", photo.third)
                            .put("storedSha256", bSha(photos.file(photo.first).readBytes()))
                    }
                if (importedStage != null) {
                    val task =
                        CountTask(
                            sid,
                            imported!!,
                            importedStage.getInt("width"),
                            importedStage.getInt("height"),
                            0,
                            region = region,
                        )
                    recordEvaluationStage(report, "primary", ::publish) {
                        val detection = detector.detect(task)
                        JSONObject()
                            .put("markers", JSONArray(detection.markers.map(::markerJson)))
                            .put("elapsedMillis", detection.millis)
                    }
                    report.put("detectorStatus", detector.status)
                    detector.lastInput?.let { input ->
                        assertEquals(grayRef.getString("sha256"), input.graySha256)
                        report.put(
                            "input",
                            JSONObject()
                                .put("graySha256", input.graySha256)
                                .put("decodeWidth", input.decodeWidth)
                                .put("decodeHeight", input.decodeHeight)
                                .put("roi", JSONArray(input.roi))
                                .put("scaleX", input.scaleX)
                                .put("scaleY", input.scaleY),
                        )
                    }
                    detector.lastRun?.let { run ->
                        File(output, "$sid-photo.json").writeText(runJson(run).toString())
                        val input = detector.lastInput!!
                        val transform =
                            AnalysisTransform(
                                task.width,
                                task.height,
                                0,
                                0,
                                input.scaleX,
                                input.scaleY,
                            )
                        val diagnostic =
                            run.representatives.map { r ->
                                toMarker(
                                    r.anchor.geometry.copy(
                                        centerXPx = r.anchor.geometry.centerXPx + input.roi[0],
                                        centerYPx = r.anchor.geometry.centerYPx + input.roi[1],
                                    ),
                                    transform,
                                    "b-${r.anchor.id}",
                                )
                            }
                        File(output, "$sid-markers.json")
                            .writeText(JSONArray(diagnostic.map(::markerJson)).toString())
                        val expected =
                            sample.getJSONArray("expected").let { a ->
                                (0 until a.length()).map { i ->
                                    a.getJSONObject(i).let {
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
                        val metric =
                            matchDetectionsInReferencePixels(
                                expected,
                                diagnostic,
                                sample.getInt("referenceWidth"),
                                sample.getInt("referenceHeight"),
                                tolerances,
                            )
                        report.put(
                            "beforeLimitDiagnostic",
                            JSONObject()
                                .put("M", metric.metrics.matched)
                                .put("FN", metric.metrics.missing)
                                .put("FP", metric.metrics.falsePositive)
                                .put("correction", metric.metrics.correctionCount)
                                .put("count", diagnostic.size)
                                .put("pairs", JSONObject(metric.expectedToActualIds)),
                        )
                    }
                    recordEvaluationStage(report, "traceOff", ::publish) {
                        if (detector.status == "COUNT_LIMIT")
                            throw EvaluationStageSkipped(
                                "primary COUNT_LIMIT; before-limit trace geometry retained"
                            )
                        val noTrace = EdgeDrawingEvaluationDetector(photos, 0)
                        val off = noTrace.detect(task)
                        val original =
                            report
                                .getJSONObject("primary")
                                .getJSONObject("result")
                                .getJSONArray("markers")
                        assertEquals(
                            original.toString(),
                            JSONArray(off.markers.map(::markerJson)).toString(),
                        )
                        JSONObject()
                            .put("geometryEqual", true)
                            .put("rawCount", noTrace.lastRun!!.rawCount)
                            .put("gateCount", noTrace.lastRun!!.gateCount)
                            .put("qualityCount", noTrace.lastRun!!.qualityCount)
                    }
                }
                assertNotNull(comparison)
            } catch (e: java.io.IOException) {
                report.put(
                    "sampleFailure",
                    JSONObject().put("status", "FAILURE").put("errorType", e.javaClass.simpleName),
                )
            } finally {
                imported?.let { photos.file(it).delete() }
                publish()
            }
        }
        assertEquals(10, summary.length())
    }

    @Test
    fun reproduceNineFixedSyntheticInputs() = runBlocking {
        val rootArg = InstrumentationRegistry.getArguments().getString("edgeDrawingRoot")
        assumeTrue("external synthetic inputs required", rootArg != null)
        val root = File(rootArg!!)
        val rows = JSONArray(File(root, "synthetic-summary.json").readText())
        val prior = JSONArray(File(root, "synthetic-native.json").readText())
        assertTrue(OpenCVLoader.initLocal())
        val result = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val ref = row.getJSONObject("comparisonGray")
            val bytes = File(root, ref.getString("file")).readBytes()
            assertEquals(ref.getString("sha256"), bSha(bytes))
            val gray = Mat(ref.getInt("height"), ref.getInt("width"), CvType.CV_8UC1)
            try {
                gray.put(0, 0, bytes)
                val raw = EdgeDrawingTestNative.generate(gray)
                val old =
                    (0 until prior.length())
                        .map { prior.getJSONObject(it) }
                        .single { it.getString("sampleId") == row.getString("sampleId") }
                        .getJSONArray("shapes")
                assertEquals(old.length() * 6, raw.size)
                for (k in 0 until old.length()) for (j in 0 until 5) assertEquals(
                    old.getJSONArray(k).getDouble(j),
                    geometryJson(canonicalEdgeDrawing(raw, k * 6)).getDouble(j),
                    1e-9,
                )
                result.put(
                    JSONObject()
                        .put("sampleId", row.getString("sampleId"))
                        .put("inputSha256", bSha(bytes))
                        .put("run", runJson(processBGeometry(gray, raw)))
                )
            } finally {
                gray.release()
            }
        }
        File(
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "b-synthetic-task4.json",
            )
            .writeText(result.toString(2))
    }
}
