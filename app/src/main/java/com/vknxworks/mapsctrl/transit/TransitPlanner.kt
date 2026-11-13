package com.vknxworks.mapsctrl.transit

import com.vknxworks.mapsctrl.BuildConfig
import com.vknxworks.mapsctrl.data.Http
import com.vknxworks.mapsctrl.geo.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

sealed interface TransitStatus {
    data object Idle : TransitStatus
    data object Loading : TransitStatus
    data class Ready(val plan: TransitPlan) : TransitStatus
    /** [offline]: no connection or no answer; [busy]: the server asked us to slow down; else a bad answer. */
    data class Failed(val offline: Boolean, val busy: Boolean = false) : TransitStatus
}

data class TransitState(
    val time: TransitTime = TransitTime.Now,
    val status: TransitStatus = TransitStatus.Idle,
    /** Index in the plan's itineraries (then the direct ones) of the journey shown on the map. */
    val selected: Int = 0,
    /** The selected journey is open with all its legs. */
    val expanded: Boolean = false,
)

/**
 * Public transport journeys from Transitous. Contacts api.transitous.org only from [search], which the route panel
 * calls when you pick Transit or change the start, destination or time while it's picked. Sends the two points and
 * the time, nothing else.
 */
class TransitPlanner(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(TransitState())
    val state: StateFlow<TransitState> = _state

    private var job: Job? = null

    fun setTime(time: TransitTime) = _state.update { it.copy(time = time) }

    fun select(index: Int) = _state.update { it.copy(selected = index, expanded = if (it.selected == index) !it.expanded else true) }

    fun collapse() = _state.update { it.copy(expanded = false) }

    fun search(from: LatLon, to: LatLon) {
        job?.cancel()
        val time = _state.value.time
        _state.update { it.copy(status = TransitStatus.Loading, selected = 0, expanded = false) }
        job = scope.launch {
            val status = withContext(Dispatchers.IO) {
                try {
                    val url = Motis.planUrl(from, to, time)
                    val body = withTimeout(35_000) {
                        Http.getJsonCapped(url, Motis.userAgent(BuildConfig.VERSION_NAME), maxBytes = MAX_BYTES, deadlineMs = 30_000)
                    }
                    TransitStatus.Ready(Motis.parsePlan(body))
                } catch (e: Http.HttpStatusException) {
                    TransitStatus.Failed(offline = false, busy = e.code == 429 || e.code == 503)
                } catch (e: IOException) {
                    TransitStatus.Failed(offline = true)
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    TransitStatus.Failed(offline = true)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A malformed answer (or anything else unexpected) is a failed search, never a crash.
                    TransitStatus.Failed(offline = false)
                }
            }
            _state.update { it.copy(status = status) }
        }
    }

    fun clear() {
        job?.cancel()
        _state.update { TransitState(time = it.time) }
    }

    /** Every journey in the order the list shows them. */
    fun journeys(plan: TransitPlan): List<Itinerary> = plan.itineraries + plan.direct

    companion object {
        /** A plan answer is usually under 100 kB; refuse anything far bigger. */
        const val MAX_BYTES = 3 * 1024 * 1024
    }
}
