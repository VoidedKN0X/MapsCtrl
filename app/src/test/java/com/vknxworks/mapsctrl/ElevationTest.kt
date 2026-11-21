package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.ElevationProfile
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.saved.Gpx
import com.vknxworks.mapsctrl.saved.GpxData
import com.vknxworks.mapsctrl.saved.GpxTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringWriter

class ElevationTest {
    private val line = (0..10).map { LatLon(50.0, 3.0 + it * 0.001) }

    @Test
    fun climbIgnoresSmallNoise() {
        // Up 2 m and down 2 m all the way: noise, not climbing. Then 30 m up and 10 m down.
        val noise = floatArrayOf(10f, 12f, 10f, 12f, 10f, 12f)
        assertEquals(0.0 to 0.0, ElevationProfile.climb(noise))
        val (up, down) = ElevationProfile.climb(floatArrayOf(10f, 20f, 30f, 40f, 35f, 30f))
        assertEquals(30.0, up, 0.01)
        assertEquals(10.0, down, 0.01)
    }

    @Test
    fun profileAlongALine() {
        val h = listOf(10f, 20f, 30f, 40f, 50f, 60f, 50f, 40f, 30f, 20f, 10f)
        val p = ElevationProfile.of(line, h)!!
        assertEquals(50.0, p.ascent, 0.01)
        assertEquals(50.0, p.descent, 0.01)
        assertEquals(10f, p.min)
        assertEquals(60f, p.max)
        assertEquals(0.0, p.ascentAfter(p.distances[5]), 0.01)
        assertEquals(50.0, p.ascentAfter(0.0), 0.01)
        // Peaks survive downsampling.
        assertEquals(60f, p.downsample(4).max)
    }

    @Test
    fun noHeightsNoProfile() {
        assertNull(ElevationProfile.of(line, emptyList()))
        assertNull(ElevationProfile.of(line, line.map { null }))
    }

    @Test
    fun gpxKeepsHeights() {
        val t = GpxTrack("Climb", listOf(line.take(3)), elevations = listOf(listOf(12.5f, null, 30f)))
        val out = StringWriter()
        Gpx.write(out, GpxData(emptyList(), listOf(t)), "test")
        val back = Gpx.read(out.toString().byteInputStream()).tracks.single()
        assertEquals(listOf(listOf(12.5f, null, 30f)), back.elevations)
    }
}
