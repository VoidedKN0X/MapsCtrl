package com.vknxworks.mapsctrl.routing

import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon

/** A road from the map data with its name and/or number (ref), as a line. */
data class NamedWay(
    val name: String?,
    val ref: String?,
    val highway: String?,
    val points: List<LatLon>,
    val roundabout: Boolean = false,
)

/** Named roads near a point. The app reads them from the downloaded Mapsforge maps; tests use a list. */
fun interface WayNameSource {
    fun waysNear(p: LatLon, radiusM: Double): List<NamedWay>
}

/**
 * Street names for turn instructions ("turn left onto Rue Grimaldi").
 *
 * BRouter's routing data has no names, so for each turn MapsCtrl first looks at the way tags BRouter reports for
 * the stretch after the turn (a name or ref, if the routing data ever carries one), and otherwise finds the named
 * road the route follows a little after the turn point in the map data: the nearest one running in the same
 * direction as the route.
 */
object StreetNames {

    /** How far from the route a road may be to count as the one it follows (map and routing data differ a bit). */
    const val MAX_DISTANCE_M = 18.0

    /** How far a road's direction may differ from the route's there. */
    const val MAX_ANGLE_DEG = 35.0

    /** Where after the turn to look, in metres along the route. */
    private val SAMPLE_OFFSETS = doubleArrayOf(12.0, 30.0)

    private val WITHOUT_STREET = setOf(TurnKind.ARRIVE, TurnKind.BEELINE, TurnKind.OFF_ROUTE, TurnKind.U_TURN, TurnKind.U_TURN_LEFT, TurnKind.U_TURN_RIGHT)

    /** Highways that are not roads you turn onto by name. */
    private val IGNORED_HIGHWAYS = setOf("steps", "proposed", "construction", "platform", "elevator", "corridor", "bus_stop", "raceway")

    fun wantsStreet(kind: TurnKind) = kind !in WITHOUT_STREET

    /** The name from OSM-style tags: name, else ref ("A8"). BRouter writes spaces in values as "_". */
    fun fromTags(tags: Map<String, String>): String? =
        clean(tags["name"]?.replace('_', ' ')) ?: clean(tags["ref"]?.replace('_', ' '))

    /** What to say for a road: its name, else its number. Null for blank or absurdly long values. */
    fun label(name: String?, ref: String?): String? = clean(name) ?: clean(ref)

    private fun clean(s: String?): String? {
        val t = s?.replace(Regex("\\s+"), " ")?.trim() ?: return null
        return t.takeIf { it.isNotEmpty() && it.length <= 80 }
    }

    /** The route with a street on every turn where one was found. [source] is null when no map is on the phone. */
    fun annotate(route: Route, source: WayNameSource?): Route {
        if (route.hints.isEmpty() || route.points.size < 2) return route
        // A broken or unreadable map must never cost the route: a failed lookup just means no name.
        val safe = source?.let { s -> WayNameSource { p, r -> runCatching { s.waysNear(p, r) }.getOrDefault(emptyList()) } }
        val hints = route.hints.mapIndexed { i, h ->
            if (!wantsStreet(h.kind) || h.street != null) return@mapIndexed h
            val next = route.hints.getOrNull(i + 1)?.index ?: route.points.lastIndex
            h.copy(street = streetFor(route, h, next, safe))
        }
        return route.copy(hints = hints)
    }

    /** The street you turn onto at [hint]; [nextIndex] is where the next instruction is. */
    fun streetFor(route: Route, hint: TurnHint, nextIndex: Int, source: WayNameSource?): String? {
        val start = outgoingIndex(route, hint)
        // 1. Tags from the routing data for the stretch after the turn.
        route.sectionAt(start)?.let { s -> if (!isRoundabout(s.wayTags)) fromTags(s.wayTags)?.let { return it } }
        // 2. The map data.
        if (source == null) return null
        val cum = route.cumulative
        val limit = cum[nextIndex.coerceIn(start, route.points.lastIndex)] - 3.0
        val votes = LinkedHashMap<String, Double>()
        for (offset in SAMPLE_OFFSETS) {
            val along = (cum[start] + offset).coerceAtMost(limit)
            if (along < cum[start]) break
            val (p, bearing) = pointAt(route, along) ?: continue
            val best = best(source.waysNear(p, 60.0), p, bearing) ?: continue
            votes[best.first] = (votes[best.first] ?: 0.0) + 1.0 / (1.0 + best.second)
            if (along >= limit) break
        }
        return votes.maxByOrNull { it.value }?.key
    }

    /** For a roundabout, the first point after it; otherwise the turn point. */
    fun outgoingIndex(route: Route, hint: TurnHint): Int {
        if (hint.kind != TurnKind.ROUNDABOUT && hint.kind != TurnKind.ROUNDABOUT_LEFT) return hint.index
        var i = hint.index
        while (i < route.points.lastIndex) {
            val s = route.sectionAt(i) ?: break
            if (!isRoundabout(s.wayTags)) break
            i = s.endIndex.coerceAtLeast(i + 1)
        }
        return i.coerceAtMost(route.points.lastIndex)
    }

    private fun isRoundabout(tags: Map<String, String>) = tags["junction"] == "roundabout" || tags["junction"] == "circular"

    /** The point [along] metres from the start, and the route's bearing there. */
    private fun pointAt(route: Route, along: Double): Pair<LatLon, Double>? {
        val cum = route.cumulative
        val pts = route.points
        var i = 0
        while (i < pts.size - 2 && cum[i + 1] < along) i++
        val a = pts[i]
        val b = pts[i + 1]
        val seg = cum[i + 1] - cum[i]
        if (seg <= 0.01) return null
        val f = ((along - cum[i]) / seg).coerceIn(0.0, 1.0)
        return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f) to Geo.bearing(a, b)
    }

    /** The best road for [p] heading [bearing]: its label and its distance in metres. */
    fun best(ways: List<NamedWay>, p: LatLon, bearing: Double): Pair<String, Double>? {
        var bestLabel: String? = null
        var bestScore = Double.MAX_VALUE
        var bestDist = 0.0
        for (w in ways) {
            if (w.roundabout || w.highway in IGNORED_HIGHWAYS || w.points.size < 2) continue
            val label = label(w.name, w.ref) ?: continue
            for (j in 0 until w.points.size - 1) {
                val a = w.points[j]
                val b = w.points[j + 1]
                if (a == b) continue
                val pr = Geo.project(p, a, b)
                if (pr.distance > MAX_DISTANCE_M) continue
                // Roads may be drawn in either direction.
                val diff = Geo.angleDiff(Geo.bearing(a, b), bearing).let { minOf(it, 180.0 - it) }
                if (diff > MAX_ANGLE_DEG) continue
                val score = pr.distance + diff * 0.25
                if (score < bestScore) {
                    bestScore = score
                    bestLabel = label
                    bestDist = pr.distance
                }
            }
        }
        return bestLabel?.let { it to bestDist }
    }
}
