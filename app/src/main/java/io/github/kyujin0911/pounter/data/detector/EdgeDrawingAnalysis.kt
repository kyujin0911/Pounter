package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import kotlin.math.*
import kotlinx.coroutines.*
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/** Caller serializes detection; all Mat/Bitmap ownership ends before return. */
internal suspend fun analyzeEdgeDrawing(
    photos: PhotoStore,
    task: CountTask,
    version: String,
    generateGeometry: suspend (Mat) -> DoubleArray,
    select: (Mat, DoubleArray, () -> Unit) -> List<BRepresentative>,
    observeInput: ((Mat, Int, Int, List<Int>, Double, Double) -> Unit)? = null,
    observeRepresentatives: ((List<BRepresentative>) -> Unit)? = null,
): Detection {
    val started = System.nanoTime()
    try {
        if (!OpenCVLoader.initLocal()) throw DetectionFailure(DetectionFailureReason.INITIALIZATION)
        return photos.analysisPhoto(task.photoName, 1600, 4_000_000L).use { photo ->
            currentCoroutineContext().ensureActive()
            if (photo.photoWidthPx != task.width || photo.photoHeightPx != task.height)
                throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
            val rgba = Mat()
            val gray = Mat()
            var crop: Mat? = null
            try {
                Utils.bitmapToMat(photo.bitmap, rgba)
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
                if (min(crop!!.rows(), crop!!.cols()) < max(3, 31))
                    throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
                Imgproc.cvtColor(crop, gray, Imgproc.COLOR_RGBA2GRAY)
                observeInput?.invoke(
                    gray,
                    photo.bitmap.width,
                    photo.bitmap.height,
                    listOf(left, top, right, bottom),
                    sx,
                    sy,
                )
                currentCoroutineContext().ensureActive()
                val raw =
                    try {
                        generateGeometry(gray)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IllegalArgumentException) {
                        throw e
                    } catch (e: Exception) {
                        throw BNativeException(e)
                    }
                val activeContext = currentCoroutineContext()
                val representatives = select(gray, raw) { activeContext.ensureActive() }
                currentCoroutineContext().ensureActive()
                val transform = AnalysisTransform(task.width, task.height, 0, 0, sx, sy)
                val inside =
                    representatives.filter {
                        task.region.contains(
                            ((it.anchor.geometry.centerXPx + left) / sx / task.width).toFloat(),
                            ((it.anchor.geometry.centerYPx + top) / sy / task.height).toFloat(),
                        )
                    }
                observeRepresentatives?.invoke(inside)
                if (inside.size > 300) throw DetectionFailure(DetectionFailureReason.COUNT_LIMIT)
                val markers =
                    inside.map { r ->
                        toMarker(
                            r.anchor.geometry.copy(
                                centerXPx = r.anchor.geometry.centerXPx + left,
                                centerYPx = r.anchor.geometry.centerYPx + top,
                            ),
                            transform,
                            "b-${r.anchor.id}",
                        )
                    }
                currentCoroutineContext().ensureActive()
                Detection(
                    markers,
                    (System.nanoTime() - started) / 1_000_000,
                    AnalysisMetadata(version),
                )
            } finally {
                crop?.release()
                gray.release()
                rgba.release()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: DetectionFailure) {
        throw e
    } catch (e: BNativeException) {
        throw DetectionFailure(DetectionFailureReason.PROCESSING, e.cause)
    } catch (e: IllegalArgumentException) {
        throw DetectionFailure(DetectionFailureReason.INVALID_INPUT, e)
    } catch (e: LinkageError) {
        throw DetectionFailure(DetectionFailureReason.INITIALIZATION, e)
    } catch (e: OutOfMemoryError) {
        throw DetectionFailure(DetectionFailureReason.PROCESSING, e)
    } catch (e: Exception) {
        throw DetectionFailure(DetectionFailureReason.PROCESSING, e)
    }
}

private class BNativeException(cause: Throwable) : Exception(cause)
