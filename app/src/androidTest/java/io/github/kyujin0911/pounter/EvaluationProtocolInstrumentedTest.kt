package io.github.kyujin0911.pounter

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EvaluationProtocolInstrumentedTest {
    private fun sample(id: String, session: String, bundle: String, split: String) =
        JSONObject()
            .put("sampleId", id)
            .put("sessionId", session)
            .put("bundleId", bundle)
            .put("split", split)
            .put("photo", "photo.jpg")
            .put("supportGroup", JSONArray(listOf("yellow", "depth")))
            .put("region", JSONArray(listOf(0, 0, 1, 1)))
            .put("expected", JSONArray())
            .put("excluded", JSONArray())
            .put("normalizedTolerance", .02)
            .put("zeroFalsePositiveAllowance", 0)

    private fun manifest() =
        JSONObject()
            .put("schemaVersion", 1)
            .put("groundTruthFrozen", true)
            .put("configSha256", "hash")
            .put(
                "samples",
                JSONArray()
                    .put(sample("t", "s1", "b1", "tuning"))
                    .put(sample("i", "s2", "b2", "independent")),
            )

    @Test(expected = IllegalArgumentException::class)
    fun unknownSessionCannotBeIndependent() {
        val m = manifest()
        m.getJSONArray("samples").getJSONObject(1).put("sessionId", "unknown")
        validateEvaluationManifest(m, "hash")
    }

    @Test
    fun schema1KeepsOriginalMetric() {
        assertEquals("metric-v1", evaluationMetricVersion(manifest()))
    }

    @Test
    fun comparisonFailurePreservesPrimaryResult() =
        kotlinx.coroutines.runBlocking {
            val report = JSONObject()
            recordEvaluationStage(report, "primary") { JSONObject().put("matched", 7) }
            recordEvaluationStage(report, "comparison") {
                throw java.io.IOException("bad comparison")
            }
            assertEquals(
                7,
                report.getJSONObject("primary").getJSONObject("result").getInt("matched"),
            )
            assertEquals("FAILURE", report.getJSONObject("comparison").getString("status"))
        }

    @Test
    fun badImportDoesNotEraseOtherSamples() =
        kotlinx.coroutines.runBlocking {
            val rows = listOf(JSONObject(), JSONObject())
            recordEvaluationStage(rows[0], "import") { throw java.io.IOException("bad input") }
            recordEvaluationStage(rows[1], "import") { JSONObject().put("width", 100) }
            assertEquals("FAILURE", rows[0].getJSONObject("import").getString("status"))
            assertEquals("SUCCESS", rows[1].getJSONObject("import").getString("status"))
        }

    @Test
    fun cancellationPropagates() =
        kotlinx.coroutines.runBlocking {
            val report = JSONObject()
            try {
                recordEvaluationStage(report, "repeat") {
                    throw kotlinx.coroutines.CancellationException("cancel")
                }
                fail("cancel must propagate")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertEquals("CANCELLED", report.getJSONObject("repeat").getString("status"))
            }
        }

    @Test(expected = IllegalArgumentException::class)
    fun excludedIdsCannotOverlapTruth() {
        val m = manifest()
        val sample = m.getJSONArray("samples").getJSONObject(0)
        sample.put(
            "expected",
            JSONArray().put(JSONObject().put("id", "a").put("x", .5).put("y", .5)),
        )
        sample.put(
            "excluded",
            JSONArray()
                .put(
                    JSONObject()
                        .put("id", "a")
                        .put("x", .6)
                        .put("y", .6)
                        .put("reason", "other bundle")
                ),
        )
        validateEvaluationManifest(m, "hash")
    }

    @Test
    fun startedStageIsPublishedBeforeAction() =
        kotlinx.coroutines.runBlocking {
            val report = JSONObject()
            val snapshots = mutableListOf<String>()
            recordEvaluationStage(report, "primary", { snapshots += report.toString() }) {
                assertEquals(
                    "STARTED",
                    JSONObject(snapshots.single()).getJSONObject("primary").getString("status"),
                )
                JSONObject().put("matched", 1)
            }
            assertEquals(
                "SUCCESS",
                JSONObject(snapshots.last()).getJSONObject("primary").getString("status"),
            )
        }

    @Test
    fun originalBudgetSkipIsNotSuccess() =
        kotlinx.coroutines.runBlocking {
            val context =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext
            val source = java.io.File(context.cacheDir, "original-budget.png")
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    20,
                    20,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            source.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            try {
                val report = JSONObject()
                recordEvaluationStage(report, "original") {
                    compareOriginal(
                        source,
                        io.github.kyujin0911.pounter.data.local.photo.PhotoStore(context),
                        io.github.kyujin0911.pounter.core.model.CountTask(
                            "t",
                            "unused.jpg",
                            20,
                            20,
                            1,
                        ),
                        emptyList(),
                        .02,
                        io.github.kyujin0911.pounter.data.detector.DetectionConfig.synthetic()
                            .copy(maxDecodedPixels = 100),
                        { JSONObject() },
                    )
                }
                assertEquals("SKIP", report.getJSONObject("original").getString("status"))
            } finally {
                source.delete()
            }
        }

    @Test
    fun originalReadFailureIsNotSuccess() =
        kotlinx.coroutines.runBlocking {
            val context =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext
            val report = JSONObject()
            recordEvaluationStage(report, "original") {
                compareOriginal(
                    java.io.File(context.cacheDir, "missing-original.png"),
                    io.github.kyujin0911.pounter.data.local.photo.PhotoStore(context),
                    io.github.kyujin0911.pounter.core.model.CountTask("t", "unused.jpg", 20, 20, 1),
                    emptyList(),
                    .02,
                    io.github.kyujin0911.pounter.data.detector.DetectionConfig.synthetic(),
                    { JSONObject() },
                )
            }
            assertEquals("FAILURE", report.getJSONObject("original").getString("status"))
        }

    private fun schema2(frozen: Boolean = false): JSONObject {
        val truth =
            JSONArray()
                .put(
                    JSONObject()
                        .put("id", "a")
                        .put("x", .5)
                        .put("y", .5)
                        .put("bPx", 20)
                        .put("uPx", 4)
                        .put("reviewStatus", "DRAFT")
                )
        val hash =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(truth.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
        return JSONObject()
            .put("schemaVersion", 2)
            .put("groundTruthFrozen", frozen)
            .put("datasetVersion", "synthetic-data")
            .put("truthVersion", "draft-1")
            .put("annotationVersion", "manual-1")
            .put("configSha256", "a".repeat(64))
            .put(
                "samples",
                JSONArray()
                    .put(
                        sample("t", "unknown", "unknown", "tuning")
                            .put("sourceSha256", "b".repeat(64))
                            .put("truthSha256", hash)
                            .put("confirmedCount", 1)
                            .put("referenceWidth", 1000)
                            .put("referenceHeight", 500)
                            .put("expected", truth)
                            .put("tolerancesPx", JSONObject().put("a", 5))
                    ),
            )
    }

    @Test
    fun draftSchema2IsReadableForDevelopmentOnly() {
        assertEquals(1, validateEvaluationManifest(schema2(), "a".repeat(64)).size)
        assertEquals("metric-v2", evaluationMetricVersion(schema2()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun draftCannotBeFrozen() {
        validateEvaluationManifest(schema2(true), "a".repeat(64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun changedTruthHashIsRejected() {
        val m = schema2()
        m.getJSONArray("samples").getJSONObject(0).put("truthSha256", "c".repeat(64))
        validateEvaluationManifest(m, "a".repeat(64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun badSourceHashIsRejected() {
        val m = schema2()
        m.getJSONArray("samples").getJSONObject(0).put("sourceSha256", "invalid")
        validateEvaluationManifest(m, "a".repeat(64))
    }

    @Test
    fun runnerBadImportPreservesNextPrimaryAndActualCoordinateMetadata() =
        kotlinx.coroutines.runBlocking {
            val context =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext
            val folder =
                java.io
                    .File(context.cacheDir, "runner-fixture-${java.util.UUID.randomUUID()}")
                    .apply { mkdirs() }
            val photo = java.io.File(folder, "blank.png")
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    1000,
                    500,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            try {
                bitmap.eraseColor(android.graphics.Color.rgb(30, 30, 30))
                photo.outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
            java.io.File(folder, "bad.png").writeBytes(byteArrayOf())
            val config =
                JSONObject(
                    """{"detectorVersion":"synthetic-v1","analysisMaxSidePx":1600,"maxDecodedPixels":4000000,"maxCandidates":10000,"medianKernelSize":3,"cannyLow":40,"cannyHigh":90,"adaptiveBlockSize":31,"adaptiveOffset":4,"minSemiAxisPx":7,"maxSemiAxisPx":80,"maxAxisRatio":2.5,"minEdgeSupport":0.65,"maxFitError":0.15,"minContrast":0,"mergePolicy":{"centerToleranceRatio":0.3,"maxBoundaryGapRatio":0.5,"minContainmentFraction":0.85}}"""
                )
            val configFile =
                java.io.File(folder, "config.json").apply { writeText(config.toString()) }
            fun hash(bytes: ByteArray) =
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                }
            fun row(id: String, name: String) =
                sample(id, "unknown", "unknown", "tuning")
                    .put("photo", name)
                    .put("sourceSha256", hash(java.io.File(folder, name).readBytes()))
                    .put("referenceWidth", 1000)
                    .put("referenceHeight", 500)
                    .put("confirmedCount", 0)
                    .put("truthSha256", hash("[]".toByteArray()))
                    .put("tolerancesPx", JSONObject())
            val manifest =
                JSONObject()
                    .put("schemaVersion", 2)
                    .put("groundTruthFrozen", false)
                    .put("datasetVersion", "synthetic")
                    .put("truthVersion", "empty-draft")
                    .put("annotationVersion", "synthetic")
                    .put("configSha256", hash(configFile.readBytes()))
                    .put(
                        "samples",
                        JSONArray().put(row("bad", "bad.png")).put(row("blank", "blank.png")),
                    )
            val manifestFile =
                java.io.File(folder, "manifest.json").apply { writeText(manifest.toString()) }
            val reportsDir = java.io.File(context.cacheDir, "evaluation-private")
            val before = reportsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            evaluateImages(
                "tuning",
                android.os.Bundle().apply {
                    putString("evaluationManifest", manifestFile.path)
                    putString("evaluationConfig", configFile.path)
                },
            )
            val journal =
                reportsDir.listFiles()!!.single { it.name !in before && it.extension == "jsonl" }
            val reports = journal.readLines().map { JSONObject(it) }
            val failed = reports.last { it.getString("sampleId") == "bad" }
            val valid = reports.last { it.getString("sampleId") == "blank" }
            assertEquals("FAILURE", failed.getJSONObject("import").getString("status"))
            assertEquals("SUCCESS", valid.getJSONObject("primary").getString("status"))
            assertEquals(
                0,
                valid.getJSONObject("primary").getJSONObject("result").getInt("falsePositive"),
            )
            assertEquals(
                1000,
                valid.getJSONObject("import").getJSONObject("result").getInt("referenceWidth"),
            )
            assertEquals(500, valid.getJSONObject("coordinates").getInt("decodeHeight"))
            assertEquals("SKIP", valid.getJSONObject("hough").getString("status"))
            assertTrue(
                reports.any { it.optJSONObject("primary")?.optString("status") == "STARTED" }
            )
            journal.delete()
            folder.deleteRecursively()
            Unit
        }

    @Test
    fun frozenSeparatedDataIsReadable() {
        assertEquals(2, validateEvaluationManifest(manifest(), "hash").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sharedSessionCannotBeIndependent() {
        val m = manifest()
        m.getJSONArray("samples").getJSONObject(1).put("sessionId", "s1")
        validateEvaluationManifest(m, "hash")
    }

    @Test(expected = IllegalArgumentException::class)
    fun sharedBundleCannotBeIndependent() {
        val m = manifest()
        m.getJSONArray("samples").getJSONObject(1).put("bundleId", "b1")
        validateEvaluationManifest(m, "hash")
    }

    @Test(expected = IllegalArgumentException::class)
    fun configChangeInvalidatesFrozenData() {
        validateEvaluationManifest(manifest(), "new-hash")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unfrozenTruthRejected() {
        validateEvaluationManifest(manifest().put("groundTruthFrozen", false), "hash")
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroAllowanceMustBePredeclaredAndNonNegative() {
        val m = manifest()
        m.getJSONArray("samples").getJSONObject(1).put("zeroFalsePositiveAllowance", -1)
        validateEvaluationManifest(m, "hash")
    }
}
