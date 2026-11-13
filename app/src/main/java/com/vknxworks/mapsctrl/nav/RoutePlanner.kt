package com.vknxworks.mapsctrl.nav

import com.vknxworks.mapsctrl.data.SegmentRepository
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.routing.RouteError
import com.vknxworks.mapsctrl.routing.Router
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.search.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

/** The route planner: start, destination, travel mode, and the route BRouter found. */
class RoutePlanner(
    private val scope: CoroutineScope,
    private val segments: SegmentRepository,
    private val myLocation: () -> LatLon?,
) {
    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state

    private val router by lazy { Router(segments.profileDir, segments.segmentDir) }
    private var job: Job? = null

    val active: Boolean get() = _state.value.destination != null

    fun open(destination: Place, mode: TravelMode, start: RouteStart? = null) {
        _state.update { it.copy(destination = destination, mode = mode, start = start ?: it.start, status = PlanStatus.Idle) }
        compute()
    }

    fun setStart(start: RouteStart) {
        _state.update { it.copy(start = start) }
        compute()
    }

    fun setMode(mode: TravelMode) {
        _state.update { it.copy(mode = mode) }
        compute()
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
        _state.value = _state.value.copy(destination = null, status = PlanStatus.Idle, start = RouteStart.MyLocation)
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
        _state.update { it.copy(status = PlanStatus.Computing) }
        job = scope.launch {
            val status = withContext(Dispatchers.Default) {
                try {
                    segments.ensureProfiles()
                    PlanStatus.Ready(router.route(listOf(from, dest.point), s.mode))
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
        runCatching { router.route(listOf(from, dest), mode, timeoutMs = 30_000) }.getOrNull()
    }
}
