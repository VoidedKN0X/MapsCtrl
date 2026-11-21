package com.vknxworks.mapsctrl.nav

import com.vknxworks.mapsctrl.data.SegmentRepository
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.map.MapWayNames
import com.vknxworks.mapsctrl.routing.Avoid
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
    /** Places to pass on the way, in order, between the start and the destination. */
    val stops: List<Place> = emptyList(),
    /** Other routes BRouter found when you asked for them (the first route is in [PlanStatus.Ready]). */
    val alternatives: List<Route> = emptyList(),
    val alternativesState: AlternativesState = AlternativesState.NONE,
    /** Which route you picked: 0 is BRouter's first choice, 1 and up are [alternatives]. */
    val selected: Int = 0,
) {
    /** Every route found so far: BRouter's first choice, then the alternatives. */
    val routes: List<Route> get() = listOfNotNull((status as? PlanStatus.Ready)?.route) + alternatives

    /** The route you picked (the first one unless you chose an alternative). */
    val chosen: Route? get() = routes.getOrNull(selected) ?: (status as? PlanStatus.Ready)?.route
}

enum class AlternativesState { NONE, COMPUTING, DONE }

/** At most this many stops between the start and the destination. */
const val MAX_STOPS = 5

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
    /** What to avoid, from the settings (only the switches the mode's profile has are used). */
    private val avoid: () -> Set<Avoid>,
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
    private var altJob: Job? = null

    val active: Boolean get() = _state.value.destination != null

    fun open(destination: Place, mode: TravelMode, start: RouteStart? = null) {
        transit.clear()
        _state.update { it.copy(destination = destination, mode = mode, start = start ?: it.start, status = PlanStatus.Idle, transit = false, stops = emptyList()) }
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
        // The stops are passed the other way round too.
        _state.update { it.copy(start = RouteStart.At(dest), destination = start.place, stops = it.stops.reversed()) }
        compute()
    }

    /** Adds a stop at the end of the list of stops (just before the destination). */
    fun addStop(place: Place) {
        if (_state.value.stops.size >= MAX_STOPS) return
        _state.update { it.copy(stops = it.stops + place) }
        compute()
    }

    fun removeStop(index: Int) {
        _state.update { it.copy(stops = it.stops.filterIndexed { i, _ -> i != index }) }
        compute()
    }

    /** Moves a stop one place earlier ([up]) or later in the order. */
    fun moveStop(index: Int, up: Boolean) {
        val list = _state.value.stops.toMutableList()
        val to = if (up) index - 1 else index + 1
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        _state.update { it.copy(stops = list) }
        compute()
    }

    /** The avoid options changed: work the route out again. */
    fun optionsChanged() {
        if (_state.value.destination != null && !_state.value.transit) compute()
    }

    /** Picks one of [PlanState.routes]. */
    fun select(index: Int) {
        _state.update { if (index in it.routes.indices) it.copy(selected = index) else it }
    }

    /**
     * Asks BRouter for up to two other routes. They're only worked out when you ask, since each one means
     * running the router again (BRouter finds the second route after the first, the third after both).
     */
    fun findAlternatives() {
        val s = _state.value
        val first = (s.status as? PlanStatus.Ready)?.route ?: return
        if (s.alternativesState != AlternativesState.NONE) return
        val from = startPoint() ?: return
        val dest = s.destination ?: return
        val points = listOf(from) + s.stops.map { it.point } + dest.point
        val avoiding = avoid()
        _state.update { it.copy(alternativesState = AlternativesState.COMPUTING) }
        altJob = scope.launch {
            val found = ArrayList<Route>()
            for (i in 1..2) {
                val r = withContext(Dispatchers.Default) {
                    runCatching { named(router.route(points, s.mode, avoiding, alternative = i)) }.getOrNull()
                } ?: break
                if (_state.value.status !is PlanStatus.Ready || (_state.value.status as PlanStatus.Ready).route !== first) return@launch
                if ((listOf(first) + found).none { same(it, r) }) {
                    found += r
                    _state.update { it.copy(alternatives = found.toList()) }
                }
            }
            _state.update { it.copy(alternativesState = AlternativesState.DONE) }
        }
    }

    /** Two routes are the same when they're as long and pass the same points (BRouter can find the same one twice). */
    private fun same(a: Route, b: Route): Boolean =
        kotlin.math.abs(a.distanceMeters - b.distanceMeters) < 1.0 && a.points.size == b.points.size

    fun close() {
        job?.cancel()
        altJob?.cancel()
        router.cancel()
        transit.clear()
        _state.value = _state.value.copy(
            destination = null, status = PlanStatus.Idle, start = RouteStart.MyLocation, transit = false, stops = emptyList(),
            alternatives = emptyList(), alternativesState = AlternativesState.NONE, selected = 0,
        )
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
        altJob?.cancel()
        router.cancel()
        _state.update { it.copy(alternatives = emptyList(), alternativesState = AlternativesState.NONE, selected = 0) }
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
                    val points = listOf(from) + s.stops.map { it.point } + dest.point
                    PlanStatus.Ready(named(router.route(points, s.mode, avoid())))
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

    /** Routes again from [from] through the [stops] still ahead to [dest] (rerouting while navigating). Null when it fails. */
    suspend fun reroute(from: LatLon, dest: LatLon, mode: TravelMode, stops: List<LatLon> = emptyList()): Route? = withContext(Dispatchers.Default) {
        runCatching { named(router.route(listOf(from) + stops + dest, mode, avoid(), timeoutMs = 30_000)) }.getOrNull()
    }
}
