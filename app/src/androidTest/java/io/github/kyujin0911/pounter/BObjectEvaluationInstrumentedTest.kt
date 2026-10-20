package io.github.kyujin0911.pounter

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import kotlin.math.PI
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*

/** Fixed Task4 geometry: no generator, refit, truth, ROI or representative policy changes. */
class BObjectEvaluationInstrumentedTest {
    private fun raw(shapes: JSONArray): DoubleArray =
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

    private fun geometry(g: EllipseGeometry) =
        JSONArray(listOf(g.centerXPx, g.centerYPx, g.semiMajorPx, g.semiMinorPx, g.angleRadians))

    private fun run(r: BRun): JSONObject =
        JSONObject()
            .put("raw", r.rawCount)
            .put("gate", r.gateCount)
            .put("quality", r.qualityCount)
            .put("sameRimCount", r.sameRimCount)
            .put(
                "objectMerge",
                r.objectMerge?.let { m ->
                    JSONObject()
                        .put("inputCount", m.inputCount)
                        .put("mergedCount", m.mergedCount)
                        .put("pairChecks", m.pairChecks)
                        .put("reasonCounts", JSONObject(m.reasonCounts))
                        .put("truncated", m.truncated)
                        .put("elapsedNanos", m.elapsedNanos)
                        .put(
                            "decisions",
                            JSONArray(
                                m.decisions.map { d ->
                                    JSONObject()
                                        .put("anchorId", d.anchorId)
                                        .put("candidateId", d.candidateId)
                                        .put("reason", d.reason)
                                        .put("coherentSpokes", d.coherentSpokes)
                                        .put("exteriorSpokes", d.exteriorSpokes)
                                        .put("invalidSpokes", d.invalidSpokes)
                                }
                            ),
                        )
                } ?: JSONObject.NULL,
            )
            .put(
                "representatives",
                JSONArray(
                    r.representatives.map {
                        JSONObject()
                            .put("anchorId", it.anchor.id)
                            .put("sourceIds", JSONArray(it.sourceIds))
                            .put("geometry", geometry(it.anchor.geometry))
                            .put("support", it.anchor.support)
                    }
                ),
            )
            .put(
                "observations",
                JSONArray(
                    r.observations.map { o ->
                        JSONObject()
                            .put("id", o.id)
                            .put("geometry", geometry(o.geometry))
                            .put("support", o.support ?: JSONObject.NULL)
                            .put("stage", o.stage)
                            .also { v ->
                                r.edgeQualities[o.id]?.let { q ->
                                    v.put(
                                        "evidence",
                                        JSONObject()
                                            .put("supported", q.supported)
                                            .put("invalid", q.invalid)
                                            .put("unsupported", q.unsupported)
                                            .put("gapSamples", q.longestGapSamples)
                                            .put("gapDegrees", q.longestGapDegrees)
                                            .put("distinctPixels", q.distinctPixels)
                                            .put("maxStepPx", q.maxStepPx)
                                            .put("undersampled", q.undersampled),
                                    )
                                }
                            }
                    }
                ),
            )

