package com.vknxworks.mapsctrl.nav

/**
 * Decides when to warn that you're going faster than allowed. Pure logic, unit-tested.
 *
 * The limit is the road's speed limit; on a moped it's never more than [MOPED_KMH] (a class A moped may not go
 * faster anywhere). You're over it when your speed is more than [marginKmh] above it for [needed] position
 * fixes in a row (one GPS jump doesn't count). After a warning the next one comes only after you've been back
 * under the limit for a while and at least [repeatMs] has passed.
 */
class OverSpeed(private val marginKmh: Int, private val needed: Int = 3, private val repeatMs: Long = 30_000) {
    private var overCount = 0
    private var underCount = 0
    private var armed = true
    private var lastWarn = Long.MIN_VALUE / 2

    /** True while you're over the limit (for the screen). */
    var over: Boolean = false
        private set

    /**
     * Feed one fix. [speedKmh] is your speed, [limitKmh] the road's limit (null when unknown), [moped] caps
     * the limit at 25 km/h. Returns true when a warning should be given now.
     */
    fun update(speedKmh: Float?, limitKmh: Int?, moped: Boolean, nowMs: Long): Boolean {
        val limit = effectiveLimit(limitKmh, moped)
        if (speedKmh == null || limit == null) {
            overCount = 0
            over = false
            return false
        }
        if (speedKmh > limit + marginKmh) {
            overCount++
            underCount = 0
        } else {
            overCount = 0
            underCount++
            // Five fixes (about five seconds) under the limit before the next warning can come.
            if (underCount >= 5) armed = true
        }
        over = overCount >= needed
        if (over && armed && nowMs - lastWarn >= repeatMs) {
            armed = false
            lastWarn = nowMs
            return true
        }
        return false
    }

    companion object {
        const val MOPED_KMH = 25

        fun effectiveLimit(limitKmh: Int?, moped: Boolean): Int? =
            if (moped) minOf(limitKmh ?: MOPED_KMH, MOPED_KMH) else limitKmh
    }
}
