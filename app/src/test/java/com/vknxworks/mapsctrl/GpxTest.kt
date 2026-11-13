package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.saved.Gpx
import com.vknxworks.mapsctrl.saved.GpxData
import com.vknxworks.mapsctrl.saved.GpxTrack
import com.vknxworks.mapsctrl.saved.GpxWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Instant

class GpxTest {

    @Test
    fun roundTrip() {
        val data = GpxData(
            waypoints = listOf(
                GpxWaypoint(LatLon(43.7384, 7.4246), "Casino & <Café>", "Place du \"Casino\"", Instant.parse("2026-11-13T10:00:00Z")),
                GpxWaypoint(LatLon(-33.8688, 151.2093), "Sydney"),
            ),
            tracks = listOf(
                GpxTrack(
                    "Walk",
                    listOf(listOf(LatLon(43.73, 7.42), LatLon(43.731, 7.421)), listOf(LatLon(43.74, 7.43))),
                    listOf(listOf(Instant.parse("2026-11-13T10:00:00Z"), null), listOf(null)),
                ),
            ),
        )
        val out = StringWriter()
        Gpx.write(out, data, "MapsCtrl")
        val xml = out.toString()
        assertTrue(xml.contains("Casino &amp; &lt;Café&gt;"))

        val back = Gpx.read(xml.byteInputStream())
        assertEquals(2, back.waypoints.size)
        assertEquals("Casino & <Café>", back.waypoints[0].name)
        assertEquals("Place du \"Casino\"", back.waypoints[0].description)
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), back.waypoints[0].time)
        assertEquals(LatLon(-33.8688, 151.2093), back.waypoints[1].point)
        assertEquals(1, back.tracks.size)
        assertEquals("Walk", back.tracks[0].name)
        assertEquals(2, back.tracks[0].segments.size)
        assertEquals(LatLon(43.731, 7.421), back.tracks[0].segments[0][1])
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), back.tracks[0].times[0][0])
    }

    @Test
    fun readsOtherApps() {
        val xml = """<?xml version="1.0"?>
            <gpx:gpx xmlns:gpx="http://www.topografix.com/GPX/1/1" version="1.1" creator="x">
              <gpx:wpt lat="50.1" lon="4.2"><gpx:name>A</gpx:name></gpx:wpt>
              <gpx:wpt lat="bad" lon="4.2"><gpx:name>skipped</gpx:name></gpx:wpt>
              <gpx:rte><gpx:name>R</gpx:name><gpx:rtept lat="50" lon="4"/><gpx:rtept lat="50.1" lon="4.1"/></gpx:rte>
              <gpx:trk><gpx:trkseg><gpx:trkpt lat="1" lon="2"/></gpx:trkseg></gpx:trk>
            </gpx:gpx>"""
        val data = Gpx.read(xml.byteInputStream())
        assertEquals(listOf("A"), data.waypoints.map { it.name })
        assertEquals(listOf("Track 1", "R"), data.tracks.map { it.name })
        assertEquals(2, data.tracks[1].segments[0].size)
    }

    @Test(expected = Exception::class)
    fun refusesDoctype() {
        val xml = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]><gpx><wpt lat="1" lon="1"><name>&x;</name></wpt></gpx>"""
        Gpx.read(xml.byteInputStream())
    }
}
