package com.vknxworks.mapsctrl.geo

/**
 * The elevation profile of a line: height against distance, the climb and descent, the lowest and highest
 * point. Pure logic, unit-tested.
 */
class ElevationProfile(
    /** Metres from the start, one per sample. */
    val distances: DoubleArray,
    /** Height above sea level in metres, one per sample. */
    val heights: FloatArray,
    val ascent: Double,
    val descent: Double,
) {
    val min: Float get() = heights.minOrNull() ?: 0f
    val max: Float get() = heights.maxOrNull() ?: 0f
    val length: Double get() = distances.lastOrNull() ?: 0.0

    /** Climb still ahead after [along] metres from the start (with the same filter as [ascent]). */
    fun ascentAfter(along: Double): Double {
        val i = distances.indexOfFirst { it >= along }
        if (i < 0) return 0.0
        return climb(heights.copyOfRange(i, heights.size)).first
    }

    /** At most [n] samples, for drawing: the highest and lowest of each stretch are kept, so peaks don't vanish. */
    fun downsample(n: Int): ElevationProfile {
        if (heights.size <= n || n < 4) return this
        val buckets = n / 2
        val d = ArrayList<Double>(n)
        val h = ArrayList<Float>(n)
        val per = heights.size.toDouble() / buckets
        for (b in 0 until buckets) {
            val from = (b * per).toInt()
            val to = ((b + 1) * per).toInt().coerceAtMost(heights.size).coerceAtLeast(from + 1)
            var lo = from
            var hi = from
            for (i in from until to) {
                if (heights[i] < heights[lo]) lo = i
                if (heights[i] > heights[hi]) hi = i
            }
            for (i in listOf(lo, hi).distinct().sorted()) { d += distances[i]; h += heights[i] }
        }
        return ElevationProfile(d.toDoubleArray(), h.toFloatArray(), ascent, descent)
    }

    companion object {
        /** Height changes smaller than this (metres) are taken as noise in the data, not as climbing. */
        const val THRESHOLD = 5.0

        /** The profile of [points] with a height for each one, or null when there are no (or not enough) heights. */
        fun of(points: List<LatLon>, heights: List<Float?>): ElevationProfile? {
            if (points.size < 2 || heights.size != points.size) return null
            val d = ArrayList<Double>(points.size)
            val h = ArrayList<Float>(points.size)
            var along = 0.0
            for (i in points.indices) {
                if (i > 0) along += Geo.distance(points[i - 1], points[i])
                val e = heights[i] ?: continue
                d += along
                h += e
            }
            if (h.size < 2) return null
            val hs = h.toFloatArray()
            val (up, down) = climb(hs)
            return ElevationProfile(d.toDoubleArray(), hs, up, down)
        }

        /** Climb and descent with a [THRESHOLD] filter: only changes that add up to it count. */
        fun climb(h: FloatArray): Pair<Double, Double> {
            if (h.isEmpty()) return 0.0 to 0.0
            var ref = h[0].toDouble()
            var up = 0.0
            var down = 0.0
            for (x in h) {
                val v = x.toDouble()
                if (v - ref >= THRESHOLD) { up += v - ref; ref = v } else if (ref - v >= THRESHOLD) { down += ref - v; ref = v }
            }
            return up to down
        }
    }
}
