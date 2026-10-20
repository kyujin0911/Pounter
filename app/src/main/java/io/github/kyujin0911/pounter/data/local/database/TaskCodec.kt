package io.github.kyujin0911.pounter.data.local.database

import io.github.kyujin0911.pounter.core.model.AnalysisMetadata
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.core.model.Region
import org.json.JSONArray
import org.json.JSONObject

internal object TaskCodec {
    private fun markers(items: List<Marker>) =
        JSONArray().apply {
            items.forEach { marker ->
                put(
                    JSONObject()
                        .put("id", marker.id)
                        .put("x", marker.x)
                        .put("y", marker.y)
                        .put("r", marker.radius)
                        .put("manual", marker.manual)
                )
            }
        }

    private fun readMarkers(items: JSONArray): List<Marker> =
        (0 until items.length()).map { markerIndex ->
            val markerJson = items.getJSONObject(markerIndex)
            Marker(
                markerJson.getString("id"),
                markerJson.getDouble("x").toFloat(),
                markerJson.getDouble("y").toFloat(),
                markerJson.getDouble("r").toFloat(),
                markerJson.getBoolean("manual"),
            )
        }

    fun encode(task: CountTask) =
        JSONObject()
            .apply {
                put("id", task.id)
                put("photo", task.photoName)
                put("width", task.width)
                put("height", task.height)
                put("created", task.createdAt)
                put("updated", task.updatedAt)
                put("revision", task.revision)
                put(
                    "region",
                    JSONArray(
                        listOf(
                            task.region.left,
                            task.region.top,
                            task.region.right,
                            task.region.bottom,
                        )
                    ),
                )
                put("markers", markers(task.markers))
                put("automatic", markers(task.automatic))
                put("undo", task.undo?.let(::markers) ?: JSONObject.NULL)
                put("analyzed", task.analyzed)
                put("millis", task.analysisMillis ?: JSONObject.NULL)
                put("confirmed", task.confirmedAt ?: JSONObject.NULL)
                put("deleted", task.deleted)
                put(
                    "analysisMetadata",
                    task.analysisMetadata?.let {
                        JSONObject().put("detectorVersion", it.detectorVersion)
                    } ?: JSONObject.NULL,
                )
            }
            .toString()

    fun decode(payload: String): CountTask {
        val taskJson = JSONObject(payload)
        val regionJson = taskJson.getJSONArray("region")
        return CountTask(
            taskJson.getString("id"),
            taskJson.getString("photo"),
            taskJson.getInt("width"),
            taskJson.getInt("height"),
            taskJson.getLong("created"),
            taskJson.getLong("updated"),
            taskJson.getLong("revision"),
            Region(
                regionJson.getDouble(0).toFloat(),
                regionJson.getDouble(1).toFloat(),
                regionJson.getDouble(2).toFloat(),
                regionJson.getDouble(3).toFloat(),
            ),
            readMarkers(taskJson.getJSONArray("markers")),
            readMarkers(taskJson.getJSONArray("automatic")),
            if (taskJson.isNull("undo")) null else readMarkers(taskJson.getJSONArray("undo")),
            taskJson.getBoolean("analyzed"),
            if (taskJson.isNull("millis")) null else taskJson.getLong("millis"),
            if (taskJson.isNull("confirmed")) null else taskJson.getLong("confirmed"),
            taskJson.getBoolean("deleted"),
            taskJson.optJSONObject("analysisMetadata")?.let {
                AnalysisMetadata(it.getString("detectorVersion"))
            },
        )
    }
}
