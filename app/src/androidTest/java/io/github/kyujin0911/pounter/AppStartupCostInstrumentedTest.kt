package io.github.kyujin0911.pounter

import android.content.ContextWrapper
import android.net.Uri
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.data.detector.SupportedFacesPipeDetector
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader

/** Invoke each method in a fresh instrumentation process, with no concurrent build/comparison. */
class AppStartupCostInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun write(name: String, value: JSONObject) {
        File(context.cacheDir, "app-integration-output")
            .apply { mkdirs() }
            .resolve(name)
            .writeText(value.toString(2))
    }

    private fun memory() =
        JSONObject()
            .put("pssKb", Debug.getPss())
            .put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize())

    @Test
    fun coldLibraryInitialization() {
        val before = memory()
        val started = System.nanoTime()
        assertTrue(OpenCVLoader.initLocal())
        val openCvMillis = (System.nanoTime() - started) / 1_000_000.0
        val nativeStarted = System.nanoTime()
        System.loadLibrary("pounter_ed")
        val nativeMillis = (System.nanoTime() - nativeStarted) / 1_000_000.0
        write(
            "initialization.json",
            JSONObject()
                .put("openCvMillis", openCvMillis)
                .put("jniLoadMillis", nativeMillis)
                .put("before", before)
                .put("after", memory())
                .put("peakMeasured", false),
        )
    }

    @Test
    fun coldFirstAndRepeatedAppAnalysisIncludingImagePreparation() = runBlocking {
        val root =
            File(
                requireNotNull(
                    InstrumentationRegistry.getArguments().getString("radialEvaluationRoot")
                )
            )
        val row = JSONArray(File(root, "rows.json").readText()).getJSONObject(2)
        val source = File(root, row.getJSONObject("sample").getString("photo"))
        val fixture = File(context.cacheDir, "app-cost-${UUID.randomUUID()}").apply { mkdirs() }
        val photos =
            PhotoStore(
                object : ContextWrapper(context) {
                    override fun getFilesDir() = fixture
                }
            )
        val detector = SupportedFacesPipeDetector(photos)
        try {
            val importStart = System.nanoTime()
            val photo = photos.import(Uri.fromFile(source))
            val importMillis = (System.nanoTime() - importStart) / 1_000_000.0
            val task = CountTask("cost", photo.first, photo.second, photo.third, 0)
            val samples = JSONArray()
            repeat(4) {
                val before = memory()
                val started = System.nanoTime()
                val result = detector.detect(task)
                val total = (System.nanoTime() - started) / 1_000_000.0
                assertEquals(58, result.markers.size)
                samples.put(
                    JSONObject()
                        .put("totalMillis", total)
                        .put("reportedMillis", result.millis)
                        .put("before", before)
                        .put("after", memory())
                )
            }
            write(
                "cold-analysis.json",
                JSONObject()
                    .put("sample", "cap-55-full")
                    .put("importMillis", importMillis)
                    .put("samples", samples)
                    .put("peakMeasured", false)
                    .put("process", "fresh instrumentation target; excludes Activity startup")
                    .put(
                        "nativeMaps",
                        File("/proc/self/maps").readLines().filter {
                            it.contains("libpounter_ed") || it.contains("libopencv_java4")
                        },
                    ),
            )
        } finally {
            fixture.deleteRecursively()
        }
    }
}
