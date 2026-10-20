package io.github.kyujin0911.pounter

import androidx.test.platform.app.InstrumentationRegistry
import io.github.kyujin0911.pounter.data.detector.DETECTOR_VALIDATED
import io.github.kyujin0911.pounter.data.detector.PipeDetector
import io.github.kyujin0911.pounter.data.local.database.CountDatabase
import io.github.kyujin0911.pounter.data.local.database.TaskDao
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.data.repository.LocalCountRepository
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.android.ext.android.getKoin

class DiInstrumentedTest {
    @Test
    fun applicationStartsOneStorageGraphWithTrialDetector() {
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as PipeApplication
        val koin = application.getKoin()
        assertSame(koin.get<CountDatabase>(), koin.get<CountDatabase>())
        assertSame(koin.get<TaskDao>(), koin.get<TaskDao>())
        assertSame(koin.get<PhotoStore>(), koin.get<PhotoStore>())
        assertSame(koin.get<CountRepository>(), koin.get<LocalCountRepository>())
        assertSame(koin.get<CountRepository>(), koin.get<CountRepository>())
        assertSame(koin.get<PipeDetector>(), koin.get<PipeDetector>())
        assertTrue(koin.get<PipeDetector>().javaClass.simpleName == "SupportedFacesPipeDetector")
        assertTrue(!DETECTOR_VALIDATED)
    }
}
