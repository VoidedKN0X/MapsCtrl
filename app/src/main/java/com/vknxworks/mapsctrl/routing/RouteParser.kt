package com.vknxworks.mapsctrl.routing

import com.vknxworks.mapsctrl.geo.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads BRouter's GeoJSON result (FormatJson): the line, per-point times, voice hints
 * ([index, command, exit, distance, angle]) and the "messages" table, whose rows describe each stretch of
 * road with its OSM tags and end at the stretch's last point (lon/lat in micro-degrees).
 */
object RouteParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(geoJson: String, mode: TravelMode): Route {
        val root = json.parseToJsonElement(geoJson).jsonObject
        val feature = root["features"]!!.jsonArray.first { f ->
            f.jsonObject["geometry"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull == "LineString"
        }.jsonObject
        val props = feature["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val coords = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val points = coords.map { c ->
            val a = c.jsonArray
            LatLon(a[1].jsonPrimitive.doubleOrNull ?: 0.0, a[0].jsonPrimitive.doubleOrNull ?: 0.0)
        }
        val times = (props["times"] as? JsonArray)?.map { it.jsonPrimitive.doubleOrNull?.toFloat() ?: 0f }.orEmpty()

        val hints = (props["voicehints"] as? JsonArray)?.mapNotNull { h ->
            val a = h as? JsonArray ?: return@mapNotNull null
            val index = a.getOrNull(0)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            TurnHint(
                index = index.coerceIn(0, (points.size - 1).coerceAtLeast(0)),
                kind = TurnKind.fromJson(a.getOrNull(1)?.jsonPrimitive?.intOrNull ?: 1),
                exit = a.getOrNull(2)?.jsonPrimitive?.intOrNull ?: 0,
                angle = a.getOrNull(4)?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
            )
        }.orEmpty().sortedBy { it.index }

        val sections = parseMessages(props["messages"] as? JsonArray, points)

        return Route(
            mode = mode,
            points = points,
            times = if (times.size == points.size) times else emptyList(),
            distanceMeters = props.number("track-length") ?: 0.0,
            durationSeconds = props.number("total-time") ?: (times.lastOrNull()?.toDouble() ?: 0.0),
            hints = hints,
            sections = sections,
        )
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun parseMessages(messages: JsonArray?, points: List<LatLon>): List<WaySection> {
        if (messages == null || messages.isEmpty() || points.isEmpty()) return emptyList()
        val header = messages[0].jsonArray.map { it.jsonPrimitive.content }
        val iLon = header.indexOf("Longitude")
        val iLat = header.indexOf("Latitude")
        val iWay = header.indexOf("WayTags")
        val iNode = header.indexOf("NodeTags")
        if (iLon < 0 || iLat < 0 || iWay < 0) return emptyList()
        val out = ArrayList<WaySection>()
        var start = 0
        var cursor = 0
        for (row in messages.drop(1)) {
            val cells = (row as? JsonArray)?.map { it.jsonPrimitive.content } ?: continue
            val lon = cells.getOrNull(iLon)?.toLongOrNull() ?: continue
            val lat = cells.getOrNull(iLat)?.toLongOrNull() ?: continue
            // Find the point this stretch ends at, searching forward from the previous one.
            var end = -1
            var j = cursor
            while (j < points.size) {
                val p = points[j]
                if (Math.round(p.lon * 1e6) == lon && Math.round(p.lat * 1e6) == lat) { end = j; break }
                j++
            }
            if (end < 0) continue
            out += WaySection(start, end, tags(cells.getOrNull(iWay)), if (iNode >= 0) tags(cells.getOrNull(iNode)) else emptyMap())
            start = end
            cursor = end
        }
        return out
    }

    /** "highway=primary maxspeed=50" -> map. BRouter writes spaces inside values as "_" already. */
    fun tags(s: String?): Map<String, String> {
        if (s.isNullOrBlank()) return emptyMap()
        return s.trim().split(' ').mapNotNull { kv ->
            val i = kv.indexOf('=')
            if (i <= 0) null else kv.substring(0, i) to kv.substring(i + 1)
        }.toMap()
    }
}
