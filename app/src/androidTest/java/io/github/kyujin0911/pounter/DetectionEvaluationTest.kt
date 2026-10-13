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
internal suspend fun evaluateImages(split: String) {
    val arguments = InstrumentationRegistry.getArguments()
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
    var allEligible = true
    for (sample in selected) {
        val root = manifestFile.canonicalFile.parentFile!!
        val source = File(root, sample.getString("photo")).canonicalFile
        require(source.toPath().startsWith(root.toPath()) && source.isFile) {
            "사진 경로가 manifest 폴더 안에 있어야 합니다."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        val imported = photos.import(Uri.fromFile(source))
        try {
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
            val expected = readTruth(sample.getJSONArray("expected"))
            require(expected.all { region.contains(it.x, it.y) })
            val tolerance = sample.getDouble("normalizedTolerance")
            val before = Debug.getNativeHeapAllocatedSize()
            val started = System.nanoTime()
            val first = detector.detect(task)
            val firstCandidateCounts = detector.candidateCounts
            val firstRejectedCounts = detector.rejectedCandidateCounts
            val firstElapsed = (System.nanoTime() - started) / 1_000_000
            val firstInitialization = detector.initializationMillis
            val repeatStarted = System.nanoTime()
            val repeated = detector.detect(task)
            val repeatElapsed = (System.nanoTime() - repeatStarted) / 1_000_000
            val metrics = matchDetections(expected, first.markers, tolerance)
            val repeatedMetrics = matchDetections(expected, repeated.markers, tolerance)
            val allowance =
                if (expected.isEmpty()) sample.getInt("zeroFalsePositiveAllowance")
                else correctionAllowance(expected.size)
            val eligible =
                metrics.correctionCount <= allowance && repeatedMetrics.correctionCount <= allowance
            allEligible = allEligible && eligible
            val report =
                JSONObject()
                    .put("sampleId", sample.getString("sampleId"))
                    .put("split", split)
                    .put("supportGroup", sample.getJSONArray("supportGroup"))
                    .put("configSha256", configHash)
                    .put("detectorVersion", config.detectorVersion)
                    .put("expected", expected.size)
                    .put("matched", metrics.matched)
                    .put("missing", metrics.missing)
                    .put("falsePositive", metrics.falsePositive)
                    .put("corrections", metrics.correctionCount)
                    .put("allowance", allowance)
                    .put("repeatCorrections", repeatedMetrics.correctionCount)
                    .put("firstDetectCallMillis", firstElapsed)
                    .put("repeatDetectCallMillis", repeatElapsed)
                    .put("firstInitializationMillis", firstInitialization)
                    .put("contourCandidates", firstCandidateCounts[CandidateSource.CONTOUR] ?: 0)
                    .put("regionCandidates", firstCandidateCounts[CandidateSource.REGION] ?: 0)
                    .put("rejectedCandidates", JSONObject(firstRejectedCounts))
                    .put("nativeHeapBeforeBytes", before)
                    .put("nativeHeapAfterBytes", Debug.getNativeHeapAllocatedSize())
                    .put("originalWidth", bounds.outWidth)
                    .put("originalHeight", bounds.outHeight)
                    .put("storedWidth", task.width)
                    .put("storedHeight", task.height)
                    .put(
                        "fieldAcceptance",
                        "PENDING: save/display latency, correction review, total workflow, memory peak and lifecycle experiments required",
                    )
            photos
                .analysisPhoto(task.photoName, config.analysisMaxSidePx, config.maxDecodedPixels)
                .use { analysis ->
                    report
                        .put("analysisWidth", analysis.bitmap.width)
                        .put("analysisHeight", analysis.bitmap.height)
                }
            if (split == "tuning") {
                val hough = HoughPipeDetector(photos).detect(task)
                val baseline = matchDetections(expected, hough.markers, tolerance)
                report
                    .put("houghMissing", baseline.missing)
                    .put("houghFalsePositive", baseline.falsePositive)
                report.put(
                    "originalNormalizedComparison",
                    compareOriginal(source, photos, task, expected, tolerance, config),
                )
            }
            // Only non-identifying sample IDs and metrics: never absolute photo paths or pixels.
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(0, Bundle().apply { putString("evaluationResult", report.toString()) })
        } catch (failure: DetectionFailure) {
            allEligible = false
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    0,
                    Bundle().apply {
                        putString(
                            "evaluationResult",
                            JSONObject()
                                .put("sampleId", sample.getString("sampleId"))
                                .put("split", split)
                                .put("configSha256", configHash)
                                .put("failureReason", failure.reason.name)
                                .put("fieldAcceptance", "PENDING/FAILED")
                                .toString(),
                        )
                    },
                )
        } finally {
            photos.delete(imported.first)
        }
    }
    if (split == "independent")
        assertTrue("독립 자료에서 사진별 보정량 기준 미충족. Task 7은 시작하지 않습니다.", allEligible)
}

internal fun validateEvaluationManifest(
    manifest: JSONObject,
    configHash: String,
): List<JSONObject> {
    require(manifest.getInt("schemaVersion") == 1 && manifest.getBoolean("groundTruthFrozen"))
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
        val tolerance = sample.getDouble("normalizedTolerance")
        require(tolerance.isFinite() && tolerance >= 0 && tolerance <= 1)
        val expected = readTruth(sample.getJSONArray("expected"))
        require(expected.size <= 300)
        require(expected.map { it.id }.distinct().size == expected.size)
        sample.getJSONArray("excluded").let { excluded ->
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

private suspend fun compareOriginal(
    source: File,
    photos: PhotoStore,
    task: CountTask,
    expected: List<Marker>,
    tolerance: Double,
    config: DetectionConfig,
): JSONObject {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth.toLong() * bounds.outHeight > config.maxDecodedPixels)
        return JSONObject().put("status", "PENDING: full original exceeds fixed pixel budget")
    val decoded =
        BitmapFactory.decodeFile(source.path) ?: return JSONObject().put("status", "READ_FAILURE")
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
        val metrics = matchDetections(expected, detected.markers, tolerance)
        return JSONObject()
            .put("status", "MEASURED normalized original, full scale")
            .put("missing", metrics.missing)
            .put("falsePositive", metrics.falsePositive)
    } finally {
        photos.delete(name)
        if (normalized !== decoded) normalized?.recycle()
        decoded.recycle()
    }
}
