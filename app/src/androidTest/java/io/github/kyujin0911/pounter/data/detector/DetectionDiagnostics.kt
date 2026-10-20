package io.github.kyujin0911.pounter.data.detector

/** Run-local values only. No image/native ownership; budgets never bound detection work. */
class DetectionDiagnostics(private val maxEntries: Int = 256, private val maxPoints: Int = 1024) {
    init {
        require(maxEntries >= 0 && maxPoints >= 0)
    }

    data class Geometry(
        val cxPx: Double,
        val cyPx: Double,
        val aPx: Double,
        val bPx: Double,
        val angleRadians: Double,
    )

    data class Entry(
        val observationId: Int,
        val mask: String,
        val bbox: List<Double>,
        val points: List<Pair<Double, Double>>,
        val geometry: Geometry? = null,
        val stage: String = "raw",
        val reason: String = "UNRESOLVED",
        val quality: List<Double>? = null,
        val acceptedIndex: Int? = null,
        val representativeIndex: Int? = null,
    )

    data class Snapshot(
        val totals: Map<String, Int>,
        val entries: List<Entry>,
        val truncated: Boolean,
        val status: String,
    )

    private val totals = mutableMapOf<String, Int>()
    private val entries = mutableListOf<Entry>()
    private var pointCount = 0
    private var nextId = 0
    private var truncated = false
    private var status = "STARTED"

    fun reset() {
        totals.clear()
        entries.clear()
        pointCount = 0
        nextId = 0
        truncated = false
        status = "STARTED"
        for (stage in
            listOf("raw", "fit", "geometry", "quality", "mergeInput", "mergeOutput")) totals[
            stage] = 0
    }

    fun count(stage: String, mask: String = "", amount: Int = 1) {
        totals[stage] = (totals[stage] ?: 0) + amount
        if (mask.isNotEmpty()) totals["$mask:$stage"] = (totals["$mask:$stage"] ?: 0) + amount
    }

    fun raw(mask: String, points: List<Pair<Double, Double>>): Int {
        val id = nextId++
        if (entries.size >= maxEntries) {
            truncated = true
            return id
        }
        val kept = points.take((maxPoints - pointCount).coerceAtLeast(0))
        if (kept.size != points.size) truncated = true
        pointCount += kept.size
        val bbox =
            if (points.isEmpty()) emptyList()
            else
                listOf(
                    points.minOf { it.first },
                    points.minOf { it.second },
                    points.maxOf { it.first },
                    points.maxOf { it.second },
                )
        entries += Entry(id, mask, bbox, kept)
        return id
    }

    fun observe(
        id: Int,
        stage: String,
        reason: String = "UNRESOLVED",
        geometry: Geometry? = null,
        quality: List<Double>? = null,
        acceptedIndex: Int? = null,
    ) {
        if (stage == "rejected") count("rejected:$reason")
        val index = entries.indexOfFirst { it.observationId == id }
        if (index < 0) return
        entries[index] =
            entries[index].copy(
                stage = stage,
                reason = reason,
                geometry = geometry ?: entries[index].geometry,
                quality = quality,
                acceptedIndex = acceptedIndex,
            )
    }

    fun representative(candidateIndex: Int, representativeIndex: Int) {
        val index = entries.indexOfFirst { it.acceptedIndex == candidateIndex }
        if (index >= 0)
            entries[index] = entries[index].copy(representativeIndex = representativeIndex)
    }

    fun finish(status: String) {
        this.status = status
    }

    fun snapshot() = Snapshot(totals.toMap(), entries.toList(), truncated, status)
}

internal fun mergeWithDiagnostics(
    candidates: List<EllipseCandidate>,
    policy: MergePolicy,
    diagnostics: DetectionDiagnostics?,
    allCandidates: List<EllipseCandidate> = candidates,
): List<EllipseCandidate> =
    mergeCandidates(candidates, policy) { candidate, representative ->
        diagnostics?.representative(allCandidates.indexOfFirst { it === candidate }, representative)
    }
