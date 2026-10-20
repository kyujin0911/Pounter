package io.github.kyujin0911.pounter.data.detector

import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App trial policy. Field acceptance is independent of runtime availability. */
internal class SupportedFacesPipeDetector(private val photos: PhotoStore) : PipeDetector {
    private val mutex = Mutex()

    override suspend fun detect(task: CountTask): Detection = detect(task) {}

    override suspend fun detect(task: CountTask, onStage: (AnalysisStage) -> Unit): Detection =
        withContext(Dispatchers.Default) {
            onStage(AnalysisStage.WAITING)
            mutex.withLock {
                ensureActive()
                onStage(AnalysisStage.PROCESSING)
                analyzeEdgeDrawing(
                    photos,
                    task,
                    "$B_VERSION-supported-faces-v1",
                    generateGeometry = { EdgeDrawingNative.generate(it) },
                    select = ::selectSupportedFacesGeometry,
                )
            }
        }
}

internal fun selectSupportedFacesGeometry(
    gray: org.opencv.core.Mat,
    raw: DoubleArray,
    checkActive: () -> Unit,
): List<BRepresentative> {
    val base = processBBaseGeometry(gray, raw, entryBudget = 0, checkActive = checkActive)
    val local =
        selectBLocalObjects(gray, base.representatives, entryBudget = 0, checkActive = checkActive)
    val shared = selectBSharedEdges(gray, local.representatives, checkActive = checkActive)
    return selectBSupportedFaces(gray, shared, checkActive = checkActive)
}
