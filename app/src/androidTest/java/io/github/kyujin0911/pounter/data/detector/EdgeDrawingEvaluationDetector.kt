package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.security.MessageDigest
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opencv.android.*
import org.opencv.core.*

internal data class BInput(
    val graySha256: String,
    val decodeWidth: Int,
    val decodeHeight: Int,
    val roi: List<Int>,
    val scaleX: Double,
    val scaleY: Double,
)

/** Local evaluation only. R0/DI/runtime/save contracts remain untouched. */
internal class EdgeDrawingEvaluationDetector(
    private val photos: PhotoStore,
    private val entryBudget: Int = 25000,
    private val nativeProbe: () -> Unit = {},
    private val objectPolicy: BObjectPolicy? = null,
    private val maxPairChecks: Int = 1_000_000,
    private val radialObjects: Boolean = false,
    private val localObjects: Boolean = false,
    private val sharedEdges: Boolean = false,
    private val continuousFaces: Boolean = false,
    private val supportedFaces: Boolean = false,
    private val generateGeometry: suspend (Mat) -> DoubleArray = {
        EdgeDrawingTestNative.generate(it, nativeProbe)
    },
) : PipeDetector {
    private val mutex = Mutex()
    var lastRun: BRun? = null
        private set

    var lastInput: BInput? = null
        private set

    var status: String = "NOT_STARTED"
        private set

    override suspend fun detect(task: CountTask): Detection = detect(task) {}

    override suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit): Detection =
        withContext(Dispatchers.Default) {
            onStage(AnalysisStage.WAITING)
            mutex.withLock {
                ensureActive()
                lastRun = null
                lastInput = null
                status = "STARTED"
                onStage(AnalysisStage.PROCESSING)
                try {
                    val result =
                        analyzeEdgeDrawing(
                            photos,
                            task,
                            if (supportedFaces) "$B_VERSION-supported-faces-v1"
                            else if (continuousFaces) "$B_VERSION-continuous-faces-v1"
                            else if (sharedEdges) "$B_VERSION-shared-edges-v1"
                            else if (localObjects) "$B_VERSION-local-objects-v1"
                            else if (radialObjects) "$B_VERSION-radial-objects-v2" else B_VERSION,
                            { gray ->
                                try {
                                    generateGeometry(gray)
                                } catch (e: OutOfMemoryError) {
                                    status = "ALLOCATION_FAILURE"
                                    throw e
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: IllegalArgumentException) {
                                    throw e
                                } catch (e: Exception) {
                                    status = "NATIVE_EXCEPTION"
                                    throw e
                                }
                            },
                            select = { gray, raw, checkActive ->
                                processBGeometry(
                                        gray,
                                        raw,
                                        entryBudget,
                                        10000,
                                        objectPolicy = objectPolicy,
                                        maxPairChecks = maxPairChecks,
                                        radialObjects = radialObjects,
                                        localObjects = localObjects,
                                        sharedEdges = sharedEdges,
                                        continuousFaces = continuousFaces,
                                        supportedFaces = supportedFaces,
                                        checkActive = checkActive,
                                    )
                                    .also { lastRun = it }
                                    .representatives
                            },
                            observeInput = { gray, width, height, roi, sx, sy ->
                                val bytes = ByteArray(gray.rows() * gray.cols())
                                gray.get(0, 0, bytes)
                                lastInput = BInput(bSha(bytes), width, height, roi, sx, sy)
                            },
                            observeRepresentatives = { inside ->
                                lastRun = lastRun!!.copy(representatives = inside)
                            },
                        )
                    status = "SUCCESS"
                    result
                } catch (e: CancellationException) {
                    status = "CANCELLED"
                    throw e
                } catch (e: DetectionFailure) {
                    if (status == "STARTED") status = e.reason.name
                    throw e
                }
            }
        }
}

internal fun bSha(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
