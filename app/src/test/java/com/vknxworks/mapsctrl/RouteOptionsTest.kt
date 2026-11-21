package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.nav.Stops
import com.vknxworks.mapsctrl.routing.Avoid
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.routing.RouteOptions
import com.vknxworks.mapsctrl.routing.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RouteOptionsTest {
    @Test
    fun carUsesItsOwnSwitches() {
        val p = RouteOptions.profileParams(TravelMode.CAR, setOf(Avoid.TOLLS, Avoid.FERRIES))
        assertEquals(mapOf("avoid_toll" to "1", "avoid_motorways" to "0", "avoid_unpaved" to "0"), p)
    }

    @Test
    fun bikeAndFootTurnFerriesOff() {
        assertEquals(mapOf("allow_ferries" to "0"), RouteOptions.profileParams(TravelMode.BIKE, setOf(Avoid.FERRIES, Avoid.TOLLS)))
        assertEquals(mapOf("allow_ferries" to "1"), RouteOptions.profileParams(TravelMode.FOOT, emptySet()))
    }

    @Test
    fun mopedSwitches() {
        assertEquals(mapOf("avoid_unpaved" to "1", "avoid_ferries" to "0"), RouteOptions.profileParams(TravelMode.MOPED, setOf(Avoid.UNPAVED, Avoid.MOTORWAYS)))
        assertEquals(listOf(Avoid.UNPAVED), RouteOptions.active(TravelMode.MOPED, setOf(Avoid.UNPAVED, Avoid.MOTORWAYS)))
    }

    /** Every switch MapsCtrl sets must be declared in the bundled profile, or BRouter would silently ignore it. */
    @Test
    fun everySwitchIsInItsProfile() {
        val dir = File(System.getProperty("mapsctrl.profiles") ?: "src/main/assets/brouter")
        for (mode in TravelMode.entries) {
            val text = File(dir, mode.profile + ".brf").readText()
            for (key in RouteOptions.profileParams(mode, Avoid.entries.toSet()).keys) {
                assertTrue("$key missing in ${mode.profile}.brf", Regex("""assign\s+$key\s""").containsMatchIn(text))
            }
        }
    }

    @Test
    fun stopsAreFoundInOrderAlongTheRoute() {
        // A straight line east, 4 points ~100 m apart; then back west next to it.
        val pts = listOf(LatLon(50.0, 3.0), LatLon(50.0, 3.0014), LatLon(50.0, 3.0028), LatLon(50.0001, 3.0028), LatLon(50.0001, 3.0014))
        val route = Route(TravelMode.MOPED, pts, emptyList(), 0.0, 0.0, emptyList(), emptyList())
        val total = route.cumulative.last()
        // The second stop lies at the start of the line, but on the way back: it must be matched on the way back.
        val alongs = Stops.alongs(route, listOf(LatLon(50.0, 3.0028), LatLon(50.0001, 3.0014)))
        assertEquals(route.cumulative[2], alongs[0], 1.0)
        assertEquals(total, alongs[1], 1.0)
        assertEquals(listOf(1), Stops.remaining(alongs, alongs[0] + 1.0))
        assertEquals(listOf(0, 1), Stops.remaining(alongs, 0.0))
    }
}
