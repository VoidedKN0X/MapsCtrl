package com.vknxworks.mapsctrl.nav

import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.routing.Route

/** Where the stops of a route are along it. Pure logic, unit-tested. */
object Stops {
    /**
     * The distance from the route's start to each stop, in metres: the closest point of the route to each stop,
     * searching on from the previous stop (the route passes the stops in order, so a route that comes back past
     * an earlier stop isn't mistaken for it).
     */
    fun alongs(route: Route, stops: List<LatLon>): List<Double> {
        val pts = route.points
        if (pts.size < 2) return stops.map { 0.0 }
        val cum = route.cumulative
        var from = 0
        return stops.map { stop ->
            var best = Double.MAX_VALUE
            var bestI = from
            var bestF = 0.0
            for (i in from until pts.size - 1) {
                val p = Geo.project(stop, pts[i], pts[i + 1])
                if (p.distance < best) {
                    best = p.distance
                    bestI = i
                    bestF = p.fraction
                }
            }
            from = bestI
            cum[bestI] + (cum[bestI + 1] - cum[bestI]) * bestF
        }
    }

    /** The stops not passed yet at [along] metres from the start ([alongs] from [alongs]); [margin] metres early counts as reached. */
    fun remaining(alongs: List<Double>, along: Double, margin: Double = 30.0): List<Int> =
        alongs.indices.filter { alongs[it] > along + margin }
}
