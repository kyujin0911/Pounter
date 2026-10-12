package io.github.kyujin0911.pounter.data

import io.github.kyujin0911.pounter.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object TaskCodec {
    private fun markers(items: List<Marker>) =
        JSONArray().apply {
            items.forEach { m ->
                put(
                    JSONObject()
                        .put("id", m.id)
                        .put("x", m.x)
                        .put("y", m.y)
                        .put("r", m.radius)
                        .put("manual", m.manual)
                )
            }
        }

    private fun readMarkers(items: JSONArray): List<Marker> =
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            Marker(
                o.getString("id"),
                o.getDouble("x").toFloat(),
                o.getDouble("y").toFloat(),
                o.getDouble("r").toFloat(),
                o.getBoolean("manual"),
            )
        }

    fun encode(t: CountTask) =
        JSONObject()
            .apply {
                put("id", t.id)
                put("photo", t.photoName)
                put("width", t.width)
                put("height", t.height)
                put("created", t.createdAt)
                put("updated", t.updatedAt)
                put("revision", t.revision)
                put(
                    "region",
                    JSONArray(listOf(t.region.left, t.region.top, t.region.right, t.region.bottom)),
                )
                put("markers", markers(t.markers))
                put("automatic", markers(t.automatic))
                put("undo", t.undo?.let(::markers) ?: JSONObject.NULL)
                put("analyzed", t.analyzed)
                put("millis", t.analysisMillis ?: JSONObject.NULL)
                put("confirmed", t.confirmedAt ?: JSONObject.NULL)
                put("deleted", t.deleted)
            }
            .toString()

    fun decode(value: String): CountTask {
        val o = JSONObject(value)
        val r = o.getJSONArray("region")
        return CountTask(
            o.getString("id"),
            o.getString("photo"),
            o.getInt("width"),
            o.getInt("height"),
            o.getLong("created"),
            o.getLong("updated"),
            o.getLong("revision"),
            Region(
                r.getDouble(0).toFloat(),
                r.getDouble(1).toFloat(),
                r.getDouble(2).toFloat(),
                r.getDouble(3).toFloat(),
            ),
            readMarkers(o.getJSONArray("markers")),
            readMarkers(o.getJSONArray("automatic")),
            if (o.isNull("undo")) null else readMarkers(o.getJSONArray("undo")),
            o.getBoolean("analyzed"),
            if (o.isNull("millis")) null else o.getLong("millis"),
            if (o.isNull("confirmed")) null else o.getLong("confirmed"),
            o.getBoolean("deleted"),
        )
    }
}
