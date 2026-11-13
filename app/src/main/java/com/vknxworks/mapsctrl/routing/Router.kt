package com.vknxworks.mapsctrl.routing

import btools.router.FormatJson
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.router.RoutingParamCollector
import com.vknxworks.mapsctrl.geo.LatLon
import java.io.File
import kotlin.math.floor

/** Why a route couldn't be made. */
sealed class RouteError : Exception() {
    /** Routing data for these 5x5 degree tiles isn't on the phone yet. */
    data class MissingSegments(val tiles: Set<String>) : RouteError()
    data class NotFound(val detail: String) : RouteError()
}

/**
 * Offline routing with BRouter. [profileDir] holds the bundled profiles and lookups.dat; [segmentDir] the
 * downloaded .rd5 files. Runs on the calling thread (call it from a background dispatcher).
 */
class Router(private val profileDir: File, private val segmentDir: File) {

    @Volatile private var running: RoutingEngine? = null

    fun cancel() {
        running?.terminate()
    }

    /** Computes a route through [points] (start, optional stops, destination). */
    fun route(points: List<LatLon>, mode: TravelMode, timeoutMs: Long = 60_000): Route {
        require(points.size >= 2)
        val missing = Segments.tilesFor(points).filterNot { File(segmentDir, "$it.rd5").exists() }.toSet()
        if (missing.isNotEmpty()) throw RouteError.MissingSegments(missing)

        val rc = RoutingContext()
        rc.localFunction = File(profileDir, mode.profile + ".brf").absolutePath
        val collector = RoutingParamCollector()
        val waypoints = points.mapIndexed { i, p ->
            OsmNodeNamed().apply {
                name = when (i) { 0 -> "from"; points.lastIndex -> "to"; else -> "via$i" }
                ilon = ((p.lon + 180.0) * 1_000_000 + 0.5).toInt()
                ilat = ((p.lat + 90.0) * 1_000_000 + 0.5).toInt()
            }
        }
        // timode 1 = BRouter's own turn instructions; JSON output.
        collector.setParams(rc, waypoints, mutableMapOf("timode" to "1", "format" to "json"))
        // Keep every OSM tag BRouter knows (maxspeed, speed cameras) in the result, not just the ones the profile uses.
        collector.setProfileParams(rc, mutableMapOf("processUnusedTags" to "true"))

        val engine = RoutingEngine(null, null, segmentDir, waypoints, rc)
        engine.quite = true
        running = engine
        try {
            engine.doRun(timeoutMs)
        } finally {
            running = null
        }
        val error = engine.errorMessage
        if (error != null) {
            val tiles = Regex("""([EW]\d+_[NS]\d+)\.rd5""").findAll(error).map { it.groupValues[1] }.toSet()
            if (tiles.isNotEmpty() && error.contains("not found", ignoreCase = true)) throw RouteError.MissingSegments(tiles)
            throw RouteError.NotFound(error)
        }
        val track = engine.foundTrack ?: throw RouteError.NotFound("no track")
        return RouteParser.parse(FormatJson(rc).format(track), mode)
    }
}

/** BRouter's routing data comes in 5x5 degree tiles named after their south-west corner, like E5_N45. */
object Segments {
    const val BASE_URL = "https://brouter.de/brouter/segments4/"

    fun tileName(lat: Double, lon: Double): String {
        val lon5 = floor(lon / 5.0).toInt() * 5
        val lat5 = floor(lat / 5.0).toInt() * 5
        val ew = if (lon5 < 0) "W${-lon5}" else "E$lon5"
        val ns = if (lat5 < 0) "S${-lat5}" else "N$lat5"
        return "${ew}_$ns"
    }

    /**
     * Tiles a route between [points] will most likely cross: every tile touched by the box around the points,
     * widened by [marginDeg] (roads rarely run straight). BRouter names any other tile it needs when routing.
     */
    fun tilesFor(points: List<LatLon>, marginDeg: Double = 0.05): Set<String> {
        val minLat = points.minOf { it.lat } - marginDeg
        val maxLat = points.maxOf { it.lat } + marginDeg
        val minLon = points.minOf { it.lon } - marginDeg
        val maxLon = points.maxOf { it.lon } + marginDeg
        val out = linkedSetOf<String>()
        var lat = floor(minLat / 5.0) * 5.0
        while (lat <= maxLat) {
            var lon = floor(minLon / 5.0) * 5.0
            while (lon <= maxLon) {
                out += tileName(lat.coerceIn(-90.0, 89.999) + 0.001, lon.coerceIn(-180.0, 179.999) + 0.001)
                lon += 5.0
            }
            lat += 5.0
        }
        return out
    }
}
