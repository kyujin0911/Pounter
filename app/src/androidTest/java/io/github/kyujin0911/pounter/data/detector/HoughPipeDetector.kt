package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.core.model.suppressConcentric
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/** Prototype for instrumentation/probes; never described as a validated pipe detector. */
class HoughPipeDetector(private val photos: PhotoStore) : PipeDetector {
    override suspend fun detect(task: CountTask): Detection =
        withContext(Dispatchers.Default) {
            check(OpenCVLoader.initLocal()) { "검출 라이브러리를 시작할 수 없습니다." }
            val started = System.nanoTime()
            val bitmap = photos.bitmap(task.photoName, 1600)
            val source = Mat()
            val gray = Mat()
            val circles = Mat()
            val edges = Mat()
            var crop: Mat? = null
            try {
                Utils.bitmapToMat(bitmap, source)
                val w = source.cols()
                val h = source.rows()
                val r = task.region
                val x = (r.left * w).toInt().coerceIn(0, w - 1)
                val y = (r.top * h).toInt().coerceIn(0, h - 1)
                val right = ceil(r.right * w).toInt().coerceIn(x + 1, w)
                val bottom = ceil(r.bottom * h).toInt().coerceIn(y + 1, h)
                crop = source.submat(Rect(x, y, right - x, bottom - y))
                Imgproc.cvtColor(crop, gray, Imgproc.COLOR_RGBA2GRAY)
                if (min(gray.rows(), gray.cols()) < 20) error("계수 영역이 너무 작습니다.")
                Imgproc.medianBlur(gray, gray, 5)
                val short = min(gray.rows(), gray.cols())
                Imgproc.HoughCircles(
                    gray,
                    circles,
                    Imgproc.HOUGH_GRADIENT,
                    1.2,
                    short / 60.0,
                    100.0,
                    30.0,
                    max(6, short / 120),
                    short / 5,
                )
                coroutineContext.ensureActive()
                Imgproc.Canny(gray, edges, 50.0, 100.0)
                val edgePixels = ByteArray(edges.rows() * edges.cols())
                edges.get(0, 0, edgePixels)
                fun supported(c: DoubleArray): Boolean {
                    var hits = 0
                    for (sample in 0 until 72) {
                        val angle = sample * 2 * PI / 72
                        val px = (c[0] + c[2] * cos(angle)).roundToInt()
                        val py = (c[1] + c[2] * sin(angle)).roundToInt()
                        var found = false
                        for (dy in -2..2) for (dx in -2..2) {
                            val ex = px + dx
                            val ey = py + dy
                            if (
                                ex in 0 until edges.cols() &&
                                    ey in 0 until edges.rows() &&
                                    edgePixels[ey * edges.cols() + ex].toInt() != 0
                            )
                                found = true
                        }
                        if (found) hits++
                    }
                    return hits >=
                        47 // At least 65% of the proposed circumference must have edge support.
                }
                val candidates =
                    (0 until circles.cols())
                        .mapNotNull { i ->
                            val c = circles.get(0, i)
                            if (!supported(c)) return@mapNotNull null
                            Marker(
                                UUID.randomUUID().toString(),
                                ((c[0] + x) / w).toFloat(),
                                ((c[1] + y) / h).toFloat(),
                                (c[2] / w).toFloat(),
                            )
                        }
                        .filter { r.contains(it.x, it.y) }
                val retained = suppressConcentric(candidates, w, h)
                if (retained.size > 300) error("300개를 초과하는 후보가 검출되었습니다. 영역과 촬영 조건을 확인해주세요.")
                Detection(retained, (System.nanoTime() - started) / 1_000_000)
            } finally {
                crop?.release()
                source.release()
                gray.release()
                circles.release()
                edges.release()
                bitmap.recycle()
            }
        }
}
