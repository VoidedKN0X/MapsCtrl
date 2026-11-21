package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.nav.OverSpeed
import com.vknxworks.mapsctrl.nav.TripStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverSpeedTest {
    @Test
    fun warnsOnceAfterThreeFixesOver() {
        val o = OverSpeed(marginKmh = 5)
        var t = 0L
        fun feed(speed: Float, limit: Int? = 50) = o.update(speed, limit, moped = false, nowMs = t).also { t += 1000 }
        assertFalse(feed(60f))
        assertFalse(feed(60f))
        assertTrue(feed(60f))
        assertTrue(o.over)
        // Still too fast: no second warning.
        repeat(40) { assertFalse(feed(60f)) }
        // Back under the limit for a while, then too fast again: a new warning.
        repeat(5) { feed(45f) }
        assertFalse(o.over)
        feed(60f); feed(60f)
        assertTrue(feed(60f))
    }

    @Test
    fun marginAndUnknownLimit() {
        val o = OverSpeed(marginKmh = 5)
        repeat(5) { assertFalse(o.update(54f, 50, false, it * 1000L)) }
        repeat(5) { assertFalse(o.update(120f, null, false, it * 1000L)) }
        assertFalse(o.over)
    }

    @Test
    fun mopedIsCappedAt25() {
        assertEquals(25, OverSpeed.effectiveLimit(50, moped = true))
        assertEquals(25, OverSpeed.effectiveLimit(null, moped = true))
        assertEquals(20, OverSpeed.effectiveLimit(20, moped = true))
        assertNull(OverSpeed.effectiveLimit(null, moped = false))
        val o = OverSpeed(marginKmh = 3)
        o.update(30f, 50, true, 0); o.update(30f, 50, true, 1000)
        assertTrue(o.update(30f, 50, true, 2000))
    }

    @Test
    fun tripStatsSkipJitterAndCountMovingTime() {
        var s = TripStats(startedAt = 0)
        s = s.add(LatLon(50.0, 3.0), 5f, 0f, 0)
        // Standing still: 1 m wobble is ignored.
        s = s.add(LatLon(50.00001, 3.0), 5f, 0f, 10_000)
        assertEquals(0.0, s.distance, 0.01)
        // ~111 m north in 10 s.
        s = s.add(LatLon(50.001, 3.0), 5f, 11f, 20_000)
        assertEquals(111.0, s.distance, 2.0)
        assertEquals(20_000L, s.movingMs)
        assertEquals(11f, s.maxSpeed)
        // An inaccurate fix doesn't count.
        s = s.add(LatLon(50.01, 3.0), 80f, 11f, 30_000)
        assertEquals(111.0, s.distance, 2.0)
        assertEquals(30_000L, s.elapsedMs)
    }
}
