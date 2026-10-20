package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

internal fun baselinePositionEvidence(
    snapshot: DetectionDiagnostics.Snapshot,
    decodeWidth: Int,
    decodeHeight: Int,
    roiLeft: Int,
    roiTop: Int,
): JSONObject {
    require(decodeWidth > 0 && decodeHeight > 0 && roiLeft >= 0 && roiTop >= 0)
    return JSONObject()
        .put("space", "normalized full EXIF-oriented decode; ROI offset applied")
        .put("geometryTruncated", snapshot.totals.getValue("raw") > snapshot.entries.size)
        .put("rawTargetRecall", "UNRESOLVED")
        .put(
            "rawTargetRecallReason",
            "No center exists before fit; bounded geometry export cannot prove raw absence",
        )
        .put(
            "entries",
            JSONArray(
                snapshot.entries.mapNotNull { entry ->
                    entry.geometry?.let { shape ->
                        JSONObject()
                            .put("id", entry.observationId.toString())
                            .put("mask", entry.mask)
                            .put("stage", entry.stage)
                            .put("reason", entry.reason)
                            .put("quality", entry.quality?.let { JSONArray(it) } ?: JSONObject.NULL)
                            .put("acceptedIndex", entry.acceptedIndex ?: JSONObject.NULL)
                            .put(
                                "representativeIndex",
                                entry.representativeIndex ?: JSONObject.NULL,
                            )
                            .also { result ->
                                if (
                                    listOf(
                                            shape.cxPx,
                                            shape.cyPx,
                                            shape.aPx,
                                            shape.bPx,
                                            shape.angleRadians,
                                        )
                                        .all { it.isFinite() }
                                ) {
                                    result
                                        .put("geometryStatus", "FINITE_FIT")
                                        .put("x", (shape.cxPx + roiLeft) / decodeWidth)
                                        .put("y", (shape.cyPx + roiTop) / decodeHeight)
                                        .put("aDecodePx", shape.aPx)
                                        .put("bDecodePx", shape.bPx)
                                        .put("angleRadians", shape.angleRadians)
                                } else result.put("geometryStatus", "INVALID_FIT")
                            }
                    }
                }
            ),
        )
}

internal fun saveBaselinePositionEvidence(
    snapshot: DetectionDiagnostics.Snapshot,
    decodeWidth: Int,
    decodeHeight: Int,
    roiLeft: Int,
    roiTop: Int,
    file: File,
): JSONObject {
    val details = baselinePositionEvidence(snapshot, decodeWidth, decodeHeight, roiLeft, roiTop)
    val bytes = details.toString().toByteArray()
    file.writeBytes(bytes)
    return JSONObject()
        .put("file", file.name)
        .put("entryCount", details.getJSONArray("entries").length())
        .put("geometryTruncated", details.getBoolean("geometryTruncated"))
        .put(
            "sha256",
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            },
        )
}

