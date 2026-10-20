package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.detector.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DetectionEvaluationTest {
    @Test fun evaluateTuningSet() = runBlocking { evaluateImages("tuning") }
}

/** No embedded field photographs, truths, configuration defaults or acceptance assertion. */
internal suspend fun evaluateImages(
    split: String,
    arguments: Bundle = InstrumentationRegistry.getArguments(),
) {
    val manifestPath = arguments.getString("evaluationManifest")
    val configPath = arguments.getString("evaluationConfig")
    assumeTrue("현장 평가 대기: 외부 정답 manifest/config 미제공", manifestPath != null && configPath != null)
    val manifestFile = File(manifestPath!!)
    val configBytes = File(configPath!!).readBytes()
    val configHash =
        MessageDigest.getInstance("SHA-256").digest(configBytes).joinToString("") {
            "%02x".format(it)
        }
    val configJson = JSONObject(configBytes.toString(Charsets.UTF_8))
    val config = readEvaluationConfig(configJson)
    val manifest = JSONObject(manifestFile.readText())
    val samples = validateEvaluationManifest(manifest, configHash)
    val selected = samples.filter { it.getString("split") == split }
    require(selected.isNotEmpty()) { "선택 split의 자료가 없습니다." }
    if (split == "independent") {
        require(configJson.getBoolean("frozen")) { "고정 설정이 필요합니다." }
        require(
            Build.MANUFACTURER.equals("samsung", true) &&
                Build.MODEL.startsWith("SM-S906") &&
                Build.VERSION.SDK_INT == 36
        ) {
            "대표 S22+ / Android 16에서 실행해주세요."
        }
    }
    val context =
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getFilesDir() =
                File(super.getCacheDir(), "evaluation-private").apply { mkdirs() }
        }
    val photos = PhotoStore(context)
    val detector = CompositePipeDetector(photos, config)
    val metricVersion = evaluationMetricVersion(manifest)
    val journal = File(context.filesDir, "evaluation-${UUID.randomUUID()}.jsonl")
    fun publish(report: JSONObject) {
        java.io.FileOutputStream(journal, true).use { output ->
            output.write((report.toString() + "\n").toByteArray())
            output.fd.sync()
        }
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(0, Bundle().apply { putString("evaluationResult", report.toString()) })
    }
    var allEligible = true
    for (sample in selected) {
        val report =
            JSONObject()
                .put("runId", UUID.randomUUID().toString())
                .put("sampleId", sample.getString("sampleId"))
                .put("split", split)
                .put("schemaVersion", manifest.getInt("schemaVersion"))
                .put("metricVersion", metricVersion)
                .put("configSha256", configHash)
                .put("manifestSha256", sha256(manifestFile))
                .put("detectorVersion", config.detectorVersion)
                .put(
                    "testApkSha256",
                    sha256(
                        File(
                            InstrumentationRegistry.getInstrumentation()
                                .context
                                .applicationInfo
                                .sourceDir
                        )
                    ),
                )
                .put("appApkSha256", sha256(File(context.applicationInfo.sourceDir)))
                .put("device", Build.MODEL)
                .put("api", Build.VERSION.SDK_INT)
                .put("abi", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("opencvVersion", org.opencv.core.Core.VERSION)
                .put("processId", android.os.Process.myPid())
                .put(
                    "fieldAcceptance",
                    "PENDING: reviewed truth, independent data and field workflow required",
                )
        var imported: Triple<String, Int, Int>? = null
        suspend fun stage(name: String, action: suspend () -> JSONObject): JSONObject? =
            recordEvaluationStage(report, name, { publish(report) }, action)
        try {
            val root = manifestFile.canonicalFile.parentFile!!
            val source = File(root, sample.getString("photo")).canonicalFile
            val importResult =
                stage("import") {
                    if (!source.toPath().startsWith(root.toPath()) || !source.isFile)
                        throw java.io.IOException("Invalid source")
                    val sourceHash = sha256(source)
                    if (
                        manifest.getInt("schemaVersion") == 2 &&
                            sourceHash != sample.getString("sourceSha256")
                    )
                        throw java.io.IOException("Source hash mismatch")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.path, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                        throw java.io.IOException("Invalid dimensions")
                    val exif = ExifInterface(source)
                    val swap = exif.rotationDegrees % 180 != 0
                    val referenceWidth = if (swap) bounds.outHeight else bounds.outWidth
                    val referenceHeight = if (swap) bounds.outWidth else bounds.outHeight
                    if (
                        manifest.getInt("schemaVersion") == 2 &&
                            (referenceWidth != sample.getInt("referenceWidth") ||
                                referenceHeight != sample.getInt("referenceHeight"))
                    )
                        throw java.io.IOException("Reference dimensions mismatch")
                    imported = photos.import(Uri.fromFile(source))
                    val stored = imported!!
                    JSONObject()
                        .put("sourceSha256", sourceHash)
                        .put("importSha256", sha256(photos.file(stored.first)))
                        .put("sourceWidth", bounds.outWidth)
                        .put("sourceHeight", bounds.outHeight)
                        .put(
                            "exifOrientation",
                            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1),
                        )
                        .put("referenceWidth", referenceWidth)
                        .put("referenceHeight", referenceHeight)
                        .put("storedWidth", stored.second)
                        .put("storedHeight", stored.third)
                }
            if (importResult == null) {
                allEligible = false
                continue
            }
            val region =
                sample.getJSONArray("region").let {
                    Region(
                        it.getDouble(0).toFloat(),
                        it.getDouble(1).toFloat(),
                        it.getDouble(2).toFloat(),
                        it.getDouble(3).toFloat(),
                    )
                }
            val stored = imported!!
            val task =
                CountTask(
                    UUID.randomUUID().toString(),
                    stored.first,
                    stored.second,
                    stored.third,
                    0,
                    region = region,
                )
            val expected = readTruth(sample.getJSONArray("expected"))
            require(expected.all { region.contains(it.x, it.y) })
            fun measure(markers: List<Marker>): JSONObject {
                val result =
                    if (metricVersion == "metric-v2")
                        matchDetectionsInReferencePixels(
                            expected,
                            markers,
                            sample.getInt("referenceWidth"),
                            sample.getInt("referenceHeight"),
                            readTolerances(sample),
                        )
                    else null
                val metrics =
                    result?.metrics
                        ?: matchDetections(
                            expected,
                            markers,
                            sample.getDouble("normalizedTolerance"),
                        )
                return JSONObject()
                    .put("matched", metrics.matched)
                    .put("missing", metrics.missing)
                    .put("falsePositive", metrics.falsePositive)
                    .put("corrections", metrics.correctionCount)
                    .put("netDifference", markers.size - expected.size)
                    .put(
                        "precision",
                        if (markers.isEmpty()) JSONObject.NULL
                        else metrics.matched.toDouble() / markers.size,
                    )
                    .put(
                        "recall",
                        if (expected.isEmpty()) JSONObject.NULL
                        else metrics.matched.toDouble() / expected.size,
                    )
                    .also { row ->
                        if (result != null) {
                            val legacy =
                                matchDetections(
                                    expected,
                                    markers,
                                    sample.getDouble("normalizedTolerance"),
                                )
                            row.put(
                                "samePredictionsMetricV1",
                                JSONObject()
                                    .put("matched", legacy.matched)
                                    .put("missing", legacy.missing)
                                    .put("falsePositive", legacy.falsePositive),
                            )
                            row.put("expectedToActualIds", JSONObject(result.expectedToActualIds))
                                .put("unmatchedExpectedIds", JSONArray(result.unmatchedExpectedIds))
                                .put("unmatchedActualIds", JSONArray(result.unmatchedActualIds))
                        }
                    }
            }
            val decode =
                stage("decode") {
                    photos
                        .analysisPhoto(
                            task.photoName,
                            config.analysisMaxSidePx,
                            config.maxDecodedPixels,
                        )
                        .use { analysis ->
                            val w = analysis.bitmap.width
                            val h = analysis.bitmap.height
                            val left = kotlin.math.floor(region.left * w).toInt().coerceIn(0, w - 1)
                            val top = kotlin.math.floor(region.top * h).toInt().coerceIn(0, h - 1)
                            report.put(
                                "coordinates",
                                JSONObject()
                                    .put("decodeWidth", w)
                                    .put("decodeHeight", h)
                                    .put("scaleX", w.toDouble() / task.width)
                                    .put("scaleY", h.toDouble() / task.height)
                                    .put("roiLeft", left)
                                    .put("roiTop", top)
                                    .put(
                                        "roiRight",
                                        kotlin.math
                                            .ceil(region.right * w)
                                            .toInt()
                                            .coerceIn(left + 1, w),
                                    )
                                    .put(
                                        "roiBottom",
                                        kotlin.math
                                            .ceil(region.bottom * h)
                                            .toInt()
                                            .coerceIn(top + 1, h),
                                    ),
                            )
                        }
                    report.getJSONObject("coordinates")
                }
            if (decode == null) {
                allEligible = false
                continue
            }
            if (manifest.getInt("schemaVersion") == 2)
                report
                    .put("truthVersion", manifest.getString("truthVersion"))
                    .put("datasetVersion", manifest.getString("datasetVersion"))
                    .put("annotationVersion", manifest.getString("annotationVersion"))
                    .put("truthSha256", sample.getString("truthSha256"))
                    .put("groundTruthFrozen", manifest.getBoolean("groundTruthFrozen"))
            val allowance =
                if (expected.isEmpty()) sample.getInt("zeroFalsePositiveAllowance")
                else correctionAllowance(expected.size)
            val primary =
                stage("primary") {
                    val started = System.nanoTime()
                    val detected = detector.detect(task)
                    measure(detected.markers)
                        .put("detectCallMillis", (System.nanoTime() - started) / 1_000_000)
                        .put("initializationMillis", detector.initializationMillis)
                        .put("nativeHeapAfterBytes", Debug.getNativeHeapAllocatedSize())
                        .put("rejectedCandidates", JSONObject(detector.rejectedCandidateCounts))
                }
            if (primary == null) {
                allEligible = false
                continue
            }
            // Primary is durably published before any repeat/comparison begins.
            val repeat = stage("repeat") { measure(detector.detect(task).markers) }
            allEligible =
                allEligible &&
                    primary.getInt("corrections") <= allowance &&
                    repeat != null &&
                    repeat.getInt("corrections") <= allowance
            if (split == "tuning" && arguments.getString("compareHough") == "true")
                stage("hough") { measure(HoughPipeDetector(photos).detect(task).markers) }
            else report.put("hough", JSONObject().put("status", "SKIP"))
            if (split == "tuning" && arguments.getString("compareOriginal") == "true")
                stage("original") {
                    compareOriginal(
                        source,
                        photos,
                        task,
                        expected,
                        sample.optDouble("normalizedTolerance", .02),
                        config,
                        ::measure,
                    )
                }
            else report.put("original", JSONObject().put("status", "SKIP"))
            publish(report)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (unexpected: Throwable) {
            report.put("sessionStatus", "FAILED").put("errorType", unexpected.javaClass.simpleName)
            publish(report)
            throw unexpected
        } finally {
            imported?.let { photos.delete(it.first) }
        }
    }
    if (split == "independent") assertTrue("독립 자료 보정 기준 미충족; 제품 연결 대기", allEligible)
}

