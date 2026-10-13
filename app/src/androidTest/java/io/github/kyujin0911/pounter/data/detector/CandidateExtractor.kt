package io.github.kyujin0911.pounter.data.detector

import kotlin.coroutines.coroutineContext
import kotlin.math.*
import kotlinx.coroutines.ensureActive
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object CandidateExtractor {
    suspend fun extract(
        gray: Mat,
        config: DetectionConfig,
        onRejected: (String) -> Unit = {},
    ): List<EllipseCandidate> {
        val blurred = Mat()
        val edges = Mat()
        val binary = Mat()
        try {
            coroutineContext.ensureActive()
            Imgproc.medianBlur(gray, blurred, config.medianKernelSize)
            coroutineContext.ensureActive()
            Imgproc.Canny(blurred, edges, config.cannyLow, config.cannyHigh)
            val width = gray.cols()
            val height = gray.rows()
            val edgePixels = ByteArray(width * height)
            edges.get(0, 0, edgePixels)
            val intensities = ByteArray(width * height)
            gray.get(0, 0, intensities)
            val candidates = mutableListOf<EllipseCandidate>()
            suspend fun collect(mask: Mat, source: CandidateSource) {
                val contours = mutableListOf<MatOfPoint>()
                val hierarchy = Mat()
                try {
                    coroutineContext.ensureActive()
                    Imgproc.findContours(
                        mask,
                        contours,
                        hierarchy,
                        Imgproc.RETR_LIST,
                        Imgproc.CHAIN_APPROX_NONE,
                    )
                    // Bound raw contour work as well as accepted candidates; never return a partial
                    // list.
                    if (contours.size >= config.maxCandidates)
                        throw DetectionFailure(DetectionFailureReason.CANDIDATE_SATURATION)
                    for (contour in contours) {
                        coroutineContext.ensureActive()
                        val points = contour.toArray()
                        if (points.size < 5) {
                            onRejected("POINT_COUNT")
                            continue
                        }
                        val curve = MatOfPoint2f(*points)
                        val ellipse =
                            try {
                                Imgproc.fitEllipse(curve)
                            } catch (_: CvException) {
                                onRejected("FIT_FAILURE")
                                continue
                            } finally {
                                curve.release()
                            }
                        val major = max(ellipse.size.width, ellipse.size.height) / 2
                        val minor = min(ellipse.size.width, ellipse.size.height) / 2
                        val cx = ellipse.center.x
                        val cy = ellipse.center.y
                        if (
                            !listOf(major, minor, cx, cy, ellipse.angle).all { it.isFinite() } ||
                                minor < config.minSemiAxisPx ||
                                major > config.maxSemiAxisPx ||
                                major / minor > config.maxAxisRatio
                        ) {
                            onRejected("GEOMETRY")
                            continue
                        }
                        val angle =
                            (ellipse.angle +
                                if (ellipse.size.width < ellipse.size.height) 90 else 0) * PI / 180
                        val shape =
                            EllipseCandidate(
                                cx,
                                cy,
                                major,
                                minor,
                                angle,
                                setOf(source),
                                0.0,
                                0.0,
                                0.0,
                            )
                        val fitError =
                            points.sumOf { abs(shape.normalizedRadius(it.x, it.y) - 1) } /
                                points.size
                        var edgeHits = 0
                        var innerSum = 0.0
                        var outerSum = 0.0
                        var validSamples = 0
                        for (sample in 0 until 72) {
                            val (x, y) = shape.boundaryPoint(sample * 2 * PI / 72)
                            val px = x.roundToInt()
                            val py = y.roundToInt()
                            var hit = false
                            for (dy in -2..2) for (dx in -2..2) {
                                val ex = px + dx
                                val ey = py + dy
                                if (
                                    ex in 0 until width &&
                                        ey in 0 until height &&
                                        edgePixels[ey * width + ex].toInt() != 0
                                )
                                    hit = true
                            }
                            if (hit) edgeHits++
                            val ix = (cx + (x - cx) * .94).roundToInt()
                            val iy = (cy + (y - cy) * .94).roundToInt()
                            val ox = (cx + (x - cx) * 1.06).roundToInt()
                            val oy = (cy + (y - cy) * 1.06).roundToInt()
                            if (
                                ix in 0 until width &&
                                    iy in 0 until height &&
                                    ox in 0 until width &&
                                    oy in 0 until height
                            ) {
                                innerSum += (intensities[iy * width + ix].toInt() and 255)
                                outerSum += (intensities[oy * width + ox].toInt() and 255)
                                validSamples++
                            }
                        }
                        val support = edgeHits / 72.0
                        val contrast =
                            if (validSamples == 0) 0.0 else abs(innerSum - outerSum) / validSamples
                        if (
                            support >= config.minEdgeSupport &&
                                fitError <= config.maxFitError &&
                                contrast >= config.minContrast
                        ) {
                            candidates +=
                                shape.copy(
                                    edgeSupport = support,
                                    fitError = fitError,
                                    contrast = contrast,
                                )
                            if (candidates.size >= config.maxCandidates)
                                throw DetectionFailure(DetectionFailureReason.CANDIDATE_SATURATION)
                        } else onRejected("QUALITY")
                    }
                } finally {
                    contours.forEach { it.release() }
                    hierarchy.release()
                }
            }
            collect(edges, CandidateSource.CONTOUR)
            for (polarity in listOf(Imgproc.THRESH_BINARY, Imgproc.THRESH_BINARY_INV)) {
                coroutineContext.ensureActive()
                Imgproc.adaptiveThreshold(
                    blurred,
                    binary,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    polarity,
                    config.adaptiveBlockSize,
                    config.adaptiveOffset,
                )
                collect(binary, CandidateSource.REGION)
            }
            return candidates
        } finally {
            blurred.release()
            edges.release()
            binary.release()
        }
    }
}
