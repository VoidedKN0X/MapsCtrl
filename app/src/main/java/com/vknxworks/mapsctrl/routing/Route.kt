package com.vknxworks.mapsctrl.routing

import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon

/** How you travel; each one is a BRouter profile bundled with the app. */
enum class TravelMode(val profile: String) {
    CAR("car-vario"),
    BIKE("trekking"),
    FOOT("hiking-mountain"),
}

/** What a turn instruction tells you to do (BRouter's voice hint commands). */
enum class TurnKind {
    STRAIGHT, LEFT, SLIGHT_LEFT, SHARP_LEFT, RIGHT, SLIGHT_RIGHT, SHARP_RIGHT,
    KEEP_LEFT, KEEP_RIGHT, U_TURN_LEFT, U_TURN_RIGHT, U_TURN, OFF_ROUTE,
    ROUNDABOUT, ROUNDABOUT_LEFT, BEELINE, EXIT_LEFT, EXIT_RIGHT, ARRIVE;

    companion object {
        /** BRouter's JSON command numbers (see Formatter.getJsonCommandIndex). */
        fun fromJson(cmd: Int): TurnKind = when (cmd) {
            1 -> STRAIGHT
            2 -> LEFT
            3 -> SLIGHT_LEFT
            4 -> SHARP_LEFT
            5 -> RIGHT
            6 -> SLIGHT_RIGHT
            7 -> SHARP_RIGHT
            8 -> KEEP_LEFT
            9 -> KEEP_RIGHT
            10 -> U_TURN_LEFT
            11 -> U_TURN_RIGHT
            12 -> OFF_ROUTE
            13 -> ROUNDABOUT
            14 -> ROUNDABOUT_LEFT
            15 -> U_TURN
            16 -> BEELINE
            17 -> EXIT_LEFT
            18 -> EXIT_RIGHT
            else -> STRAIGHT
        }
    }
}

/**
 * One turn instruction at [index] in the route's points. [exit] is the roundabout exit (0 when not a roundabout).
 * [street] is the name (or number) of the road you turn onto, when MapsCtrl could find it (see [StreetNames]).
 */
data class TurnHint(val index: Int, val kind: TurnKind, val exit: Int, val angle: Int, val street: String? = null)

/** A stretch of road with the same OSM tags, from [startIndex] to [endIndex] in the route's points. */
data class WaySection(val startIndex: Int, val endIndex: Int, val wayTags: Map<String, String>, val nodeTags: Map<String, String>)

data class Route(
    val mode: TravelMode,
    val points: List<LatLon>,
    /** Seconds from the start to each point (same size as [points]), or empty when BRouter gave no times. */
    val times: List<Float>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val hints: List<TurnHint>,
    val sections: List<WaySection>,
) {
    /** Distance from the start to each point, in metres. */
    val cumulative: DoubleArray by lazy {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + Geo.distance(points[i - 1], points[i])
        out
    }

    /** Seconds from the start at a fractional position along the route (falls back to the average speed). */
    fun timeAt(index: Int, fraction: Double = 0.0): Double {
        if (times.size == points.size && index in times.indices) {
            val t0 = times[index].toDouble()
            val t1 = times.getOrElse(index + 1) { times[index] }.toDouble()
            return t0 + (t1 - t0) * fraction
        }
        val total = cumulative.lastOrNull() ?: 0.0
        if (total <= 0.0) return 0.0
        val d = cumulative[index] + if (index + 1 < points.size) (cumulative[index + 1] - cumulative[index]) * fraction else 0.0
        return durationSeconds * d / total
    }

    /** The way section containing point [index], if any. */
    fun sectionAt(index: Int): WaySection? = sections.firstOrNull { index >= it.startIndex && index < it.endIndex }
        ?: sections.lastOrNull { index >= it.startIndex }
}
