package io.github.kyujin0911.pounter.data.detector

import org.opencv.core.*

internal data class BRun(
    val rawCount: Int,
    val gateCount: Int,
    val qualityCount: Int,
    val representatives: List<BRepresentative>,
    val observations: List<BObservation>,
    val truncated: Boolean,
    val sameRimCount: Int = representatives.size,
    val objectMerge: BObjectRun? = null,
    val radialObject: BRadialRun? = null,
    val edgeQualities: Map<Int, BEdgeQuality> = emptyMap(),
)

internal fun processBGeometry(
    gray: Mat,
    raw: DoubleArray,
    entryBudget: Int = 25000,
    maxCandidates: Int = 10000,
    qualityPolicy: BQualityPolicy? = null,
    objectPolicy: BObjectPolicy? = null,
    maxPairChecks: Int = 1_000_000,
    radialObjects: Boolean = false,
    localObjects: Boolean = false,
    sharedEdges: Boolean = false,
    continuousFaces: Boolean = false,
    supportedFaces: Boolean = false,
    checkActive: () -> Unit = {},
): BRun {
    require(!supportedFaces || sharedEdges)
    require(!(supportedFaces && continuousFaces))
    require(!continuousFaces || sharedEdges)
    require(!sharedEdges || localObjects)
    require(!(radialObjects && localObjects))
    require(!(radialObjects || localObjects) || objectPolicy == null)
    val qualities = mutableMapOf<Int, BEdgeQuality>()
    val base =
        processBBaseGeometry(
            gray,
            raw,
            entryBudget,
            maxCandidates,
            maxPairChecks,
            observe = { o, pixels, width, height ->
                if (qualityPolicy == null || o.support == null) o
                else {
                    val quality = measureBEdgeQuality(pixels, width, height, o.geometry)
                    check(quality.support == o.support)
                    qualities[o.id] = quality
                    if (o.stage == "quality" && qualityPolicy.rejects(quality))
                        o.copy(stage = "GAP_REJECTED")
                    else o
                }
            },
            checkActive = checkActive,
        )
    val sameRim = base.representatives
    val objects =
        objectPolicy?.let {
            groupBCrossSectionBoundaries(gray, sameRim, it, entryBudget, maxPairChecks)
        }
    val radial =
        if (localObjects)
            selectBLocalObjects(gray, sameRim, entryBudget, maxPairChecks, checkActive)
        else if (radialObjects)
            selectBRadialObjects(gray, sameRim, entryBudget, maxPairChecks, checkActive)
        else null
    val selected =
        if (sharedEdges)
            selectBSharedEdges(gray, radial!!.representatives, maxPairChecks, checkActive)
        else radial?.representatives ?: objects?.representatives ?: sameRim
    val continuous =
        if (continuousFaces) selectBContinuousFaces(gray, selected, maxPairChecks, checkActive)
        else selected
    return BRun(
        base.rawCount,
        base.gateCount,
        base.qualityCount,
        if (supportedFaces) selectBSupportedFaces(gray, selected, maxPairChecks, checkActive)
        else continuous,
        base.observations,
        base.truncated,
        sameRim.size,
        objects,
        radial,
        qualities,
    )
}
