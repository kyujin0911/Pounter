package io.github.kyujin0911.pounter

import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import java.io.File
import kotlin.math.PI
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*

/** Fixed Task4 geometry: no generator, refit, truth, ROI or representative policy changes. */
class BQualityEvaluationInstrumentedTest {
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
    fun fixedTenRowsAndNineSyntheticQualityAblations() {
        val arg = InstrumentationRegistry.getArguments().getString("edgeDrawingRoot")
        assumeTrue("explicit external inputs required", arg != null)
        val root = File(arg!!)
        val output =
            File(
                    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "b-quality-task5",
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
                val observed = processBGeometry(gray, raw, qualityPolicy = BQualityPolicy.OBSERVE)
                assertEquals(base.representatives, observed.representatives)
                assertEquals(base.observations, observed.observations)
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
                for (policy in
                    listOf(
                        null,
                        BQualityPolicy.OBSERVE,
                        BQualityPolicy.GAP_90,
                        BQualityPolicy.GAP_120,
                    )) {
                    val name = policy?.name ?: "BASELINE"
                    val full = processBGeometry(gray, raw, qualityPolicy = policy)
                    for (budget in listOf(0, 3)) {
                        val short =
                            processBGeometry(
                                gray,
                                raw,
                                entryBudget = budget,
                                qualityPolicy = policy,
                            )
                        assertEquals(full.representatives, short.representatives)
                        assertEquals(full.rawCount, short.rawCount)
                        assertEquals(full.gateCount, short.gateCount)
                        assertEquals(full.qualityCount, short.qualityCount)
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
                    val times =
                        (0 until 3).map {
                            val start = System.nanoTime()
                            processBGeometry(gray, raw, entryBudget = 0, qualityPolicy = policy)
                            (System.nanoTime() - start) / 1_000_000.0
                        }
                    val report =
                        run(full)
                            .put("sample", sid)
                            .put("policy", name)
                            .put("graySha256", bSha(bytes))
                            .put("primaryStatus", if (markers.size > 300) "FAILURE" else "SUCCESS")
                            .put(
                                "reason",
                                if (markers.size > 300) "COUNT_LIMIT" else JSONObject.NULL,
                            )
                            .put("traceOffStatus", if (markers.size > 300) "SKIP" else "SUCCESS")
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
                            .put("traceOffPostprocessMillis", JSONArray(times))
                            .put("traceOffMedianMillis", times.sorted()[1])
                    File(output, "$sid-$name.json").writeText(report.toString())
                    summary.put(
                        JSONObject()
                            .put("sample", sid)
                            .put("policy", name)
                            .put("file", "$sid-$name.json")
                            .put("primaryStatus", report.getString("primaryStatus"))
                            .put("diagnostic", report.getJSONObject("beforeLimitDiagnostic"))
                            .put("traceOffMedianMillis", times.sorted()[1])
                    )
                    File(output, "summary.json").writeText(summary.toString())
                }
            } finally {
                gray.release()
            }
        }
        assertEquals(40, summary.length())
        val synth = JSONArray(File(root, "synthetic-summary.json").readText())
        val shapes = JSONArray(File(root, "synthetic-native.json").readText())
        val synthetic = JSONArray()
        for (n in 0 until synth.length()) {
            val row = synth.getJSONObject(n)
            val sid = row.getString("sampleId")
            val ref = row.getJSONObject("comparisonGray")
            val bytes = File(root, ref.getString("file")).readBytes()
            assertEquals(ref.getString("sha256"), bSha(bytes))
            val gray = Mat(ref.getInt("height"), ref.getInt("width"), CvType.CV_8UC1)
            try {
                gray.put(0, 0, bytes)
                val prior =
                    (0 until shapes.length())
                        .map { shapes.getJSONObject(it) }
                        .single { it.getString("sampleId") == sid }
                val raw = raw(prior.getJSONArray("shapes"))
                val b = processBGeometry(gray, raw)
                val o = processBGeometry(gray, raw, qualityPolicy = BQualityPolicy.OBSERVE)
                assertEquals(b.representatives, o.representatives)
                for (p in BQualityPolicy.entries) synthetic.put(
                    run(processBGeometry(gray, raw, qualityPolicy = p))
                        .put("sample", sid)
                        .put("policy", p.name)
                )
            } finally {
                gray.release()
            }
        }
        File(output, "synthetic.json").writeText(synthetic.toString())
    }
}
