package com.vknxworks.mapsctrl.transit

import com.vknxworks.mapsctrl.geo.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale

/**
 * The MOTIS 2 routing API that Transitous runs (https://transitous.org/api/): building the plan request and reading
 * its answer. Pure logic, unit-tested. The answer comes from the network, so it's read defensively: anything
 * missing or malformed is skipped, sizes are capped, and a bad answer never throws past [parsePlan].
 */
object Motis {
    const val BASE_URL = "https://api.transitous.org"

    /**
     * The plan endpoint, at the API version current in MOTIS's OpenAPI (2026-11). v1 still answers, but encodes
     * lines with 7 decimals, which overflows east or west of 107° (the Americas, East Asia, Australia).
     */
    const val PLAN_PATH = "/api/v6/plan"

    const val MAX_ITINERARIES = 12
    const val MAX_LEGS = 40
    const val MAX_POINTS_PER_LEG = 20_000
    const val MAX_INTERMEDIATE = 500

    /** Transitous asks every app to name itself, its version and a contact in the User-Agent. */
    fun userAgent(version: String) = "MapsCtrl/$version (+https://github.com/VoidedKN0X/MapsCtrl)"

    /**
     * The request URL. Only the start, the destination and the time go to the server: coordinates rounded to
     * 5 decimals (about 1 m), the time in UTC.
     */
    fun planUrl(from: LatLon, to: LatLon, time: TransitTime, numItineraries: Int = 5): String {
        require(from.isValid() && to.isValid())
        val params = buildList {
            add("fromPlace" to coord(from))
            add("toPlace" to coord(to))
            when (time) {
                TransitTime.Now -> Unit
                is TransitTime.DepartAt -> add("time" to time.time.truncate().toString())
                is TransitTime.ArriveBy -> { add("time" to time.time.truncate().toString()); add("arriveBy" to "true") }
            }
            add("numItineraries" to numItineraries.coerceIn(1, 10).toString())
        }
        return BASE_URL + PLAN_PATH + "?" + params.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    private fun Instant.truncate(): Instant = Instant.ofEpochSecond(epochSecond)

    private fun coord(p: LatLon) = String.format(Locale.ROOT, "%.5f,%.5f", p.lat, p.lon)

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Reads a plan answer. Throws [IllegalArgumentException] only when it isn't a plan answer at all. */
    fun parsePlan(body: String): TransitPlan {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw IllegalArgumentException("Not a plan answer")
        if (root["itineraries"] !is JsonArray && root["direct"] !is JsonArray) throw IllegalArgumentException("Not a plan answer")
        return TransitPlan(
            itineraries = itineraries(root["itineraries"]),
            direct = itineraries(root["direct"]),
        )
    }

    private fun itineraries(e: JsonElement?): List<Itinerary> =
        (e as? JsonArray).orEmpty().asSequence().take(MAX_ITINERARIES).mapNotNull { runCatching { itinerary(it as? JsonObject) }.getOrNull() }.toList()

    private fun itinerary(o: JsonObject?): Itinerary? {
        o ?: return null
        val legs = (o["legs"] as? JsonArray).orEmpty().asSequence().take(MAX_LEGS).mapNotNull { leg(it as? JsonObject) }.toList()
        if (legs.isEmpty()) return null
        return Itinerary(
            start = o.time("startTime") ?: legs.first().start,
            end = o.time("endTime") ?: legs.last().end,
            transfers = o.int("transfers") ?: (legs.count { it.kind.isTransit && !it.stayOnBoard } - 1).coerceAtLeast(0),
            legs = legs,
        )
    }

    private fun leg(o: JsonObject?): TransitLeg? {
        o ?: return null
        val kind = LegKind.fromMotis(o.str("mode"))
        val from = place(o["from"] as? JsonObject) ?: return null
        val to = place(o["to"] as? JsonObject) ?: return null
        val start = o.time("startTime") ?: return null
        val end = o.time("endTime") ?: return null
        if (end.isBefore(start)) return null
        val geometry = (o["legGeometry"] as? JsonObject)?.let { g ->
            val points = g.str("points") ?: return@let null
            val precision = g.int("precision") ?: 6
            runCatching { decodePolyline(points, precision) }.getOrNull()
        }?.takeIf { it.size >= 2 } ?: listOf(from.point, to.point)
        return TransitLeg(
            kind = kind,
            from = from,
            to = to,
            start = start,
            end = end,
            scheduledStart = o.time("scheduledStartTime"),
            scheduledEnd = o.time("scheduledEndTime"),
            realTime = o.bool("realTime") ?: false,
            // The line as signed at the stop ("C3", "1"); the trip's name ("TER 86018") when there's no line name.
            line = if (kind.isTransit) (o.str("routeShortName")?.clip(40) ?: o.str("displayName")?.clip(40) ?: o.str("tripShortName")?.clip(40)) else null,
            lineLongName = o.str("routeLongName")?.clip(120),
            headsign = o.str("headsign")?.clip(120),
            agency = o.str("agencyName")?.clip(80),
            color = color(o.str("routeColor")),
            textColor = color(o.str("routeTextColor")),
            distanceMeters = o.double("distance")?.takeIf { it >= 0 && it < 1e8 },
            intermediateStops = (o["intermediateStops"] as? JsonArray)?.size?.coerceAtMost(MAX_INTERMEDIATE) ?: 0,
            cancelled = o.bool("cancelled") ?: false,
            stayOnBoard = o.bool("interlineWithPreviousLeg") ?: false,
            geometry = geometry,
        )
    }

    private fun place(o: JsonObject?): TransitStop? {
        o ?: return null
        val lat = o.double("lat") ?: return null
        val lon = o.double("lon") ?: return null
        val p = LatLon(lat, lon)
        if (!p.isValid()) return null
        return TransitStop(
            // MOTIS calls the ends of a journey "START" and "END"; the app shows its own names there.
            name = o.str("name")?.clip(120) ?: "",
            point = p,
            track = o.str("track")?.clip(12),
            scheduledTrack = o.str("scheduledTrack")?.clip(12),
            cancelled = o.bool("cancelled") ?: false,
        )
    }

    /** "0749ff" -> 0xFF0749FF. */
    fun color(hex: String?): Int? {
        val h = hex?.trim()?.removePrefix("#") ?: return null
        if (!h.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return (0xFF000000 or h.toLong(16)).toInt()
    }

    /** Google's encoded polyline format with [precision] decimals (MOTIS sends 6, or 7 for its v1 API). */
    fun decodePolyline(s: String, precision: Int = 6): List<LatLon> {
        require(precision in 1..8)
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < s.length) {
            if (out.size >= MAX_POINTS_PER_LEG) break
            val (dLat, i2) = next(s, i)
            val (dLon, i3) = next(s, i2)
            i = i3
            lat += dLat
            lon += dLon
            val p = LatLon(lat / factor, lon / factor)
            require(p.isValid()) { "Bad polyline" }
            out += p
        }
        return out
    }

    private fun next(s: String, start: Int): Pair<Long, Int> {
        var i = start
        var result = 0L
        var shift = 0
        while (true) {
            require(i < s.length && shift <= 60) { "Bad polyline" }
            val b = s[i++].code - 63
            require(b in 0..63) { "Bad polyline" }
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return (if (result and 1L != 0L) (result shr 1).inv() else result shr 1) to i
    }

    private fun String.clip(n: Int): String? = trim().takeIf { it.isNotEmpty() }?.take(n)

    private fun JsonObject.prim(k: String): JsonPrimitive? = this[k] as? JsonPrimitive
    private fun JsonObject.str(k: String): String? = prim(k)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.int(k: String): Int? = prim(k)?.intOrNull
    private fun JsonObject.double(k: String): Double? = prim(k)?.doubleOrNull?.takeIf { it.isFinite() }
    private fun JsonObject.bool(k: String): Boolean? = prim(k)?.booleanOrNull
    private fun JsonObject.time(k: String): Instant? = str(k)?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
