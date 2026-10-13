package io.github.kyujin0911.pounter

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kyujin0911.pounter.core.model.*
import io.github.kyujin0911.pounter.data.local.database.TaskCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisMetadataInstrumentedTest {
    @Test
    fun metadataRoundTripsWithoutChangingMarkerIds() {
        val marker = Marker("original-id", .3f, .4f, .02f)
        val task =
            CountTask(
                "t",
                "t.jpg",
                100,
                80,
                1,
                markers = listOf(marker),
                automatic = listOf(marker),
                analysisMetadata = AnalysisMetadata("synthetic-v1"),
            )
        assertEquals(task, TaskCodec.decode(TaskCodec.encode(task)))
        val legacy =
            JSONObject(TaskCodec.encode(task)).apply { remove("analysisMetadata") }.toString()
        assertNull(TaskCodec.decode(legacy).analysisMetadata)
        val explicitNull = JSONObject(legacy).put("analysisMetadata", JSONObject.NULL).toString()
        assertNull(TaskCodec.decode(explicitNull).analysisMetadata)
    }
}