internal class EvaluationStageSkipped(val reason: String) : Exception(reason)

internal suspend fun recordEvaluationStage(
    report: JSONObject,
    name: String,
    publish: () -> Unit = {},
    action: suspend () -> JSONObject,
): JSONObject? {
    report.put(name, JSONObject().put("status", "STARTED"))
    publish()
    try {
        val result = action()
        report.put(name, JSONObject().put("status", "SUCCESS").put("result", result))
        publish()
        return result
    } catch (skip: EvaluationStageSkipped) {
        report.put(name, JSONObject().put("status", "SKIP").put("reason", skip.reason))
        publish()
        return null
    } catch (cancel: kotlinx.coroutines.CancellationException) {
        report.put(name, JSONObject().put("status", "CANCELLED"))
        publish()
        throw cancel
    } catch (failure: Throwable) {
        report.put(
            name,
            JSONObject()
                .put("status", "FAILURE")
                .put("errorType", failure.javaClass.simpleName)
                .put("reason", (failure as? DetectionFailure)?.reason?.name ?: "UNRESOLVED"),
        )
        publish()
        if (failure !is DetectionFailure && failure !is java.io.IOException) throw failure
        return null
    }
}

internal fun evaluationMetricVersion(manifest: JSONObject): String =
    if (manifest.getInt("schemaVersion") == 1) "metric-v1" else "metric-v2"

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun readTolerances(sample: JSONObject): Map<String, Double> {
    val json = sample.getJSONObject("tolerancesPx")
    return json.keys().asSequence().associateWith { json.getDouble(it) }
}

