package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.navigation.HomeRoute
import io.github.kyujin0911.pounter.navigation.RecordRoute
import io.github.kyujin0911.pounter.navigation.WorkRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class RouteCompatibilityTest {
    @Test
    fun serializedRouteNamesAndTaskArgumentRemainCompatibleAfterPackageMove() {
        assertEquals(
            "io.github.kyujin0911.pounter.ui.HomeRoute",
            HomeRoute.serializer().descriptor.serialName,
        )
        assertEquals(
            "io.github.kyujin0911.pounter.ui.WorkRoute",
            WorkRoute.serializer().descriptor.serialName,
        )
        assertEquals(
            "io.github.kyujin0911.pounter.ui.RecordRoute",
            RecordRoute.serializer().descriptor.serialName,
        )
        assertEquals("taskId", WorkRoute.serializer().descriptor.getElementName(0))
        assertEquals("taskId", RecordRoute.serializer().descriptor.getElementName(0))
        assertEquals(WorkRoute("task"), Json.decodeFromString<WorkRoute>("""{"taskId":"task"}"""))
    }
}
