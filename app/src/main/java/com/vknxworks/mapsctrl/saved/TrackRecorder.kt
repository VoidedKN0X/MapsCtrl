package com.vknxworks.mapsctrl.saved

import android.content.Context
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.location.LocationSource
import com.vknxworks.mapsctrl.nav.NavigationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Records your track while it's on; saving it puts it in Saved. Needs the location permission. */
class TrackRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val location: LocationSource,
    private val saved: SavedRepository,
) {
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _points = MutableStateFlow<List<LatLon>>(emptyList())
    val points: StateFlow<List<LatLon>> = _points
    private val times = ArrayList<Long>()
    private var job: Job? = null
    private var startedAt = 0L

    fun start(): Boolean {
        if (_recording.value) return true
        if (!location.hasPermission()) return false
        _points.value = emptyList()
        times.clear()
        startedAt = System.currentTimeMillis()
        _recording.value = true
        location.start("track")
        NavigationService.start(context)
        job = scope.launch {
            location.fix.collect { f ->
                if (f == null || f.accuracy > 40f) return@collect
                val last = _points.value.lastOrNull()
                // Every 5 m at most, to keep tracks small.
                if (last == null || Geo.distance(last, f.point) >= 5.0) {
                    _points.value = _points.value + f.point
                    times += f.time
                }
            }
        }
        return true
    }

    /** Stops recording and saves the track (when it has at least two points). */
    fun stop(save: Boolean = true) {
        job?.cancel()
        _recording.value = false
        location.stop("track")
        NavigationService.stop(context)
        val pts = _points.value
        if (save && pts.size >= 2) {
            val name = context.getString(R.string.track_default_name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startedAt)))
            val t = SavedTrack(name = name, segments = listOf(pts), times = listOf(times.toList()))
            scope.launch { saved.addTrack(t) }
        }
        _points.value = emptyList()
    }
}