internal fun validateEvaluationManifest(
    manifest: JSONObject,
    configHash: String,
): List<JSONObject> {
    val schema = manifest.getInt("schemaVersion")
    require(schema in 1..2)
    if (schema == 1) require(manifest.getBoolean("groundTruthFrozen"))
    if (schema == 2) {
        for (key in listOf("datasetVersion", "truthVersion", "annotationVersion")) require(
            manifest.getString(key).isNotBlank()
        )
        require(configHash.matches(Regex("[0-9a-f]{64}")))
    }
    require(manifest.getString("configSha256") == configHash)
    val array = manifest.getJSONArray("samples")
    val samples = (0 until array.length()).map { array.getJSONObject(it) }
    require(samples.map { it.getString("sampleId") }.distinct().size == samples.size)
    samples.forEach { sample ->
        for (key in listOf("sampleId", "sessionId", "bundleId", "photo")) require(
            sample.getString(key).isNotBlank()
        )
        require(sample.getString("split") in listOf("reference", "tuning", "independent"))
        require(sample.getJSONArray("supportGroup").length() > 0)
        run {
            val tolerance = sample.getDouble("normalizedTolerance")
            require(tolerance.isFinite() && tolerance >= 0 && tolerance <= 1)
        }
        val expected = readTruth(sample.getJSONArray("expected"))
        if (sample.getString("split") == "independent") {
            require(manifest.getBoolean("groundTruthFrozen"))
            for (key in listOf("sessionId", "bundleId")) require(
                !sample.getString(key).equals("unknown", true)
            )
        }
        if (schema == 2) {
            for (key in listOf("sourceSha256", "truthSha256")) require(
                sample.getString(key).matches(Regex("[0-9a-f]{64}"))
            )
            val truthBytes = sample.getJSONArray("expected").toString().toByteArray()
            val truthHash =
                MessageDigest.getInstance("SHA-256").digest(truthBytes).joinToString("") {
                    "%02x".format(it)
                }
            require(truthHash == sample.getString("truthSha256"))
            require(sample.getInt("confirmedCount") == expected.size)
            matchDetectionsInReferencePixels(
                expected,
                emptyList(),
                sample.getInt("referenceWidth"),
                sample.getInt("referenceHeight"),
                readTolerances(sample),
            )
            val items = sample.getJSONArray("expected")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                require(item.getDouble("bPx").isFinite() && item.getDouble("bPx") > 0)
                require(item.getDouble("uPx").isFinite() && item.getDouble("uPx") >= 0)
                require(item.getString("reviewStatus") in listOf("DRAFT", "REVIEWED"))
                if (manifest.getBoolean("groundTruthFrozen"))
                    require(item.getString("reviewStatus") == "REVIEWED")
            }
        }
        require(expected.size <= 300)
        require(expected.map { it.id }.distinct().size == expected.size)
        sample.getJSONArray("excluded").let { excluded ->
            val excludedIds =
                (0 until excluded.length()).map { excluded.getJSONObject(it).getString("id") }
            require(
                excludedIds.all { it.isNotBlank() } &&
                    excludedIds.distinct().size == excludedIds.size
            )
            require(excludedIds.toSet().intersect(expected.map { it.id }.toSet()).isEmpty())
            for (index in 0 until excluded.length()) {
                val target = excluded.getJSONObject(index)
                require(target.getString("reason").isNotBlank())
                Marker(
                    target.getString("id"),
                    target.getDouble("x").toFloat(),
                    target.getDouble("y").toFloat(),
                    .01f,
                )
            }
        }
        require(sample.getInt("zeroFalsePositiveAllowance") >= 0)
        sample.getJSONArray("region").let {
            Region(
                it.getDouble(0).toFloat(),
                it.getDouble(1).toFloat(),
                it.getDouble(2).toFloat(),
                it.getDouble(3).toFloat(),
            )
        }
    }
    val independent = samples.filter { it.getString("split") == "independent" }
    val development = samples.filter { it.getString("split") != "independent" }
    for (key in listOf("sessionId", "bundleId")) require(
        independent
            .map { it.getString(key) }
            .toSet()
            .intersect(development.map { it.getString(key) }.toSet())
            .isEmpty()
    ) {
        "split 사이에 촬영 세션이나 묶음을 공유할 수 없습니다."
    }
    return samples
}

