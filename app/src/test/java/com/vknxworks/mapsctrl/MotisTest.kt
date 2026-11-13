package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.transit.LegKind
import com.vknxworks.mapsctrl.transit.Motis
import com.vknxworks.mapsctrl.transit.TransitTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URI
import java.time.Instant

class MotisTest {
    private val monaco = LatLon(43.738412345, 7.424612345)
    private val nice = LatLon(43.7045, 7.2619)

    private fun query(url: String): Map<String, String> = URI(url).rawQuery.split('&').associate {
        val (k, v) = it.split('=', limit = 2)
        k to java.net.URLDecoder.decode(v, "UTF-8")
    }

    @Test
    fun requestSendsOnlyPointsAndTime() {
        val now = Motis.planUrl(monaco, nice, TransitTime.Now)
        assertTrue(now.startsWith("https://api.transitous.org/api/v6/plan?"))
        val q = query(now)
        assertEquals(setOf("fromPlace", "toPlace", "numItineraries"), q.keys)
        // Rounded to 5 decimals, always with a dot.
        assertEquals("43.73841,7.42461", q["fromPlace"])
        assertEquals("43.70450,7.26190", q["toPlace"])

        val t = Instant.parse("2026-11-16T08:00:00.123Z")
        val depart = query(Motis.planUrl(monaco, nice, TransitTime.DepartAt(t)))
        assertEquals("2026-11-16T08:00:00Z", depart["time"])
        assertNull(depart["arriveBy"])
        val arrive = query(Motis.planUrl(monaco, nice, TransitTime.ArriveBy(t), numItineraries = 50))
        assertEquals("true", arrive["arriveBy"])
        assertEquals("10", arrive["numItineraries"])
        assertTrue(Motis.planUrl(monaco, nice, TransitTime.ArriveBy(t)).contains("time=2026-11-16T08%3A00%3A00Z"))
    }

