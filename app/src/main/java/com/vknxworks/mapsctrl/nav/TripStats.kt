package com.vknxworks.mapsctrl.nav

import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon

/** Statistics of a trip so far: distance ridden, time, moving time, average and top speed. */
data class TripStats(
    val startedAt: Long,
    val distance: Double = 0.0,
    val elapsedMs: Long = 0,
    val movingMs: Long = 0,
    val maxSpeed: Float = 0f,
    val last: LatLon? = null,
    val lastTime: Long = 0,
) {
    /** Average speed while moving, in m/s (0 before you've moved). */
    val movingAverage: Double get() = if (movingMs > 0) distance / (movingMs / 1000.0) else 0.0

    /**
     * Adds a position fix. Fixes less accurate than 40 m are skipped, and so are steps under 3 m (standing
     * still makes the position wander). Time between fixes counts as moving when you went faster than 1 km/h.
     */
    fun add(point: LatLon, accuracy: Float, speed: Float?, time: Long): TripStats {
        val elapsed = (time - startedAt).coerceAtLeast(0)
        if (accuracy > 40f) return copy(elapsedMs = elapsed)
        val prev = last
        if (prev == null) return copy(last = point, lastTime = time, elapsedMs = elapsed)
        val step = Geo.distance(prev, point)
        if (step < 3.0) return copy(elapsedMs = elapsed)
        val dt = (time - lastTime).coerceAtLeast(0)
        val moving = dt > 0 && step / (dt / 1000.0) > 0.28
        return copy(
            distance = distance + step,
            elapsedMs = elapsed,
            movingMs = movingMs + if (moving && dt < 60_000) dt else 0,
            maxSpeed = maxOf(maxSpeed, speed ?: 0f),
            last = point,
            lastTime = time,
        )
    }
}
