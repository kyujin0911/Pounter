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
