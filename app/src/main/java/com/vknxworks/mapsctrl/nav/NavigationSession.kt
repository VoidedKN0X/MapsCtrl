package com.vknxworks.mapsctrl.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.geo.ElevationProfile
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.location.Fix
import com.vknxworks.mapsctrl.location.LocationSource
import com.vknxworks.mapsctrl.routing.Instructions
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.TurnKind
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.saved.SavedTrack
import com.vknxworks.mapsctrl.search.SearchRepository
import com.vknxworks.mapsctrl.settings.SettingsRepository
import com.vknxworks.mapsctrl.share.Ntfy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class NavState(
    val route: Route,
    val destination: Place,
    val progress: Progress? = null,
    val fix: Fix? = null,
    val rerouting: Boolean = false,
    val muted: Boolean = false,
    val waitingForLocation: Boolean = true,
    val demo: Boolean = false,
    /** The stops still ahead, in order (a stop is dropped from the list when you reach it). */
    val stops: List<Place> = emptyList(),
    /** Following a saved track: no turn directions and no rerouting, statistics instead. */
    val follow: Boolean = false,
    val stats: TripStats? = null,
    /** The track's (or route's) elevation profile, for the climb still ahead. */
    val elevation: ElevationProfile? = null,
    /** You're going faster than allowed (only with the over-speed alert on). */
    val overSpeed: Boolean = false,
)

/**
 * Turn-by-turn navigation: follows your position on the route, speaks the turns with the phone's own
 * text-to-speech (offline voices), reroutes when you leave the route, warns about speed cameras and shares the
 * ETA to ntfy when you've turned that on.
 */
class NavigationSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val location: LocationSource,
    private val planner: RoutePlanner,
    private val search: SearchRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state

    private var tracker: NavigationTracker? = null
    private var follow: Job? = null
    private var demoJob: Job? = null
    private var ntfyJob: Job? = null
    private var lastReroute = 0L
    private val warnedCameras = HashSet<Long>()
    /** Where each stop still ahead is along the current route (same order as [NavState.stops]). */
    private var stopAlongs: List<Double> = emptyList()
    private var overSpeed: OverSpeed? = null
    private var overSpeedMargin = -1
    /** While following a track: you're off it (said once each time you leave it). */
    private var offTrack = false
    private val words = ResourceWords(context)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(navAttributes())
        .build()

    private fun navAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun start(
        route: Route,
        destination: Place,
        demo: Boolean = false,
        stops: List<Place> = emptyList(),
        follow: Boolean = false,
        elevation: ElevationProfile? = ElevationProfile.of(route.points, route.elevations),
    ) {
        end(releaseVoice = false)
        tracker = NavigationTracker(route)
        warnedCameras.clear()
        stopAlongs = Stops.alongs(route, stops.map { it.point })
        overSpeed = null
        offTrack = false
        _state.value = NavState(
            route, destination, demo = demo, stops = stops, follow = follow,
            stats = TripStats(System.currentTimeMillis()), elevation = elevation,
        )
        initTts()
        location.start("nav")
        loadCameras(route)
        NavigationService.start(context)
        follow = scope.launch {
            location.fix.collect { fix -> if (fix != null) onFix(fix) }
        }
        if (demo) startDemo(route)
        startNtfy()
    }

    fun stop() = end(releaseVoice = true)

    /**
     * Follows a saved track (an imported GPX track or one you recorded): the track is shown like a route, with
     * the distance and estimated time left, your trip statistics and the climb ahead. There are no turn
     * directions and no rerouting; leaving the track is said once and shown until you're back on it.
     */
    fun followTrack(track: SavedTrack, mode: TravelMode, demo: Boolean = false) {
        val points = track.segments.flatten()
        if (points.size < 2) return
        val heights = track.flatElevations()
        val length = track.lengthMeters
        val route = Route(
            mode = mode,
            points = points,
            times = emptyList(),
            distanceMeters = length,
            durationSeconds = length / followSpeed(mode),
            hints = emptyList(),
            sections = emptyList(),
            elevations = if (heights.isNotEmpty() && heights.all { it != null }) heights.map { it!! } else emptyList(),
        )
        start(route, Place(track.name, points.last()), demo = demo, follow = true, elevation = ElevationProfile.of(points, heights))
    }

    /** Average speeds for the time left on a track, in m/s: 50, 25, 16 and 4.5 km/h. */
    private fun followSpeed(mode: TravelMode): Double = when (mode) {
        TravelMode.CAR -> 13.9
        TravelMode.MOPED -> 6.9
        TravelMode.BIKE -> 4.4
        TravelMode.FOOT -> 1.25
    }

    /** Ends navigation. [releaseVoice]: also let go of the text-to-speech engine (not when a new trip starts right away). */
    private fun end(releaseVoice: Boolean) {
        follow?.cancel()
        demoJob?.cancel()
        ntfyJob?.cancel()
        follow = null
        val was = _state.value
        _state.value = null
        tracker = null
        if (was?.demo == true) location.simulate(null)
        if (was != null) {
            location.stop("nav")
            NavigationService.stop(context)
        }
        tts?.stop()
        // The voice engine was kept connected for as long as the app lived after one trip; it's released when the
        // trip ends and connected again at the next start (or voice test).
        if (releaseVoice && was != null) releaseTts()
    }

    private fun releaseTts() {
        tts?.shutdown()
        tts = null
        ttsReady = false
        // Nothing will say "done" for a sentence that was cut off: give the audio focus back here.
        audio.abandonAudioFocusRequest(focus)
    }

    fun toggleMute() = _state.update { it?.copy(muted = !it.muted) }

    /** Says a test sentence (Settings). */
    fun testVoice() {
        initTts()
        scope.launch {
            repeat(20) { if (!ttsReady) delay(150) }
            speak(context.getString(R.string.voice_test_sentence), force = true)
        }
    }

    private fun onFix(fix: Fix) {
        val t = tracker ?: return
        val p = t.update(fix.point, fix.accuracy)
        val before = _state.value ?: return
        val cfg = settings.settings.value
        val over = checkOverSpeed(fix, p, before, cfg)
        val s = before.copy(
            progress = p, fix = fix, waitingForLocation = false,
            stats = before.stats?.add(fix.point, fix.accuracy, fix.speed, fix.time),
            overSpeed = over,
        )
        _state.value = s

        if (p.arrived) {
            if (before.progress?.arrived != true) {
                speak(words.action(TurnKind.ARRIVE, 0).replaceFirstChar { it.uppercase() })
                sendNtfy(arrived = true)
            }
            return
        }
        // A stop you've reached is said once and dropped, so a reroute no longer goes back to it.
        if (s.stops.isNotEmpty() && stopAlongs.size == s.stops.size) {
            val along = (t.route.cumulative.lastOrNull() ?: 0.0) - p.distanceLeft
            val ahead = Stops.remaining(stopAlongs, along).toSet()
            if (ahead.size < s.stops.size) {
                s.stops.filterIndexed { i, _ -> i !in ahead }.lastOrNull()?.let { speak(context.getString(R.string.voice_stop_reached, it.name)) }
                _state.value = _state.value?.copy(stops = s.stops.filterIndexed { i, _ -> i in ahead })
                stopAlongs = stopAlongs.filterIndexed { i, _ -> i in ahead }
            }
        }
        if (s.follow) {
            // A track isn't rerouted: say once that you've left it, and once that you're back.
            if (p.offRoute && !offTrack) { offTrack = true; speak(context.getString(R.string.voice_off_track)) }
            if (!p.offRoute && offTrack && p.distanceFromRoute < 20.0) { offTrack = false; speak(context.getString(R.string.voice_back_on_track)) }
        } else if (p.offRoute && !s.rerouting && System.currentTimeMillis() - lastReroute > 8_000) {
            reroute(fix.point)
            return
        }
        t.announcement(p)?.let { a ->
            speak(Instructions.spoken(words, a.hint, a.distance, cfg.units, a.following, a.gap))
        }
        val cam = p.cameraAhead
        if (cfg.speedCameras && cam != null) {
            val key = ((p.distanceLeft - cam) / 10).toLong() // the camera's place along the route, in 10 m steps
            if (warnedCameras.add(key)) speak(context.getString(R.string.voice_speed_camera))
        }
    }

    /** Whether you're over the limit now, and the warning (a beep, and the voice when it's on) when it's time for one. */
    private fun checkOverSpeed(fix: Fix, p: Progress, s: NavState, cfg: com.vknxworks.mapsctrl.settings.AppSettings): Boolean {
        if (!cfg.overSpeedAlert) { overSpeed = null; return false }
        if (overSpeed == null || overSpeedMargin != cfg.overSpeedMargin) {
            overSpeed = OverSpeed(cfg.overSpeedMargin)
            overSpeedMargin = cfg.overSpeedMargin
        }
        val o = overSpeed ?: return false
        val warn = o.update(fix.speed?.let { it * 3.6f }, p.speedLimit?.kmh, s.route.mode == TravelMode.MOPED, fix.time)
        if (warn && !s.muted) {
            beep()
            speak(context.getString(R.string.voice_over_speed))
        }
        return o.over
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            scope.launch { delay(600); tone.release() }
        }
    }

    private fun reroute(from: LatLon) {
        val s = _state.value ?: return
        lastReroute = System.currentTimeMillis()
        _state.value = s.copy(rerouting = true)
        speak(context.getString(R.string.voice_rerouting))
        scope.launch {
            val stops = s.stops
            val r = planner.reroute(from, s.destination.point, s.route.mode, stops.map { it.point })
            val cur = _state.value ?: return@launch
            if (r != null) {
                tracker = NavigationTracker(r)
                // The stops still ahead at the moment of the reroute are on the new route.
                val left = cur.stops.filter { it in stops }
                stopAlongs = Stops.alongs(r, left.map { it.point })
                loadCameras(r)
                _state.value = cur.copy(route = r, rerouting = false, progress = null, stops = left)
                if (cur.demo) startDemo(r)
            } else {
                _state.value = cur.copy(rerouting = false)
            }
            lastReroute = System.currentTimeMillis()
        }
    }

    /** Adds speed cameras from the POI files along the route to the tracker. */
    private fun loadCameras(route: Route) {
        if (route.points.isEmpty()) return
        scope.launch {
            val pad = 0.01
            val cams = search.speedCameras(
                route.points.minOf { it.lat } - pad, route.points.minOf { it.lon } - pad,
                route.points.maxOf { it.lat } + pad, route.points.maxOf { it.lon } + pad,
            )
            if (cams.isNotEmpty() && _state.value?.route === route) {
                // Matching every camera to the route is work for a background thread, not the main one.
                val withCameras = withContext(Dispatchers.Default) { NavigationTracker(route, cams) }
                if (_state.value?.route === route) tracker = withCameras
            }
        }
    }

    // ---------- Voice ----------

    private fun initTts() {
        if (tts != null) return
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            // Only for the engine that's in use now: one that was released before it was ready is ignored.
            if (tts === engine) {
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.setAudioAttributes(navAttributes())
                    val lang = tts?.isLanguageAvailable(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    tts?.language = if (lang >= TextToSpeech.LANG_AVAILABLE) Locale.getDefault() else Locale.ENGLISH
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
                    })
                }
            }
        }
        tts = engine
    }

    private fun speak(text: String, force: Boolean = false) {
        val s = _state.value
        if (!force && (s == null || s.muted || !settings.settings.value.voice)) return
        val t = tts ?: return
        if (!ttsReady) return
        audio.requestAudioFocus(focus)
        t.speak(text, TextToSpeech.QUEUE_ADD, null, "nav-" + System.nanoTime())
    }

    fun shutdown() {
        end(releaseVoice = false)
        releaseTts()
    }

    // ---------- ETA sharing ----------

    private fun startNtfy() {
        val cfg = settings.settings.value
        if (!cfg.ntfyEnabled || Ntfy.topicUrl(cfg.ntfyServer, cfg.ntfyTopic) == null) return
        ntfyJob = scope.launch {
            // Once at the start (after the first position), then every 5 minutes.
            repeat(60) { if (_state.value?.progress == null) delay(1000) }
            while (isActive && _state.value?.progress?.arrived == false) {
                sendNtfy(arrived = false)
                delay(5 * 60_000L)
            }
        }
    }

    private fun sendNtfy(arrived: Boolean) {
        val cfg = settings.settings.value
        if (!cfg.ntfyEnabled) return
        val s = _state.value ?: return
        val p = s.progress ?: return
        val eta = Date(System.currentTimeMillis() + (p.timeLeftSeconds * 1000).toLong())
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(eta)
        val title = context.getString(R.string.ntfy_title, s.destination.name)
        val msg = if (arrived) context.getString(R.string.ntfy_arrived, s.destination.name)
        else context.getString(R.string.ntfy_eta, time, Instructions.displayDistance(words, p.distanceLeft, cfg.units))
        scope.launch { Ntfy.send(cfg.ntfyServer, cfg.ntfyTopic, title, msg) }
    }

    // ---------- Demo drive (debug builds) ----------

    /** Moves a simulated position along the route, for testing navigation without the location permission. */
    private fun startDemo(route: Route) {
        demoJob?.cancel()
        val speed = when (route.mode) { TravelMode.CAR -> 14.0; TravelMode.MOPED -> 6.9; TravelMode.BIKE -> 5.5; TravelMode.FOOT -> 1.6 }
        demoJob = scope.launch {
            val cum = route.cumulative
            val total = cum.lastOrNull() ?: return@launch
            var d = 0.0
            var i = 0
            while (isActive && d <= total) {
                while (i < cum.size - 2 && cum[i + 1] < d) i++
                val a = route.points[i]
                val b = route.points[(i + 1).coerceAtMost(route.points.lastIndex)]
                val seg = (cum[(i + 1).coerceAtMost(cum.lastIndex)] - cum[i]).coerceAtLeast(0.001)
                val f = ((d - cum[i]) / seg).coerceIn(0.0, 1.0)
                val pt = LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
                location.simulate(Fix(pt, 5f, Geo.bearing(a, b).toFloat(), speed.toFloat(), System.currentTimeMillis()))
                delay(1000)
                d += speed
            }
        }
    }
}
