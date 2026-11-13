package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.nav.NavigationTracker
import com.vknxworks.mapsctrl.routing.Maxspeed
import com.vknxworks.mapsctrl.routing.RouteError
import com.vknxworks.mapsctrl.routing.Router
import com.vknxworks.mapsctrl.routing.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the real BRouter engine on a real segment file. Needs the E5_N40.rd5 tile (Monaco) in the folder named by
 * the MAPSCTRL_TEST_SEGMENTS environment variable; skipped otherwise.
 */
class RouterIntegrationTest {
    private val profiles = File(System.getProperty("mapsctrl.profiles") ?: "src/main/assets/brouter")
    private val segments = System.getProperty("mapsctrl.segments")?.let { File(it) }

    private fun router(): Router {
        assumeTrue(segments != null && File(segments, "E5_N40.rd5").exists())
        return Router(profiles, segments!!)
    }

    // Monaco: from the port (Quai Antoine 1er) to the Casino.
    private val from = LatLon(43.7360, 7.4226)
    private val to = LatLon(43.7397, 7.4277)

    @Test
    fun carRouteInMonaco() {
        val r = router().route(listOf(from, to), TravelMode.CAR)
        println("car: ${r.distanceMeters} m, ${r.durationSeconds} s, ${r.points.size} points, ${r.hints.size} hints, ${r.sections.size} sections")
        r.sections.take(6).forEach { println("  ${it.startIndex}-${it.endIndex} ${it.wayTags} ${it.nodeTags}") }
        r.hints.forEach { println("  hint ${it.index} ${it.kind} exit=${it.exit} angle=${it.angle}") }
        assertTrue(r.distanceMeters > 300)
        assertTrue(r.durationSeconds > 0)
        assertEquals(r.points.size, r.times.size)
        assertTrue(r.hints.isNotEmpty())
        assertTrue(r.sections.isNotEmpty())
        // processUnusedTags: highway is always there; maxspeed shows up on at least some of Monaco's roads.
        assertTrue(r.sections.all { it.wayTags.containsKey("highway") })
        println("  limits: " + r.sections.mapNotNull { Maxspeed.forWay(it.wayTags)?.kmh }.distinct())
        val t = NavigationTracker(r)
        val p = t.update(r.points[1])
        assertTrue(p.distanceLeft > 0)
    }

    @Test
    fun footAndBike() {
        val router = router()
        val foot = router.route(listOf(from, to), TravelMode.FOOT)
        val bike = router.route(listOf(from, to), TravelMode.BIKE)
        println("foot: ${foot.distanceMeters} m ${foot.durationSeconds} s; bike: ${bike.distanceMeters} m ${bike.durationSeconds} s")
        assertTrue(foot.distanceMeters > 300 && bike.distanceMeters > 300)
    }

    @Test
    fun missingTile() {
        val router = router()
        try {
            router.route(listOf(from, LatLon(48.85, 2.35)), TravelMode.CAR)
            throw AssertionError("expected missing segments")
        } catch (e: RouteError.MissingSegments) {
            assertTrue(e.tiles.contains("E0_N45"))
        }
    }
}
