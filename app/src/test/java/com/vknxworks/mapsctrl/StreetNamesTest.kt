package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.routing.NamedWay
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.routing.StreetNames
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.TurnHint
import com.vknxworks.mapsctrl.routing.TurnKind
import com.vknxworks.mapsctrl.routing.WayNameSource
import com.vknxworks.mapsctrl.routing.WaySection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetNamesTest {
    // Around Monaco: 100 m east, then a left turn and 100 m north.
    private val lat = 43.73
    private val lon = 7.42
    private val dLon100 = 0.001243 // ~100 m east-west here
    private val dLat50 = 0.00045 // ~50 m north-south
    private val points = listOf(
        LatLon(lat, lon),
        LatLon(lat, lon + dLon100 / 2),
        LatLon(lat, lon + dLon100),
        LatLon(lat + dLat50, lon + dLon100),
        LatLon(lat + 2 * dLat50, lon + dLon100),
    )
    private val left = TurnHint(2, TurnKind.LEFT, 0, -90)

    private fun route(hints: List<TurnHint> = listOf(left), sections: List<WaySection> = emptyList()) =
        Route(TravelMode.CAR, points, emptyList(), 200.0, 30.0, hints, sections)

    private val streetA = NamedWay("Rue Grimaldi", null, "secondary", listOf(LatLon(lat, lon - 0.002), LatLon(lat, lon + 0.004)))
    private val streetB = NamedWay("Boulevard Albert 1er", "D6007", "primary", listOf(LatLon(lat - 0.001, lon + dLon100), LatLon(lat + 0.003, lon + dLon100)))
    // Parallel to B but 40 m east: too far to be the road the route follows.
    private val decoy = NamedWay("Rue Decoy", null, "residential", listOf(LatLon(lat - 0.001, lon + dLon100 + 0.0005), LatLon(lat + 0.003, lon + dLon100 + 0.0005)))

    private class ListSource(private val ways: List<NamedWay>) : WayNameSource {
        var calls = 0
        override fun waysNear(p: LatLon, radiusM: Double): List<NamedWay> { calls++; return ways }
    }

    @Test
    fun tags() {
        assertEquals("Rue Grimaldi", StreetNames.fromTags(mapOf("highway" to "secondary", "name" to "Rue_Grimaldi")))
        assertEquals("A8", StreetNames.fromTags(mapOf("highway" to "motorway", "ref" to "A8")))
        assertEquals("Quai Antoine 1er", StreetNames.fromTags(mapOf("name" to "Quai_Antoine_1er", "ref" to "D6098")))
        assertNull(StreetNames.fromTags(mapOf("highway" to "residential")))
        assertNull(StreetNames.fromTags(mapOf("name" to "  ")))
        assertNull(StreetNames.label(null, "x".repeat(200)))
    }

    @Test
    fun namesFromRoutingTagsFirst() {
        val sections = listOf(
            WaySection(0, 2, mapOf("highway" to "secondary"), emptyMap()),
            WaySection(2, 4, mapOf("highway" to "primary", "name" to "Avenue_Princesse_Grace"), emptyMap()),
        )
        val source = ListSource(listOf(streetA, streetB))
        val r = StreetNames.annotate(route(sections = sections), source)
        assertEquals("Avenue Princesse Grace", r.hints[0].street)
        assertEquals(0, source.calls)
    }

    @Test
    fun fallsBackToTheMapData() {
        val r = StreetNames.annotate(route(), ListSource(listOf(streetA, streetB, decoy)))
        // Rue Grimaldi is just as close to the turn but runs across the way you go; Boulevard Albert 1er runs along it.
        assertEquals("Boulevard Albert 1er", r.hints[0].street)
    }

    @Test
    fun refWhenTheRoadHasNoName() {
        val b = streetB.copy(name = null)
        val r = StreetNames.annotate(route(), ListSource(listOf(streetA, b)))
        assertEquals("D6007", r.hints[0].street)
    }

    @Test
    fun nothingNearby() {
        assertNull(StreetNames.annotate(route(), ListSource(listOf(streetA, decoy))).hints[0].street)
        assertNull(StreetNames.annotate(route(), null).hints[0].street)
        // A source that fails costs the name, never the route.
        val r = StreetNames.annotate(route(), WayNameSource { _, _ -> throw IllegalStateException("broken map") })
        assertNull(r.hints[0].street)
        assertEquals(points, r.points)
    }

    @Test
    fun ignoresStepsRoundaboutsAndArrival() {
        val steps = streetB.copy(highway = "steps")
        assertNull(StreetNames.annotate(route(), ListSource(listOf(steps))).hints[0].street)
        val ring = streetB.copy(roundabout = true)
        assertNull(StreetNames.annotate(route(), ListSource(listOf(ring))).hints[0].street)
        val arrive = TurnHint(4, TurnKind.ARRIVE, 0, 0)
        assertNull(StreetNames.annotate(route(listOf(arrive)), ListSource(listOf(streetB))).hints[0].street)
    }

    @Test
    fun roundaboutUsesTheRoadAfterIt() {
        val rb = TurnHint(1, TurnKind.ROUNDABOUT, 2, 0)
        val sections = listOf(
            WaySection(0, 1, mapOf("highway" to "secondary", "name" to "Before"), emptyMap()),
            WaySection(1, 3, mapOf("highway" to "secondary", "junction" to "roundabout", "name" to "Rond-point"), emptyMap()),
            WaySection(3, 4, mapOf("highway" to "primary", "name" to "After"), emptyMap()),
        )
        val r = route(listOf(rb), sections)
        assertEquals(3, StreetNames.outgoingIndex(r, rb))
        assertEquals("After", StreetNames.annotate(r, null).hints[0].street)
    }

    @Test
    fun bestMatchPrefersCloseAndAligned() {
        val p = LatLon(lat + 0.0001, lon + dLon100)
        val best = StreetNames.best(listOf(streetA, streetB, decoy), p, 0.0)
        assertEquals("Boulevard Albert 1er", best?.first)
        assertTrue((best?.second ?: 99.0) < 1.0)
        // Driving the other way along B matches too: roads are drawn in either direction.
        assertEquals("Boulevard Albert 1er", StreetNames.best(listOf(streetB), p, 180.0)?.first)
    }
}
