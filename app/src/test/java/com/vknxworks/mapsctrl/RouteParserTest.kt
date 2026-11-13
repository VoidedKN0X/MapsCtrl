package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.nav.NavigationTracker
import com.vknxworks.mapsctrl.routing.Maxspeed
import com.vknxworks.mapsctrl.routing.RouteParser
import com.vknxworks.mapsctrl.routing.Segments
import com.vknxworks.mapsctrl.routing.SpeedLimit
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteParserTest {

    /** Shaped like BRouter's FormatJson output: an L-shaped route going north, then east. */
    private val json = """
    {
      "type": "FeatureCollection",
      "features": [
        {
          "type": "Feature",
          "properties": {
            "creator": "BRouter-1.7.11",
            "name": "brouter_car-vario_0",
            "track-length": "334",
            "filtered ascend": "0",
            "plain-ascend": "0",
            "total-time": "40",
            "total-energy": "0",
            "cost": "400",
            "voicehints": [
              [2,5,0,111,90]
            ],
            "messages": [
              ["Longitude", "Latitude", "Elevation", "Distance", "CostPerKm", "ElevCost", "TurnCost", "NodeCost", "InitialCost", "WayTags", "NodeTags", "Time", "Energy"],
              ["7420000", "43731000", "10", "223", "1000", "0", "0", "0", "0", "highway=primary maxspeed=50", "", "27", "0"],
              ["7421000", "43731000", "10", "80", "1000", "0", "0", "0", "0", "highway=residential maxspeed=30", "highway=speed_camera", "37", "0"],
              ["7421400", "43731000", "10", "31", "1000", "0", "0", "0", "0", "highway=residential maxspeed=30", "", "40", "0"]
            ],
            "times": [0,13.5,27,37,40]
          },
          "geometry": {
            "type": "LineString",
            "coordinates": [
              [7.420000, 43.729000, 10.0],
              [7.420000, 43.730000, 10.0],
              [7.420000, 43.731000, 10.0],
              [7.421000, 43.731000, 10.0],
              [7.421400, 43.731000, 10.0]
            ]
          }
        }
      ]
    }
    """.trimIndent()

    @Test
    fun parsesBRouterJson() {
        val r = RouteParser.parse(json, TravelMode.CAR)
        assertEquals(5, r.points.size)
        assertEquals(LatLon(43.729, 7.42), r.points[0])
        assertEquals(334.0, r.distanceMeters, 0.1)
        assertEquals(40.0, r.durationSeconds, 0.1)
        assertEquals(5, r.times.size)
        assertEquals(1, r.hints.size)
        assertEquals(TurnKind.RIGHT, r.hints[0].kind)
        assertEquals(2, r.hints[0].index)
        assertEquals(3, r.sections.size)
        assertEquals(0, r.sections[0].startIndex)
        assertEquals(2, r.sections[0].endIndex)
        assertEquals("primary", r.sections[0].wayTags["highway"])
        assertEquals("speed_camera", r.sections[1].nodeTags["highway"])
        assertEquals(SpeedLimit(30), Maxspeed.forWay(r.sectionAt(3)!!.wayTags))
        assertEquals(SpeedLimit(50), Maxspeed.forWay(r.sectionAt(0)!!.wayTags))
    }

    @Test
    fun tracksProgress() {
        val r = RouteParser.parse(json, TravelMode.CAR)
        val t = NavigationTracker(r)
        assertEquals(listOf(TurnKind.RIGHT, TurnKind.ARRIVE), t.hints.map { it.kind })
        assertEquals(1, t.cameras.size)

        // Just after the start, slightly beside the road.
        var p = t.update(LatLon(43.7292, 7.42003))
        assertEquals(TurnKind.RIGHT, p.nextHint?.kind)
        assertTrue(p.distanceToNext in 180.0..210.0)
        assertFalse(p.offRoute)
        assertEquals(SpeedLimit(50), p.speedLimit)
        assertNull(p.cameraAhead?.takeIf { it > 1000 })
        assertNotNull(p.cameraAhead) // 280 m ahead: within the 500 m warning distance for cars

        // A spoken "near" warning comes once.
        val a = t.announcement(p)
        assertNotNull(a)
        assertNull(t.announcement(t.update(LatLon(43.7293, 7.42))))

        // After the turn: on the 30 road, heading for the end.
        p = t.update(LatLon(43.731, 7.4212))
        assertEquals(TurnKind.ARRIVE, p.nextHint?.kind)
        assertEquals(SpeedLimit(30), p.speedLimit)
        assertTrue(p.timeLeftSeconds < 5)

        // Arrived.
        assertTrue(t.update(LatLon(43.731, 7.4214)).arrived)
    }

    @Test
    fun offRoute() {
        val t = NavigationTracker(RouteParser.parse(json, TravelMode.CAR))
        val away = LatLon(43.7295, 7.4230) // ~240 m east of the road
        assertFalse(t.update(away).offRoute)
        assertFalse(t.update(away).offRoute)
        assertTrue(t.update(away).offRoute)
        assertFalse(t.update(LatLon(43.7295, 7.42)).offRoute)
    }

    @Test
    fun segmentTiles() {
        assertEquals("E5_N40", Segments.tileName(43.73, 7.42))
        assertEquals("W5_N50", Segments.tileName(51.5, -0.12))
        assertEquals("E0_N50", Segments.tileName(51.5, 0.12))
        assertEquals("W75_S35", Segments.tileName(-33.4, -70.6))
        assertEquals(setOf("E5_N40"), Segments.tilesFor(listOf(LatLon(43.73, 7.42), LatLon(43.75, 7.44))))
        assertEquals(setOf("E0_N50", "E5_N50"), Segments.tilesFor(listOf(LatLon(50.85, 4.35), LatLon(51.2, 5.2))))
    }
}
