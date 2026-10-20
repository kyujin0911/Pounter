package io.github.kyujin0911.pounter.data.detector

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opencv.core.*

/** One owner across evaluation containers. JNI retains no pointers/array/Mat handles. */
internal object EdgeDrawingTestNative {
    private val mutex = Mutex()
    private var loaded = false

    private fun load() {
        if (!loaded) {
            System.loadLibrary("pounter_ed_test")
            loaded = true
        }
    }

    suspend fun generate(
        gray: Mat,
        probe: () -> Unit = {},
        afterNative: () -> Unit = {},
    ): DoubleArray =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                ensureActive()
                load()
                require(gray.type() == CvType.CV_8UC1 && gray.total() <= 4_000_000)
                val pixels = ByteArray(gray.rows() * gray.cols())
                gray.get(0, 0, pixels)
                probe()
                ensureActive()
                val result = detect(pixels, gray.cols(), gray.rows())
                afterNative()
                ensureActive() // Native itself is synchronous and cannot be interrupted by
                // cancellation.
                result
            }
        }

    @JvmStatic external fun detect(pixels: ByteArray, width: Int, height: Int): DoubleArray

    @JvmStatic external fun failAfterAllocation()

    @JvmStatic
    external fun auditOwnership(pixels: ByteArray, width: Int, height: Int, failAt: Int): IntArray
}
