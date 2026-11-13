package com.vknxworks.mapsctrl.nav

import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.routing.Maxspeed
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.routing.SpeedLimit
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.TurnHint
import com.vknxworks.mapsctrl.routing.TurnKind

/** Where you are on the route right now. */
data class Progress(
    val snapped: LatLon,
    val index: Int,
    val distanceFromRoute: Double,
    val distanceLeft: Double,
    val timeLeftSeconds: Double,
    val nextHint: TurnHint?,
    val distanceToNext: Double,
    /** The turn after the next one and the gap between them, for "then ..." */
    val followingHint: TurnHint?,
    val gapToFollowing: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
    val speedLimit: SpeedLimit?,
    /** Metres to the next speed camera on the route, when one is close. */
    val cameraAhead: Double?,
)

/** Something the voice should say now. */
data class Announcement(val hint: TurnHint, val distance: Double, val following: TurnHint?, val gap: Double)

/**
 * Follows your position along a route: snaps it to the line, works out the next turn and the distance and time
 * left, the speed limit of the road you're on, speed cameras ahead, when you've left the route and when to speak.
 * Pure logic, no Android: it's unit-tested.
 */
class NavigationTracker(
    val route: Route,
    /** Speed cameras from the POI files, besides the ones in the routing data. */
    extraCameras: List<LatLon> = emptyList(),
) {
    private val points = route.points
    private val cum = route.cumulative
    private val total = cum.lastOrNull() ?: 0.0

    /** Turn hints with an "arrive" at the end. */
    val hints: List<TurnHint> = route.hints.filter { it.kind != TurnKind.BEELINE || it.index > 0 } +
        TurnHint(points.lastIndex.coerceAtLeast(0), TurnKind.ARRIVE, 0, 0)

    /** Distances along the route of every speed camera on or right next to it. */
    val cameras: List<Double> = buildList {
        route.sections.filter { it.nodeTags["highway"] == "speed_camera" }.forEach { add(cum[it.endIndex.coerceIn(0, cum.lastIndex)]) }
        for (c in extraCameras) {
            var best = Double.MAX_VALUE
            var along = 0.0
            for (i in 0 until points.size - 1) {
                val p = Geo.project(c, points[i], points[i + 1])
                if (p.distance < best) {
                    best = p.distance
                    along = cum[i] + (cum[i + 1] - cum[i]) * p.fraction
                }
            }
            if (best < 25.0) add(along)
        }
    }.distinct().sorted()

    private var lastIndex = 0
    private var offCount = 0
    private val spoken = HashMap<Int, Int>() // hint index -> the closest stage already said

    private val offRouteLimit = when (route.mode) { TravelMode.CAR -> 45.0; TravelMode.BIKE -> 35.0; TravelMode.FOOT -> 30.0 }

    /** Distance thresholds before a turn: far, near, now. */
    private val stages: DoubleArray = when (route.mode) {
        TravelMode.CAR -> doubleArrayOf(1500.0, 400.0, 70.0)
        TravelMode.BIKE -> doubleArrayOf(400.0, 120.0, 30.0)
        TravelMode.FOOT -> doubleArrayOf(200.0, 60.0, 15.0)
    }

    fun update(pos: LatLon, accuracy: Float = 10f): Progress {
        if (points.size < 2) {
            return Progress(pos, 0, 0.0, 0.0, 0.0, null, 0.0, null, 0.0, false, true, null, null)
        }
        // Look near where we were first; only search the whole route when that fails (after a long tunnel, say).
        var (idx, proj) = nearest(pos, (lastIndex - 3).coerceAtLeast(0), (lastIndex + 80).coerceAtMost(points.size - 2))
        if (proj.distance > offRouteLimit) {
            val (i2, p2) = nearest(pos, 0, points.size - 2)
            if (p2.distance < proj.distance) { idx = i2; proj = p2 }
        }
        lastIndex = idx
        val along = cum[idx] + (cum[idx + 1] - cum[idx]) * proj.fraction
        val distanceLeft = (total - along).coerceAtLeast(0.0)
        val timeLeft = (route.timeAt(points.lastIndex) - route.timeAt(idx, proj.fraction)).coerceAtLeast(0.0)

        val tolerance = offRouteLimit + accuracy.coerceIn(0f, 50f)
        offCount = if (proj.distance > tolerance) offCount + 1 else 0
        val offRoute = offCount >= 3

        val nextPos = hints.indexOfFirst { cum[it.index] > along + 1.0 || it.kind == TurnKind.ARRIVE }
        val next = hints.getOrNull(nextPos)
        val following = hints.getOrNull(nextPos + 1)
        val toNext = next?.let { cum[it.index] - along } ?: distanceLeft
        val gap = if (next != null && following != null) cum[following.index] - cum[next.index] else Double.MAX_VALUE

        val limit = route.sectionAt(idx)?.wayTags?.let { Maxspeed.forWay(it) }
        val camera = cameras.firstOrNull { it > along - 5.0 }?.let { it - along }?.takeIf { it < cameraWarnDistance() }

        return Progress(
            snapped = proj.point,
            index = idx,
            distanceFromRoute = proj.distance,
            distanceLeft = distanceLeft,
            timeLeftSeconds = timeLeft,
            nextHint = next,
            distanceToNext = toNext.coerceAtLeast(0.0),
            followingHint = following,
            gapToFollowing = gap,
            offRoute = offRoute,
            arrived = distanceLeft < 20.0,
            speedLimit = limit,
            cameraAhead = camera?.coerceAtLeast(0.0),
        )
    }

    private fun cameraWarnDistance(): Double = when (route.mode) { TravelMode.CAR -> 500.0; else -> 150.0 }

    /**
     * What to say for this position, if anything: each turn is announced at most once per stage (far, near,
     * now), and a stage that's already been passed is skipped.
     */
    fun announcement(p: Progress): Announcement? {
        val hint = p.nextHint ?: return null
        if (p.offRoute) return null
        val stage = when {
            p.distanceToNext <= stages[2] -> 2
            p.distanceToNext <= stages[1] -> 1
            p.distanceToNext <= stages[0] -> 0
            else -> return null
        }
        // A "far" warning only makes sense when there's still a fair way to go.
        if (stage == 0 && p.distanceToNext < stages[1] * 1.5) return null
        val said = spoken[hint.index] ?: -1
        if (stage <= said) return null
        spoken[hint.index] = stage
        if (hint.kind == TurnKind.STRAIGHT && stage < 2) return null
        return Announcement(hint, p.distanceToNext, p.followingHint, p.gapToFollowing)
    }

    /** After a reroute or a manual restart: forget what's been said. */
    fun resetSpoken() = spoken.clear()

    private fun nearest(p: LatLon, from: Int, to: Int): Pair<Int, Geo.Projection> {
        var bestI = from
        var best: Geo.Projection? = null
        for (i in from..to) {
            val pr = Geo.project(p, points[i], points[i + 1])
            if (best == null || pr.distance < best.distance) { best = pr; bestI = i }
        }
        return bestI to best!!
    }
}