private fun readTruth(items: JSONArray): List<Marker> =
    (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        Marker(
            item.getString("id"),
            item.getDouble("x").toFloat(),
            item.getDouble("y").toFloat(),
            .01f,
        )
    }

internal fun readEvaluationConfig(json: JSONObject): DetectionConfig {
    val merge = json.getJSONObject("mergePolicy")
    return DetectionConfig(
        json.getString("detectorVersion"),
        json.getInt("analysisMaxSidePx"),
        json.getLong("maxDecodedPixels"),
        json.getInt("maxCandidates"),
        json.getInt("medianKernelSize"),
        json.getDouble("cannyLow"),
        json.getDouble("cannyHigh"),
        json.getInt("adaptiveBlockSize"),
        json.getDouble("adaptiveOffset"),
        json.getDouble("minSemiAxisPx"),
        json.getDouble("maxSemiAxisPx"),
        json.getDouble("maxAxisRatio"),
        json.getDouble("minEdgeSupport"),
        json.getDouble("maxFitError"),
        json.getDouble("minContrast"),
        MergePolicy(
            merge.getDouble("centerToleranceRatio"),
            merge.getDouble("maxBoundaryGapRatio"),
            merge.getDouble("minContainmentFraction"),
        ),
    )
}

internal suspend fun compareOriginal(
    source: File,
    photos: PhotoStore,
    task: CountTask,
    expected: List<Marker>,
    tolerance: Double,
    config: DetectionConfig,
    measure: (List<Marker>) -> JSONObject,
): JSONObject {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth.toLong() * bounds.outHeight > config.maxDecodedPixels)
        throw EvaluationStageSkipped("ORIGINAL_PIXEL_BUDGET")
    val decoded =
        BitmapFactory.decodeFile(source.path)
            ?: throw java.io.IOException("Original comparison image cannot be decoded")
    var normalized: Bitmap? = null
    val name = "${UUID.randomUUID()}.jpg"
    try {
        val exif = ExifInterface(source)
        val matrix =
            Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                if (exif.rotationDegrees != 0) postRotate(exif.rotationDegrees.toFloat())
            }
        normalized = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // PNG bytes retain normalized original pixels; .jpg name is a PhotoStore contract only.
        photos.file(name).outputStream().use {
            check(normalized!!.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val original =
            task.copy(photoName = name, width = normalized!!.width, height = normalized!!.height)
        val detected =
            CompositePipeDetector(
                    photos,
                    config.copy(analysisMaxSidePx = max(original.width, original.height)),
                )
                .detect(original)
        return measure(detected.markers)
            .put("input", "EXIF-oriented original full scale; compression and scale comparison")
    } finally {
        photos.delete(name)
        if (normalized !== decoded) normalized?.recycle()
        decoded.recycle()
    }
}