    @Test
    fun fixedTenRowsObjectPoliciesAndActualDetector() = runBlocking {
        val arg = InstrumentationRegistry.getArguments().getString("edgeDrawingRoot")
        assumeTrue("explicit external inputs required", arg != null)
        val root = File(arg!!)
        val output =
            File(
                    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "b-object-task6",
                )
                .apply { mkdirs() }
        assertTrue(OpenCVLoader.initLocal())
        val rows = JSONArray(File(root, "rows.json").readText())
        val summary = JSONArray()
        for (n in 0 until rows.length()) {
            val row = rows.getJSONObject(n)
            val sid = row.getString("sampleId") + "-" + row.getString("scope")
            val ref = row.getJSONObject("comparisonGray")
            val bytes = File(root, ref.getString("file")).readBytes()
            assertEquals(ref.getString("sha256"), bSha(bytes))
            val gray = Mat(ref.getInt("height"), ref.getInt("width"), CvType.CV_8UC1)
            try {
                gray.put(0, 0, bytes)
                val raw = raw(row.getJSONArray("nativeShapes"))
                val base = processBGeometry(gray, raw)
                val sample = row.getJSONObject("sample")
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
                val transform =
                    AnalysisTransform(
                        row.getInt("storedWidth"),
                        row.getInt("storedHeight"),
                        0,
                        0,
                        row.getInt("decodeWidth").toDouble() / row.getInt("storedWidth"),
                        row.getInt("decodeHeight").toDouble() / row.getInt("storedHeight"),
                    )
                val roi = row.getJSONArray("roiPixels")
                val region =
                    sample.getJSONArray("region").let {
                        Region(
                            it.getDouble(0).toFloat(),
                            it.getDouble(1).toFloat(),
                            it.getDouble(2).toFloat(),
                            it.getDouble(3).toFloat(),
                        )
                    }
                for (policy in listOf(null, BObjectPolicy.ANNULUS_ALL, BObjectPolicy.ANNULUS_90)) {
                    val name = policy?.name ?: "BASELINE"
                    val full = processBGeometry(gray, raw, objectPolicy = policy)
                    for (budget in listOf(0, 3)) {
                        val short =
                            processBGeometry(gray, raw, entryBudget = budget, objectPolicy = policy)
                        assertEquals(full.representatives, short.representatives)
                        assertEquals(full.rawCount, short.rawCount)
                        assertEquals(full.gateCount, short.gateCount)
                        assertEquals(full.qualityCount, short.qualityCount)
                        assertEquals(
                            full.objectMerge?.reasonCounts,
                            short.objectMerge?.reasonCounts,
                        )
                        assertEquals(full.objectMerge?.pairChecks, short.objectMerge?.pairChecks)
                        assertTrue(short.observations.size <= budget)
                    }
                    val markers =
                        full.representatives
                            .map {
                                toMarker(
                                    it.anchor.geometry.copy(
                                        centerXPx = it.anchor.geometry.centerXPx + roi.getInt(0),
                                        centerYPx = it.anchor.geometry.centerYPx + roi.getInt(1),
                                    ),
                                    transform,
                                    "b-${it.anchor.id}",
                                )
                            }
                            .filter { region.contains(it.x, it.y) }
                    val metric =
                        matchDetectionsInReferencePixels(
                            expected,
                            markers,
                            sample.getInt("referenceWidth"),
                            sample.getInt("referenceHeight"),
                            tolerances,
                        )
                    val objectTimes = mutableListOf<Double>()
                    val times =
                        (0 until 3).map {
                            val start = System.nanoTime()
                            val timed =
                                processBGeometry(gray, raw, entryBudget = 0, objectPolicy = policy)
                            timed.objectMerge?.let { objectTimes += it.elapsedNanos / 1_000_000.0 }
                            (System.nanoTime() - start) / 1_000_000.0
                        }
                    val report =
                        run(full)
                            .put("sample", sid)
                            .put("policy", name)
                            .put("graySha256", bSha(bytes))
                            .put(
                                "projectedPrimaryStatus",
                                if (markers.size > 300) "FAILURE" else "SUCCESS",
                            )
                            .put(
                                "reason",
                                if (markers.size > 300) "COUNT_LIMIT" else JSONObject.NULL,
                            )
                            .put(
                                "projectedTraceOffStatus",
                                if (markers.size > 300) "SKIP" else "SUCCESS",
                            )
                            .put(
                                "markers",
                                JSONArray(
                                    markers.map {
                                        JSONObject()
                                            .put("id", it.id)
                                            .put("x", it.x.toDouble())
                                            .put("y", it.y.toDouble())
                                            .put("radius", it.radius.toDouble())
                                    }
                                ),
                            )
                            .put(
                                "beforeLimitDiagnostic",
                                JSONObject()
                                    .put("M", metric.metrics.matched)
                                    .put("FN", metric.metrics.missing)
                                    .put("FP", metric.metrics.falsePositive)
                                    .put("correction", metric.metrics.correctionCount)
                                    .put("count", markers.size)
                                    .put("pairs", JSONObject(metric.expectedToActualIds)),
                            )
                            .put("traceOffObjectMillis", JSONArray(objectTimes))
                            .put("traceOffPostprocessMillis", JSONArray(times))
                            .put("traceOffMedianMillis", times.sorted()[1])
                    if (policy != null) {
                        val photos =
                            PhotoStore(InstrumentationRegistry.getInstrumentation().targetContext)
                        val source = File(root, sample.getString("photo"))
                        assertEquals(sample.getString("sourceSha256"), bSha(source.readBytes()))
                        val photo = photos.import(Uri.fromFile(source))
                        try {
                            val detector =
                                EdgeDrawingEvaluationDetector(photos, objectPolicy = policy)
                            val task =
                                CountTask(
                                    sid,
                                    photo.first,
                                    photo.second,
                                    photo.third,
                                    0,
                                    region = region,
                                )
                            var actualMarkers: List<Marker>? = null
                            var reason: String? = null
                            try {
                                actualMarkers = detector.detect(task).markers
                            } catch (e: DetectionFailure) {
                                reason = e.reason.name
                            }
                            assertEquals(ref.getString("sha256"), detector.lastInput!!.graySha256)
                            val actualRun = detector.lastRun!!
                            assertEquals(
                                full.representatives.map { it.anchor.id to it.sourceIds },
                                actualRun.representatives.map { it.anchor.id to it.sourceIds },
                            )
                            assertEquals(full.sameRimCount, actualRun.sameRimCount)
                            for ((fixed, live) in
                                full.representatives.zip(actualRun.representatives)) {
                                val f = fixed.anchor.geometry
                                val l = live.anchor.geometry
                                for ((x, y) in
                                    listOf(
                                        f.centerXPx to l.centerXPx,
                                        f.centerYPx to l.centerYPx,
                                        f.semiMajorPx to l.semiMajorPx,
                                        f.semiMinorPx to l.semiMinorPx,
                                        f.angleRadians to l.angleRadians,
                                    )) assertEquals(x, y, 1e-9)
                            }
                            if (markers.size > 300) {
                                assertEquals("COUNT_LIMIT", reason)
                                assertNull(actualMarkers)
                            } else {
                                assertNull(reason)
                                assertEquals(markers, actualMarkers)
                                assertEquals("SUCCESS", detector.status)
                            }
                            File(output, "$sid-$name-actual.json")
                                .writeText(
                                    run(actualRun)
                                        .put("actualDetectorStatus", detector.status)
                                        .put("failureReason", reason ?: JSONObject.NULL)
                                        .put(
                                            "actualMarkerCount",
                                            actualMarkers?.size ?: JSONObject.NULL,
                                        )
                                        .put("photoStoreNativeExecuted", true)
                                        .put("graySha256", detector.lastInput!!.graySha256)
                                        .toString()
                                )
                        } finally {
                            photos.file(photo.first).delete()
                        }
                    }
                    File(output, "$sid-$name.json").writeText(report.toString())
                    summary.put(
                        JSONObject()
                            .put("sample", sid)
                            .put("policy", name)
                            .put("file", "$sid-$name.json")
                            .put(
                                "projectedPrimaryStatus",
                                report.getString("projectedPrimaryStatus"),
                            )
                            .put("diagnostic", report.getJSONObject("beforeLimitDiagnostic"))
                            .put("traceOffMedianMillis", times.sorted()[1])
                    )
                    File(output, "summary.json").writeText(summary.toString())
                }
            } finally {
                gray.release()
            }
        }
        assertEquals(30, summary.length())
    }
}
