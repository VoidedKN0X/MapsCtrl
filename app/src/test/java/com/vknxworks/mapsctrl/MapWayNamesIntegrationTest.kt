package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.map.MapWayNames
import com.vknxworks.mapsctrl.routing.Router
import com.vknxworks.mapsctrl.routing.StreetNames
import com.vknxworks.mapsctrl.routing.TravelMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Street names from a real Mapsforge map. Needs monaco.map (download.mapsforge.org/maps/v5/europe/monaco.map) at the
 * path in MAPSCTRL_TEST_MAP; with MAPSCTRL_TEST_SEGMENTS as well, also names the turns of a real BRouter route.
 */
class MapWayNamesIntegrationTest {
    private val map = System.getProperty("mapsctrl.map")?.let { File(it) }
    private val segments = System.getProperty("mapsctrl.segments")?.let { File(it) }
    private val profiles = File(System.getProperty("mapsctrl.profiles") ?: "src/main/assets/brouter")

    @Test
    fun namedRoadsNearTheCasino() {
        assumeTrue(map != null && map.exists())
        MapWayNames(listOf(map!!), "en").use { names ->
            val ways = names.waysNear(LatLon(43.7392, 7.4275), 60.0)
            println("near the Casino: " + ways.map { it.name ?: it.ref }.distinct())
            assertTrue(ways.isNotEmpty())
            assertTrue(ways.all { it.highway != null && (it.name != null || it.ref != null) && it.points.size >= 2 })
        }
    }

    @Test
    fun turnsOfARealRouteGetStreets() {
        assumeTrue(map != null && map.exists() && segments != null && File(segments, "E5_N40.rd5").exists())
        val route = Router(profiles, segments!!).route(listOf(LatLon(43.7360, 7.4226), LatLon(43.7397, 7.4277)), TravelMode.CAR)
        val named = MapWayNames(listOf(map!!), "en").use { StreetNames.annotate(route, it) }
        named.hints.forEach { println("  ${it.index} ${it.kind} exit=${it.exit} -> ${it.street}") }
        assertTrue(named.hints.any { it.street != null })
    }
}