    @Test
    fun invalidPointsAreRefused() {
        try {
            Motis.planUrl(LatLon(91.0, 0.0), nice, TransitTime.Now)
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun userAgentNamesTheApp() {
        assertEquals("MapsCtrl/0.4.0 (+https://github.com/VoidedKN0X/MapsCtrl)", Motis.userAgent("0.4.0"))
    }

    @Test
    fun parsesARealAnswer() {
        val body = javaClass.getResource("/motis_plan_v6.json")!!.readText()
        val plan = Motis.parsePlan(body)
        assertEquals(2, plan.itineraries.size)
        assertTrue(plan.direct.isEmpty())

        val first = plan.itineraries[0]
        assertEquals(0, first.transfers)
        assertEquals(2160L, first.durationSeconds)
        assertEquals(listOf(LegKind.WALK, LegKind.RAIL, LegKind.WALK), first.legs.map { it.kind })
        val train = first.legs[1]
        assertEquals("C3", train.line)
        assertEquals("Grasse", train.headsign)
        assertEquals(0xFF0749FF.toInt(), train.color)
        assertEquals("Gare de Monaco - Pont Sainte Dévote", train.from.name)
        assertEquals(5, train.intermediateStops)
        assertEquals(267, train.geometry.size)
        // The line starts at the station and ends in Nice (precision 6).
        assertEquals(train.from.point.lat, train.geometry.first().lat, 0.001)
        assertEquals(nice.lon, train.geometry.last().lon, 0.01)
        val walk = first.legs[0]
        assertNull(walk.line)
        assertEquals(727.0, walk.distanceMeters!!, 0.5)

        val second = plan.itineraries[1]
        assertEquals(1, second.transfers)
        assertEquals("1", second.legs[1].line)
        assertEquals(LegKind.BUS, second.legs[1].kind)
        assertEquals(2, second.transitLegs.size)
    }

    @Test
    fun badAnswersDontCrash() {
        for (bad in listOf("", "null", "[]", "{}", "<html>busy</html>", "{\"itineraries\": 5}")) {
            try {
                Motis.parsePlan(bad)
                fail("accepted: $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
        // Broken itineraries and legs are skipped, the rest is kept.
        val body = """
            {"itineraries": [
              {"legs": "nope"},
              {"legs": [{"mode": "BUS", "from": {"name": "A", "lat": 999, "lon": 0}, "to": {"name": "B", "lat": 1, "lon": 1},
                         "startTime": "2026-11-16T08:00:00Z", "endTime": "2026-11-16T08:10:00Z"}]},
              {"startTime": "2026-11-16T08:00:00Z", "endTime": "2026-11-16T08:10:00Z", "transfers": 0,
               "legs": [{"mode": "TRAM", "routeShortName": "T1", "routeColor": "zzzzzz",
                         "from": {"name": "A", "lat": 43.7, "lon": 7.2}, "to": {"name": "B", "lat": 43.71, "lon": 7.21},
                         "startTime": "2026-11-16T08:00:00Z", "endTime": "2026-11-16T08:10:00Z",
                         "legGeometry": {"points": "%%%broken", "precision": 6, "length": 3}}]},
              {"legs": [{"mode": "SPACESHIP", "from": {"name": "A", "lat": 1, "lon": 1}, "to": {"name": "B", "lat": 2, "lon": 2},
                         "startTime": "2026-11-16T09:00:00Z", "endTime": "2026-11-16T08:00:00Z"}]}
            ], "direct": [{"legs": [{"mode": "WALK", "from": {"name": "A", "lat": 1, "lon": 1}, "to": {"name": "B", "lat": 1.001, "lon": 1},
                         "startTime": "2026-11-16T08:00:00Z", "endTime": "2026-11-16T08:12:00Z", "distance": 111}]}]}
        """.trimIndent()
        val plan = Motis.parsePlan(body)
        assertEquals(1, plan.itineraries.size)
        val tram = plan.itineraries[0].legs.single()
        assertEquals(LegKind.TRAM, tram.kind)
        assertNull(tram.color)
        // A broken line falls back to a straight line between the stops.
        assertEquals(2, tram.geometry.size)
        assertEquals(1, plan.direct.size)
        assertFalse(plan.direct[0].legs[0].kind.isTransit)
    }

    @Test
    fun polyline() {
        // Google's documented example, at 5 decimals.
        val p = Motis.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, p.size)
        assertEquals(38.5, p[0].lat, 1e-9); assertEquals(-120.2, p[0].lon, 1e-9)
        assertEquals(40.7, p[1].lat, 1e-9); assertEquals(-120.95, p[1].lon, 1e-9)
        assertEquals(43.252, p[2].lat, 1e-9); assertEquals(-126.453, p[2].lon, 1e-9)
        try {
            Motis.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`", 5) // cut short
            fail()
        } catch (_: IllegalArgumentException) {
        }
        // Read at precision 1 the same numbers are far outside the earth.
        try {
            Motis.decodePolyline("_p~iF~ps|U", 1)
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun colours() {
        assertEquals(0xFFC4362C.toInt(), Motis.color("c4362c"))
        assertEquals(0xFFFFFFFF.toInt(), Motis.color("#FFFFFF"))
        assertNull(Motis.color("fff"))
        assertNull(Motis.color(""))
        assertNull(Motis.color(null))
    }

    @Test
    fun modes() {
        assertEquals(LegKind.RAIL, LegKind.fromMotis("REGIONAL_FAST_RAIL"))
        assertEquals(LegKind.SUBWAY, LegKind.fromMotis("METRO"))
        assertEquals(LegKind.CABLE, LegKind.fromMotis("AERIAL_LIFT"))
        assertEquals(LegKind.OTHER, LegKind.fromMotis("SOMETHING_NEW"))
        assertFalse(LegKind.WALK.isTransit)
        assertTrue(LegKind.OTHER.isTransit)
    }
}
