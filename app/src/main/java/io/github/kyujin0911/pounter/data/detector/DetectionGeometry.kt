package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.Marker
import kotlin.math.*

enum class CandidateSource {
    CONTOUR,
    REGION,
}

data class EllipseCandidate(
    val centerXPx: Double,
    val centerYPx: Double,
    val semiMajorPx: Double,
    val semiMinorPx: Double,
    val angleRadians: Double,
    val sources: Set<CandidateSource>,
    val edgeSupport: Double,
    val fitError: Double,
    val contrast: Double,
) {
    init {
        require(
            listOf(
                    centerXPx,
                    centerYPx,
                    semiMajorPx,
                    semiMinorPx,
                    angleRadians,
                    edgeSupport,
                    fitError,
                    contrast,
                )
                .all { it.isFinite() }
        )
        require(semiMajorPx >= semiMinorPx && semiMinorPx > 0 && sources.isNotEmpty())
        require(edgeSupport in 0.0..1.0 && fitError >= 0 && contrast in 0.0..255.0)
    }

    internal fun boundaryPoint(angle: Double): Pair<Double, Double> {
        val x = semiMajorPx * cos(angle)
        val y = semiMinorPx * sin(angle)
        return Pair(
            centerXPx + x * cos(angleRadians) - y * sin(angleRadians),
            centerYPx + x * sin(angleRadians) + y * cos(angleRadians),
        )
    }

    internal fun normalizedRadius(x: Double, y: Double): Double {
        val dx = x - centerXPx
        val dy = y - centerYPx
        return hypot(
            (dx * cos(angleRadians) + dy * sin(angleRadians)) / semiMajorPx,
            (-dx * sin(angleRadians) + dy * cos(angleRadians)) / semiMinorPx,
        )
    }
}

/** Pure ROI-pixel geometry; quality and generator ownership stay outside this value. */
data class EllipseGeometry(
    val centerXPx: Double,
    val centerYPx: Double,
    val semiMajorPx: Double,
    val semiMinorPx: Double,
    val angleRadians: Double,
) {
    init {
        require(
            listOf(centerXPx, centerYPx, semiMajorPx, semiMinorPx, angleRadians).all {
                it.isFinite()
            }
        )
        require(semiMajorPx >= semiMinorPx && semiMinorPx > 0)
    }
}

data class AnalysisTransform(
    val photoWidthPx: Int,
    val photoHeightPx: Int,
    val cropLeftPx: Int,
    val cropTopPx: Int,
    val scaleX: Double,
    val scaleY: Double,
) {
    init {
        require(
            photoWidthPx > 0 &&
                photoHeightPx > 0 &&
                cropLeftPx in 0 until photoWidthPx &&
                cropTopPx in 0 until photoHeightPx &&
                scaleX.isFinite() &&
                scaleY.isFinite() &&
                scaleX > 0 &&
                scaleY > 0
        )
    }
}

fun toMarker(candidate: EllipseCandidate, transform: AnalysisTransform, markerId: String): Marker =
    toMarker(
        EllipseGeometry(
            candidate.centerXPx,
            candidate.centerYPx,
            candidate.semiMajorPx,
            candidate.semiMinorPx,
            candidate.angleRadians,
        ),
        transform,
        markerId,
    )

fun toMarker(candidate: EllipseGeometry, transform: AnalysisTransform, markerId: String): Marker {
    val c = cos(candidate.angleRadians)
    val s = sin(candidate.angleRadians)
    val a2 = candidate.semiMajorPx * candidate.semiMajorPx
    val b2 = candidate.semiMinorPx * candidate.semiMinorPx
    val xx = (a2 * c * c + b2 * s * s) / (transform.scaleX * transform.scaleX)
    val yy = (a2 * s * s + b2 * c * c) / (transform.scaleY * transform.scaleY)
    val xy = (a2 - b2) * c * s / (transform.scaleX * transform.scaleY)
    // Small eigenvalue of the inverse-scaled ellipse covariance, stable for long axes.
    val large = (xx + yy + hypot(xx - yy, 2 * xy)) / 2
    val small =
        (a2 * b2 / (transform.scaleX * transform.scaleX * transform.scaleY * transform.scaleY)) /
            large
    return Marker(
        markerId,
        ((candidate.centerXPx / transform.scaleX + transform.cropLeftPx) / transform.photoWidthPx)
            .toFloat(),
        ((candidate.centerYPx / transform.scaleY + transform.cropTopPx) / transform.photoHeightPx)
            .toFloat(),
        (sqrt(small) / transform.photoWidthPx).toFloat(),
    )
}