/** Optional local diagnostic run. No field truths or paths embedded in the test. */
class BaselineDiagnosticsInstrumentedTest {
    @Test
    fun positionDetailsStayOutsideAccumulatedSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "position-export-regression.json")
        val trace = DetectionDiagnostics(1, 0)
        trace.reset()
        val id = trace.raw("canny", emptyList())
        trace.observe(
            id,
            "quality",
            "ACCEPTED",
            DetectionDiagnostics.Geometry(30.0, 40.0, 12.0, 8.0, 0.0),
        )
        try {
            val summary = saveBaselinePositionEvidence(trace.snapshot(), 200, 100, 0, 0, file)
            assertFalse(summary.has("entries"))
            assertEquals(1, JSONObject(file.readText()).getJSONArray("entries").length())
            assertEquals(1, summary.getInt("entryCount"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidFitIsRecordedWithoutFabricatingGeometry() {
        val trace = DetectionDiagnostics(1, 0)
        trace.reset()
        val id = trace.raw("binary", emptyList())
        trace.observe(
            id,
            "rejected",
            "GEOMETRY",
            DetectionDiagnostics.Geometry(Double.NaN, 40.0, 12.0, 0.0, 0.0),
        )
        val entry =
            baselinePositionEvidence(trace.snapshot(), 200, 100, 0, 0)
                .getJSONArray("entries")
                .getJSONObject(0)
        assertEquals("INVALID_FIT", entry.getString("geometryStatus"))
        assertFalse(entry.has("x"))
        assertEquals("GEOMETRY", entry.getString("reason"))
    }

    @Test
    fun exportedGeometryUsesFullDecodeCoordinates() {
        val trace = DetectionDiagnostics(1, 0)
        trace.reset()
        val id = trace.raw("canny", emptyList())
        trace.observe(
            id,
            "rejected",
            "quality",
            DetectionDiagnostics.Geometry(30.0, 40.0, 12.0, 8.0, 0.0),
            quality = listOf(.75, .12, 20.0),
        )
        val result = baselinePositionEvidence(trace.snapshot(), 200, 100, 10, 20)
        val entry = result.getJSONArray("entries").getJSONObject(0)
        assertEquals(.2, entry.getDouble("x"), 1e-9)
        assertEquals(.6, entry.getDouble("y"), 1e-9)
        assertEquals("quality", entry.getString("reason"))
        assertEquals(.12, entry.getJSONArray("quality").getDouble(1), 1e-9)
        assertEquals("UNRESOLVED", result.getString("rawTargetRecall"))
    }

    @Test
    fun diagnoseLocalDevelopmentSet() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("diagnosticManifest")
        assumeTrue("Local diagnostic inputs absent", path != null)
        assertTrue(OpenCVLoader.initLocal())
        val manifestFile = File(path!!)
        diagnose(manifestFile)
    }

    private suspend fun diagnose(
        manifestFile: File,
        exportPositions: Boolean =
            InstrumentationRegistry.getArguments().getString("diagnosticPositionExport") == "true",
        beforeMaskEvidence: (String) -> Unit = {},
        beforeComparison: () -> Unit = {},
    ) {
        val samples = JSONObject(manifestFile.readText()).getJSONArray("samples")
        require(!exportPositions || samples.length() <= 10) {
            "Position export budget: at most 10 rows"
        }
        val context =
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun getFilesDir() =
                    File(super.getCacheDir(), "diagnostic-private").apply { mkdirs() }
            }
        val photos = PhotoStore(context)
        val results = JSONArray()
        val config = DetectionConfig.synthetic()
        val journal = File(context.filesDir, "baseline-${java.util.UUID.randomUUID()}.jsonl")
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            val row =
                JSONObject()
                    .put("sampleId", sample.getString("sampleId"))
                    .put("scope", sample.getString("scope"))
                    .put("runId", java.util.UUID.randomUUID().toString())
            results.put(row)
            fun publish() {
                java.io.FileOutputStream(journal, true).use { output ->
                    output.write((row.toString() + "\n").toByteArray())
                    output.fd.sync()
                }
                // The append-only journal is authoritative after interruption; this is a summary.
                File(context.filesDir, "baseline-diagnostics.json").writeText(results.toString(2))
            }
            suspend fun stage(name: String, action: suspend () -> JSONObject) =
                recordEvaluationStage(row, name, ::publish, action)
            var imported: Triple<String, Int, Int>? = null
            try {
                val root = manifestFile.canonicalFile.parentFile!!
                val source = File(root, sample.getString("photo")).canonicalFile
                if (
                    stage("import") {
                        if (!source.toPath().startsWith(root.toPath()) || !source.isFile)
                            throw java.io.IOException("Invalid source")
                        val sourceHash =
                            java.security.MessageDigest.getInstance("SHA-256")
                                .digest(source.readBytes())
                                .joinToString("") { "%02x".format(it) }
                        if (
                            sample.has("sourceSha256") &&
                                sample.getString("sourceSha256") != sourceHash
                        )
                            throw java.io.IOException("Source hash mismatch")
                        val bounds =
                            android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                        android.graphics.BitmapFactory.decodeFile(source.path, bounds)
                        val exif = androidx.exifinterface.media.ExifInterface(source)
                        row.put("sourceWidth", bounds.outWidth)
                            .put("sourceHeight", bounds.outHeight)
                            .put(
                                "exifOrientation",
                                exif.getAttributeInt(
                                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                    1,
                                ),
                            )
                        imported = photos.import(Uri.fromFile(source))
                        JSONObject()
                            .put("sourceSha256", sourceHash)
                            .put("storedWidth", imported!!.second)
                            .put("storedHeight", imported!!.third)
                    } == null
                )
                    continue
                val stored = imported!!
                val r = sample.getJSONArray("region")
                val region =
                    Region(
                        r.getDouble(0).toFloat(),
                        r.getDouble(1).toFloat(),
                        r.getDouble(2).toFloat(),
                        r.getDouble(3).toFloat(),
                    )
                val task =
                    CountTask(
                        "diagnostic",
                        stored.first,
                        stored.second,
                        stored.third,
                        0,
                        region = region,
                    )
                // Explicit bounded geometry export; native objects and raw points are never
                // retained.
                val trace =
                    if (exportPositions) DetectionDiagnostics(25000, 0)
                    else DetectionDiagnostics(128, 512)
                val detector = CompositePipeDetector(photos, config, diagnostics = trace)
                var primary: Detection? = null
                stage("primary") {
                    primary = detector.detect(task)
                    JSONObject().put("count", primary!!.markers.size).also { result ->
                        if (exportPositions)
                            result.put(
                                "markers",
                                JSONArray(
                                    primary!!.markers.map {
                                        JSONObject().put("id", it.id).put("x", it.x).put("y", it.y)
                                    }
                                ),
                            )
                    }
                }
                val snapshot = trace.snapshot()
                row.put("totals", JSONObject(snapshot.totals))
                    .put("traceStatus", snapshot.status)
                    .put("traceTruncated", snapshot.truncated)
                row.put(
                    "cause",
                    "UNRESOLVED: reviewed positions unavailable; no target-specific cause inferred",
                )
                publish()
                if (primary != null)
                    stage("traceOffComparison") {
                        beforeComparison()
                        val plain = CompositePipeDetector(photos, config).detect(task)
                        assertEquals(
                            plain.markers.map { Triple(it.x, it.y, it.radius) },
                            primary!!.markers.map { Triple(it.x, it.y, it.radius) },
                        )
                        JSONObject().put("traceGeometryPreserved", true)
                    }
                else {
                    row.put("traceOffComparison", JSONObject().put("status", "SKIP"))
                    publish()
                }
                val masks = JSONObject()
                val representatives = mutableMapOf<String, List<EllipseCandidate>>()
                stage("maskComparisons") {
                    photos
                        .analysisPhoto(
                            task.photoName,
                            config.analysisMaxSidePx,
                            config.maxDecodedPixels,
                        )
                        .use { photo ->
                            val rgba = Mat()
                            val gray = Mat()
                            var crop: Mat? = null
                            try {
                                Utils.bitmapToMat(photo.bitmap, rgba)
                                val w = rgba.cols()
                                val h = rgba.rows()
                                val left =
                                    kotlin.math.floor(region.left * w).toInt().coerceIn(0, w - 1)
                                val top =
                                    kotlin.math.floor(region.top * h).toInt().coerceIn(0, h - 1)
                                val right =
                                    kotlin.math.ceil(region.right * w).toInt().coerceIn(left + 1, w)
                                val bottom =
                                    kotlin.math.ceil(region.bottom * h).toInt().coerceIn(top + 1, h)
                                crop = rgba.submat(Rect(left, top, right - left, bottom - top))
                                Imgproc.cvtColor(crop, gray, Imgproc.COLOR_RGBA2GRAY)
                                if (exportPositions) {
                                    val pixels = ByteArray(gray.rows() * gray.cols())
                                    gray.get(0, 0, pixels)
                                    val file =
                                        File(context.filesDir, "${row.getString("runId")}.gray")
                                    file.writeBytes(pixels)
                                    row.put(
                                        "comparisonGray",
                                        JSONObject()
                                            .put("file", file.name)
                                            .put("width", gray.cols())
                                            .put("height", gray.rows())
                                            .put(
                                                "sha256",
                                                java.security.MessageDigest.getInstance("SHA-256")
                                                    .digest(pixels)
                                                    .joinToString("") { "%02x".format(it) },
                                            ),
                                    )
                                }
                                row.put("storedWidth", task.width)
                                    .put("storedHeight", task.height)
                                    .put("decodeWidth", w)
                                    .put("decodeHeight", h)
                                    .put("roiPixels", JSONArray(listOf(left, top, right, bottom)))
                                if (exportPositions)
                                    row.put(
                                        "positionEvidence",
                                        saveBaselinePositionEvidence(
                                            snapshot,
                                            w,
                                            h,
                                            left,
                                            top,
                                            File(
                                                context.filesDir,
                                                "${row.getString("runId")}-combined.json",
                                            ),
                                        ),
                                    )
                                for (mask in listOf("canny", "binary", "inverse")) {
                                    var merged: List<EllipseCandidate>? = null
                                    val report =
                                        stage("mask-$mask") {
                                            val maskTrace =
                                                if (exportPositions) DetectionDiagnostics(25000, 0)
                                                else DetectionDiagnostics(0, 0)
                                            val candidates =
                                                CandidateExtractor.extract(
                                                    gray,
                                                    config,
                                                    diagnostics = maskTrace,
                                                    masks = setOf(mask),
                                                )
                                            merged = mergeCandidates(candidates, config.mergePolicy)
                                            JSONObject()
                                                .put(
                                                    "totals",
                                                    JSONObject(maskTrace.snapshot().totals),
                                                )
                                                .put("merged", merged!!.size)
                                                .also { result ->
                                                    if (exportPositions) {
                                                        beforeMaskEvidence(mask)
                                                        result.put(
                                                            "positionEvidence",
                                                            saveBaselinePositionEvidence(
                                                                maskTrace.snapshot(),
                                                                w,
                                                                h,
                                                                left,
                                                                top,
                                                                File(
                                                                    context.filesDir,
                                                                    "${row.getString("runId")}-${mask}.json",
                                                                ),
                                                            ),
                                                        )
                                                    }
                                                }
                                        }
                                    if (report != null) {
                                        representatives[mask] = merged!!
                                        masks.put(mask, report)
                                    }
                                }
                            } finally {
                                crop?.release()
                                gray.release()
                                rgba.release()
                            }
                        }
                    row.put("maskAblation", masks)
                        .put("maskAblationComplete", representatives.size == 3)
                    if (representatives.size != 3)
                        throw java.io.IOException(
                            "Incomplete mask comparison; no unique contribution inferred"
                        )
                    for ((mask, shapes) in representatives) {
                        val others = representatives.filterKeys { it != mask }.values.flatten()
                        val unique =
                            shapes.count { candidate ->
                                others.none { other ->
                                    mergeCandidates(listOf(candidate, other), config.mergePolicy)
                                        .size == 1
                                }
                            }
                        masks.getJSONObject(mask).put("geometricallyDistinctFromOtherMasks", unique)
                    }
                    row.put("maskAblation", masks)
                    masks
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (unexpected: Throwable) {
                row.put("sessionStatus", "FAILED").put("errorType", unexpected.javaClass.simpleName)
                publish()
                throw unexpected
            } finally {
                imported?.let { photos.delete(it.first) }
            }
        }
        assertEquals(samples.length(), results.length())
    }

    @Test
    fun maskEvidenceFailureKeepsNextSampleRunning() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder =
            File(context.cacheDir, "mask-failure-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val image =
            android.graphics.Bitmap.createBitmap(400, 240, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.rgb(30, 30, 30))
        try {
            File(folder, "blank.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            image.recycle()
        }
        try {
            val samples = JSONArray()
            for (id in listOf("first", "second")) samples.put(
                JSONObject()
                    .put("sampleId", id)
                    .put("scope", "full")
                    .put("photo", "blank.png")
                    .put("region", JSONArray(listOf(0, 0, 1, 1)))
            )
            val manifest =
                File(folder, "manifest.json").apply {
                    writeText(JSONObject().put("samples", samples).toString())
                }
            var failed = false
            diagnose(
                manifest,
                exportPositions = true,
                beforeMaskEvidence = { mask ->
                    if (mask == "canny" && !failed) {
                        failed = true
                        throw java.io.IOException("mask sidecar unavailable")
                    }
                },
            )
            val rows =
                JSONArray(
                    File(context.cacheDir, "diagnostic-private/baseline-diagnostics.json")
                        .readText()
                )
            assertEquals(2, rows.length())
            val first = rows.getJSONObject(0)
            assertEquals("SUCCESS", first.getJSONObject("primary").getString("status"))
            assertEquals("FAILURE", first.getJSONObject("mask-canny").getString("status"))
            assertEquals("FAILURE", first.getJSONObject("maskComparisons").getString("status"))
            assertFalse(
                first
                    .getJSONObject("maskAblation")
                    .getJSONObject("binary")
                    .has("geometricallyDistinctFromOtherMasks")
            )
            assertEquals(
                "SUCCESS",
                rows.getJSONObject(1).getJSONObject("primary").getString("status"),
            )
            assertEquals(
                "SUCCESS",
                rows.getJSONObject(1).getJSONObject("maskComparisons").getString("status"),
            )
        } finally {
            folder.deleteRecursively()
        }
        Unit
    }

    @Test
    fun comparisonFailureKeepsPrimaryOnDisk() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder =
            File(context.cacheDir, "diagnostic-regression-${java.util.UUID.randomUUID()}").apply {
                mkdirs()
            }
        val image =
            android.graphics.Bitmap.createBitmap(400, 240, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.rgb(30, 30, 30))
        try {
            File(folder, "blank.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            image.recycle()
        }
        val sampleId = "synthetic-${java.util.UUID.randomUUID()}"
        val manifest =
            File(folder, "manifest.json").apply {
                writeText(
                    JSONObject()
                        .put(
                            "samples",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("sampleId", sampleId)
                                        .put("scope", "full")
                                        .put("photo", "blank.png")
                                        .put("region", JSONArray(listOf(0, 0, 1, 1)))
                                ),
                        )
                        .toString()
                )
            }
        diagnose(manifest) { throw java.io.IOException("comparison failed") }
        val rows =
            JSONArray(
                File(context.cacheDir, "diagnostic-private/baseline-diagnostics.json").readText()
            )
        val row =
            (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .single { it.getString("sampleId") == sampleId }
        assertEquals("SUCCESS", row.getJSONObject("primary").getString("status"))
        assertEquals("FAILURE", row.getJSONObject("traceOffComparison").getString("status"))
        folder.deleteRecursively()
        Unit
    }
}
