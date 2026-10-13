package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.AnalysisMetadata
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.util.UUID
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/** Evaluation wiring only; instantiate once per evaluation container. */
class CompositePipeDetector(
    private val photos: PhotoStore,
    private val config: DetectionConfig,
    private val initialize: () -> Boolean = { OpenCVLoader.initLocal() },
    private val nativeProbe: () -> Unit = {},
) : PipeDetector {
    private val executionMutex = Mutex()
    private var initialized = false
    var initializationMillis: Long = 0
        private set

    override suspend fun detect(task: CountTask): Detection = detect(task) {}

    override suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit): Detection =
        withContext(Dispatchers.Default) {
            onStage(AnalysisStage.WAITING)
            executionMutex.withLock {
                onStage(AnalysisStage.PROCESSING)
                ensureActive()
                val started = System.nanoTime()
                if (!initialized) {
                    val initStarted = System.nanoTime()
                    val success =
                        try {
                            initialize()
                        } catch (failure: Exception) {
                            throw DetectionFailure(DetectionFailureReason.INITIALIZATION, failure)
                        } catch (failure: LinkageError) {
                            throw DetectionFailure(DetectionFailureReason.INITIALIZATION, failure)
                        }
                    initializationMillis = (System.nanoTime() - initStarted) / 1_000_000
                    if (!success) throw DetectionFailure(DetectionFailureReason.INITIALIZATION)
                    initialized = true
                }
                ensureActive()
                try {
                    photos
                        .analysisPhoto(
                            task.photoName,
                            config.analysisMaxSidePx,
                            config.maxDecodedPixels,
                        )
                        .use { photo ->
                            ensureActive()
                            if (
                                photo.photoWidthPx != task.width ||
                                    photo.photoHeightPx != task.height
                            )
                                throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
                            val rgba = Mat()
                            val gray = Mat()
                            var crop: Mat? = null
                            try {
                                nativeProbe()
                                ensureActive()
                                Utils.bitmapToMat(photo.bitmap, rgba)
                                ensureActive()
                                val sx = photo.bitmap.width.toDouble() / photo.photoWidthPx
                                val sy = photo.bitmap.height.toDouble() / photo.photoHeightPx
                                val left =
                                    floor(task.region.left * photo.bitmap.width)
                                        .toInt()
                                        .coerceIn(0, photo.bitmap.width - 1)
                                val top =
                                    floor(task.region.top * photo.bitmap.height)
                                        .toInt()
                                        .coerceIn(0, photo.bitmap.height - 1)
                                val right =
                                    ceil(task.region.right * photo.bitmap.width)
                                        .toInt()
                                        .coerceIn(left + 1, photo.bitmap.width)
                                val bottom =
                                    ceil(task.region.bottom * photo.bitmap.height)
                                        .toInt()
                                        .coerceIn(top + 1, photo.bitmap.height)
                                crop = rgba.submat(Rect(left, top, right - left, bottom - top))
                                if (
                                    min(crop!!.rows(), crop!!.cols()) <
                                        max(config.medianKernelSize, config.adaptiveBlockSize)
                                )
                                    throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
                                Imgproc.cvtColor(crop, gray, Imgproc.COLOR_RGBA2GRAY)
                                ensureActive()
                                val candidates = CandidateExtractor.extract(gray, config)
                                ensureActive()
                                // Crop origin is in decoded pixels; convert with the actual
                                // per-axis scale.
                                val transform =
                                    AnalysisTransform(task.width, task.height, 0, 0, sx, sy)
                                val fullCandidates =
                                    candidates.map {
                                        it.copy(
                                            centerXPx = it.centerXPx + left,
                                            centerYPx = it.centerYPx + top,
                                        )
                                    }
                                val inside =
                                    fullCandidates.filter {
                                        task.region.contains(
                                            (it.centerXPx / sx / task.width).toFloat(),
                                            (it.centerYPx / sy / task.height).toFloat(),
                                        )
                                    }
                                val merged = mergeCandidates(inside, config.mergePolicy)
                                if (merged.size > 300)
                                    throw DetectionFailure(DetectionFailureReason.COUNT_LIMIT)
                                val markers =
                                    merged.map {
                                        toMarker(it, transform, UUID.randomUUID().toString())
                                    }
                                ensureActive()
                                Detection(
                                    markers,
                                    (System.nanoTime() - started) / 1_000_000,
                                    AnalysisMetadata(config.detectorVersion),
                                )
                            } finally {
                                crop?.release()
                                gray.release()
                                rgba.release()
                            }
                        }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: DetectionFailure) {
                    throw failure
                } catch (failure: Exception) {
                    throw DetectionFailure(DetectionFailureReason.PROCESSING, failure)
                }
            }
        }
}
