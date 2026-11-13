package com.vknxworks.mapsctrl.nav

import com.vknxworks.mapsctrl.data.SegmentRepository
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.map.MapWayNames
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.routing.StreetNames
import com.vknxworks.mapsctrl.routing.RouteError
import com.vknxworks.mapsctrl.routing.Router
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.transit.TransitPlanner
import com.vknxworks.mapsctrl.transit.TransitTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where a route starts: your position, or a place you chose (typed, searched or picked on the map). */
sealed interface RouteStart {
    data object MyLocation : RouteStart
    data class At(val place: Place) : RouteStart
}

sealed interface PlanStatus {
    data object Idle : PlanStatus
    data object Computing : PlanStatus
    /** Routing data must be downloaded first; [sizes] are bytes per tile (empty while asking the server). */
    data class NeedSegments(val tiles: Set<String>, val sizes: Map<String, Long>?, val sizeError: Boolean = false) : PlanStatus
    data object DownloadingSegments : PlanStatus
    data object NoLocation : PlanStatus
    data class Failed(val detail: String) : PlanStatus
    data class Ready(val route: Route) : PlanStatus
}

data class PlanState(
    val start: RouteStart = RouteStart.MyLocation,
    val destination: Place? = null,
    val mode: TravelMode = TravelMode.CAR,
    val status: PlanStatus = PlanStatus.Idle,
    /** Public transport (Transitous) instead of BRouter's [mode]; the journeys are in [TransitPlanner]'s state. */
    val transit: Boolean = false,
)

/**
 * The route planner: start, destination, travel mode, and the route BRouter found, or public transport journeys
 * from [transit] when you pick Transit.
 */
class RoutePlanner(
    private val scope: CoroutineScope,
    private val segments: SegmentRepository,
    /** The downloaded .map files, for street names in the turn instructions. */
    private val mapFiles: () -> List<File>,
    val transit: TransitPlanner,
    private val myLocation: () -> LatLon?,
) {
    /** BRouter's route with street names on its turns (from the maps on the phone; see [StreetNames]). */
    private fun named(route: Route): Route {
        val files = mapFiles()
        if (files.isEmpty()) return route
        return runCatching {
            MapWayNames(files).use { names -> StreetNames.annotate(route, names.takeUnless { it.isEmpty }) }
        }.getOrDefault(route)
    }

    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state

    private val router by lazy { Router(segments.profileDir, segments.segmentDir) }
    private var job: Job? = null

    val active: Boolean get() = _state.value.destination != null

    fun open(destination: Place, mode: TravelMode, start: RouteStart? = null) {
        transit.clear()
        _state.update { it.copy(destination = destination, mode = mode, start = start ?: it.start, status = PlanStatus.Idle, transit = false) }
        compute()
    }

    fun setStart(start: RouteStart) {
        _state.update { it.copy(start = start) }
        compute()
    }

    fun setMode(mode: TravelMode) {
        transit.clear()
        _state.update { it.copy(mode = mode, transit = false) }
        compute()
    }

    /** You picked Transit: ask Transitous (this is the only place a transit search starts from). */
    fun setTransit() {
        job?.cancel()
        router.cancel()
        _state.update { it.copy(transit = true, status = PlanStatus.Idle) }
        compute()
    }

    /** A new departure or arrival time for the transit search. */
    fun setTransitTime(time: TransitTime) {
        transit.setTime(time)
        if (_state.value.transit) compute()
    }

    fun swap() {
        val s = _state.value
        val dest = s.destination ?: return
        val start = s.start
        if (start !is RouteStart.At) return
        _state.update { it.copy(start = RouteStart.At(dest), destination = start.place) }
        compute()
    }

    fun close() {
        job?.cancel()
        router.cancel()
        transit.clear()
        _state.value = _state.value.copy(destination = null, status = PlanStatus.Idle, start = RouteStart.MyLocation, transit = false)
    }

    private fun startPoint(): LatLon? = when (val s = _state.value.start) {
        RouteStart.MyLocation -> myLocation()
        is RouteStart.At -> s.place.point
    }

    fun compute() {
        val s = _state.value
        val dest = s.destination ?: return
        val from = startPoint()
        if (from == null) {
            _state.update { it.copy(status = PlanStatus.NoLocation) }
            return
        }
        job?.cancel()
        router.cancel()
        if (s.transit) {
            _state.update { it.copy(status = PlanStatus.Idle) }
            transit.search(from, dest.point)
            return
        }
        _state.update { it.copy(status = PlanStatus.Computing) }
        job = scope.launch {
            val status = withContext(Dispatchers.Default) {
                try {
                    segments.ensureProfiles()
                    PlanStatus.Ready(named(router.route(listOf(from, dest.point), s.mode)))
                } catch (e: RouteError.MissingSegments) {
                    PlanStatus.NeedSegments(e.tiles, null)
                } catch (e: RouteError.NotFound) {
                    PlanStatus.Failed(e.detail)
                } catch (e: Exception) {
                    PlanStatus.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
            _state.update { it.copy(status = status) }
            if (status is PlanStatus.NeedSegments) {
                val sizes = runCatching { segments.sizes(status.tiles) }.getOrNull()
                _state.update { st ->
                    if (st.status is PlanStatus.NeedSegments) st.copy(status = PlanStatus.NeedSegments(status.tiles, sizes ?: emptyMap(), sizeError = sizes == null)) else st
                }
            }
        }
    }

    /** You agreed to download the routing data: fetch it, then try again. */
    fun downloadSegments() {
        val need = _state.value.status as? PlanStatus.NeedSegments ?: return
        _state.update { it.copy(status = PlanStatus.DownloadingSegments) }
        segments.download(need.tiles) { ok ->
            if (ok) compute() else _state.update { it.copy(status = PlanStatus.Failed(segments.error.value ?: "")) }
        }
    }

    /** Routes again from [from] (rerouting while navigating). Returns null when it fails. */
    suspend fun reroute(from: LatLon, dest: LatLon, mode: TravelMode): Route? = withContext(Dispatchers.Default) {
        runCatching { named(router.route(listOf(from, dest), mode, timeoutMs = 30_000)) }.getOrNull()
    }
}
