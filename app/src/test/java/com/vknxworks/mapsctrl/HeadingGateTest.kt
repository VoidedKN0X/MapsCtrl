package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.location.HeadingGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingGateTest {
    /** The rule the map had before the gate existed. */
    private fun old(hasFix: Boolean, speed: Float?, bearing: Float?, compass: Float?): Float? =
        if (!hasFix) null else if ((speed ?: 0f) > 1.5f && bearing != null) bearing else compass

    @Test
    fun sensorFollowsWantedWhenStandingStill() {
        val g = HeadingGate()
        assertFalse(g.sensorNeeded(false, true, 0f, null))
        assertTrue(g.sensorNeeded(true, true, 0f, null))
        assertTrue(g.sensorNeeded(true, true, null, null))
        assertTrue(g.sensorNeeded(true, true, 1f, 90f))
        assertFalse(g.sensorNeeded(false, true, 1f, 90f))
    }

    @Test
    fun sensorGoesOffWhenFastAndComesBackWellBeforeItIsNeeded() {
        val g = HeadingGate()
        assertTrue(g.sensorNeeded(true, true, 0f, 10f))
        assertTrue(g.sensorNeeded(true, true, 5f, 10f))   // still on between the two speeds
        assertTrue(g.sensorNeeded(true, true, 6f, 10f))
        assertFalse(g.sensorNeeded(true, true, 6.1f, 10f))
        assertFalse(g.sensorNeeded(true, true, 14f, 10f))
        assertFalse(g.sensorNeeded(true, true, 5f, 10f))  // still off between the two speeds
        assertFalse(g.sensorNeeded(true, true, 4f, 10f))
        assertTrue(g.sensorNeeded(true, true, 3.9f, 10f))  // back on while the GPS bearing is still used (> 1.5)
        assertTrue(g.sensorNeeded(true, true, 5f, 10f))
        // Fast but no bearing: the compass is the only heading there is.
        assertFalse(g.sensorNeeded(true, true, 20f, 10f))
        assertTrue(g.sensorNeeded(true, true, 20f, null))
    }

    @Test
    fun aPhoneWithoutACompassBehavesAsBefore() {
        val g = HeadingGate()
        val fixes = listOf(0f to null, 10f to 80f, 20f to 85f, 1f to 85f, 0f to null, 3f to 20f, 0f to 20f)
        for ((speed, bearing) in fixes) {
            assertTrue(g.sensorNeeded(true, false, speed, bearing))
            assertEquals(old(true, speed, bearing, null), g.heading(true, true, false, speed, bearing, null))
        }
    }

    @Test
    fun notWantedIsAsBefore() {
        val g = HeadingGate()
        val fixes = listOf(0f to null, 10f to 80f, 1f to 85f, 0f to null, 3f to 20f, 0f to 20f)
        for ((speed, bearing) in fixes) {
            assertFalse(g.sensorNeeded(false, true, speed, bearing))
            assertEquals(old(true, speed, bearing, null), g.heading(true, false, true, speed, bearing, null))
        }
        assertNull(g.heading(false, false, true, 10f, 80f, null))
    }

    @Test
    fun withAWarmCompassTheHeadingIsExactlyTheOldRule() {
        val g = HeadingGate()
        // Slow riding and walking: the sensor never goes off, so the compass always has a reading.
        val fixes = listOf(0f to null, 1f to 30f, 1.5f to 30f, 1.6f to 40f, 3f to 50f, 5.9f to 60f, 2f to 70f, 0.5f to 70f, 0f to null)
        var compass = 200f
        for ((speed, bearing) in fixes) {
            assertTrue(g.sensorNeeded(true, true, speed, bearing))
            compass += 1f
            assertEquals(old(true, speed, bearing, compass), g.heading(true, true, true, speed, bearing, compass))
        }
    }

    @Test
    fun hardBrakingNeverTurnsTheMapNorth() {
        val g = HeadingGate()
        // Riding fast: sensor off, GPS bearing.
        assertFalse(g.sensorNeeded(true, true, 14f, 120f).also { g.sensorNeeded(true, true, 14f, 120f) })
        assertEquals(120f, g.heading(true, true, true, 14f, 120f, null))
        assertFalse(g.sensorNeeded(true, true, 13f, 123f))
        assertEquals(123f, g.heading(true, true, true, 13f, 123f, null))
        // The next position is already (almost) standing still: the sensor is switched on, but has no reading yet.
        assertTrue(g.sensorNeeded(true, true, 0.4f, 123f))
        assertEquals(123f, g.heading(true, true, true, 0.4f, 123f, null))
        // Same values again (a recomposition): same answer.
        assertTrue(g.sensorNeeded(true, true, 0.4f, 123f))
        assertEquals(123f, g.heading(true, true, true, 0.4f, 123f, null))
        // Standing still without a bearing at all, still no reading: the direction is kept.
        assertTrue(g.sensorNeeded(true, true, 0f, null))
        assertEquals(123f, g.heading(true, true, true, 0f, null, null))
        // The first compass reading takes over, and from then on it's the compass alone.
        assertEquals(301f, g.heading(true, true, true, 0f, null, 301f))
        assertEquals(302f, g.heading(true, true, true, 0f, null, 302f))
    }

    @Test
    fun aPositionWithoutBearingAtSpeedKeepsTheDirectionUntilTheCompassReads() {
        val g = HeadingGate()
        g.sensorNeeded(true, true, 20f, 45f)
        assertEquals(45f, g.heading(true, true, true, 20f, 45f, null))
        assertTrue(g.sensorNeeded(true, true, 20f, null))
        assertEquals(45f, g.heading(true, true, true, 20f, null, null))
        assertEquals(50f, g.heading(true, true, true, 20f, null, 50f))
    }

    @Test
    fun slowingDownNormallyHasTheCompassReadyInTime() {
        val g = HeadingGate()
        g.sensorNeeded(true, true, 10f, 90f)
        assertEquals(90f, g.heading(true, true, true, 10f, 90f, null))
        // 3.5 m/s: sensor on again, GPS bearing still used; the compass gives its first reading within this second.
        assertTrue(g.sensorNeeded(true, true, 3.5f, 91f))
        assertEquals(91f, g.heading(true, true, true, 3.5f, 91f, null))
        assertEquals(91f, g.heading(true, true, true, 3.5f, 91f, 88f))
        // Below 1.5 m/s: the compass, as before.
        assertTrue(g.sensorNeeded(true, true, 1f, 91f))
        assertEquals(87f, g.heading(true, true, true, 1f, 91f, 87f))
    }

    @Test
    fun nothingIsKeptFromBeforeTheCompassWasWanted() {
        val g = HeadingGate()
        // Riding with plain "follow" (no compass mode, not navigating).
        g.sensorNeeded(false, true, 10f, 77f)
        assertEquals(77f, g.heading(true, false, true, 10f, 77f, null))
        g.sensorNeeded(false, true, 0f, null)
        assertNull(g.heading(true, false, true, 0f, null, null))
        // Compass mode switched on while standing still: no old bearing shows up while the sensor starts.
        assertTrue(g.sensorNeeded(true, true, 0f, null))
        assertNull(g.heading(true, true, true, 0f, null, null))
        assertEquals(10f, g.heading(true, true, true, 0f, null, 10f))
        // Turning it off again drops anything kept.
        g.sensorNeeded(true, true, 10f, 77f)
        g.heading(true, true, true, 10f, 77f, null)
        g.sensorNeeded(false, true, 0f, null)
        assertNull(g.heading(true, false, true, 0f, null, null))
        assertTrue(g.sensorNeeded(true, true, 0f, null))
        assertNull(g.heading(true, true, true, 0f, null, null))
    }

    @Test
    fun noPositionNoHeading() {
        val g = HeadingGate()
        assertTrue(g.sensorNeeded(true, true, null, null))
        assertNull(g.heading(false, true, true, null, null, 33f))
    }
}
