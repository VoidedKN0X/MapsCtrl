package com.vknxworks.mapsctrl.geo

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the earth in WGS84 degrees. */
@Serializable
data class LatLon(val lat: Double, val lon: Double) {
    fun isValid(): Boolean = lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN()

    /** "50.85045, 4.34878": the form MapsCtrl shows and copies. */
    fun format(decimals: Int = 5): String = "%.${decimals}f, %.${decimals}f".format(java.util.Locale.ROOT, lat, lon)
}

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun rad(d: Double) = d * PI / 180.0

    /** Great-circle distance in metres. */
    fun distance(a: LatLon, b: LatLon): Double = distance(a.lat, a.lon, b.lat, b.lon)

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLon = rad(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing from [a] to [b], 0..360 degrees, 0 = north. */
    fun bearing(a: LatLon, b: LatLon): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Where [p] falls on the segment [a]-[b]: fraction 0..1 along it and the distance to it in metres. */
    data class Projection(val fraction: Double, val distance: Double, val point: LatLon)

    /** Projects on a local flat approximation, which is exact enough for the few hundred metres between route points. */
    fun project(p: LatLon, a: LatLon, b: LatLon): Projection {
        val k = cos(rad(p.lat))
        val ax = a.lon * k
        val ay = a.lat
        val bx = b.lon * k
        val by = b.lat
        val px = p.lon * k
        val py = p.lat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val q = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        return Projection(t, distance(p, q), q)
    }

    /** Smallest difference between two bearings, 0..180. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = ((a - b) % 360.0 + 360.0) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
