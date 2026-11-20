package com.vknxworks.mapsctrl.ui.home

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.geo.Coordinates
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.location.HeadingGate
import com.vknxworks.mapsctrl.map.CtrlRenderTheme
import com.vknxworks.mapsctrl.map.MapController
import com.vknxworks.mapsctrl.map.OpenedMaps
import com.vknxworks.mapsctrl.nav.PlanStatus
import com.vknxworks.mapsctrl.nav.RouteStart
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.search.SearchResult
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.settings.MapStyle
import com.vknxworks.mapsctrl.transit.TransitStatus
import com.vknxworks.mapsctrl.ui.components.CtrlBottomBar
import com.vknxworks.mapsctrl.ui.components.LocalBottomClearance
import com.vknxworks.mapsctrl.ui.components.TabItem
import com.vknxworks.mapsctrl.ui.downloads.DownloadsScreen
import com.vknxworks.mapsctrl.ui.more.MoreScreen
import com.vknxworks.mapsctrl.ui.saved.SavedScreen
import com.vknxworks.mapsctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class Follow { OFF, FOLLOW, COMPASS }

/** What the home screen is showing, besides the map. */
@Stable
class HomeState {
    var tab by mutableIntStateOf(0)
    var selected by mutableStateOf<Place?>(null)
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    var follow by mutableStateOf(Follow.OFF)
    /** Next tap on the map sets the route's start. */
    var pickStart by mutableStateOf(false)
    /** While navigating: you moved the map yourself, so it stops following until "Recenter". */
    var navPanned by mutableStateOf(false)
}

const val TAB_MAP = 0
const val TAB_SAVED = 1
const val TAB_DOWNLOADS = 2
const val TAB_MORE = 3

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val c = context.container
    // Wait for the saved settings once, so the map opens where you left it; and for the lists of downloaded maps
    // and elevation tiles and the map style files, so the map is set up once, with all of them, instead of being
    // rebuilt as each one arrives.
    val initial by produceState<HomeStart?>(null) {
        c.maps.ready.first { it }
        c.terrain.ready.first { it }
        withContext(Dispatchers.IO) { runCatching { CtrlRenderTheme.prepare(context.applicationContext) } }
        val loaded = c.settings.loaded()
        // The settings the screens observe catch up with the saved ones a moment later; wait for that too (briefly),
        // so the map isn't set up with the saved style and then reset to the defaults for one frame.
        withTimeoutOrNull(1_000) { c.settings.settings.first { it == loaded } }
        // The downloaded maps are opened here, off the main thread, and handed to the map as it's built.
        val installed = c.maps.installed.value
        val maps = withContext(Dispatchers.IO) {
            runCatching { OpenedMaps.open(installed.map { c.maps.mapFile(it.path) }.filter { it.exists() }) }.getOrNull()
        }
        value = HomeStart(loaded, maps)
    }
    val start = initial ?: return
    HomeContent(start.settings, start.maps)
}

/** What the map screen starts with: the saved settings, and the downloaded maps already opened (null: the map opens them itself). */
private class HomeStart(val settings: AppSettings, val maps: OpenedMaps?)

@Composable
private fun HomeContent(start: AppSettings, openedMaps: OpenedMaps?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val state = remember { HomeState() }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val plan by c.planner.state.collectAsStateWithLifecycle()
    val nav by c.navigation.state.collectAsStateWithLifecycle()
    // Read only where it's needed (never in this composable's own body), so the once-a-second position updates
    // don't recompose the whole home screen; see PositionFeed.
    val fixState = c.location.fix.collectAsStateWithLifecycle()
    val hasFix by remember { derivedStateOf { fixState.value != null } }
    val installed by c.maps.installed.collectAsStateWithLifecycle()
    val savedPlaces by c.saved.places.collectAsStateWithLifecycle()
    val tracks by c.saved.tracks.collectAsStateWithLifecycle()
    val recordingPts by c.recorder.points.collectAsStateWithLifecycle()
    val pendingGeo by c.pendingGeo.collectAsStateWithLifecycle()
    val openTab by c.openTab.collectAsStateWithLifecycle()

    val dark = isSystemInDarkTheme()
    val night = when (settings.mapStyle) { MapStyle.SYSTEM -> dark; MapStyle.DAY -> false; MapStyle.NIGHT -> true }

    val controller = remember {
        // With what's known at start (HomeScreen waited for it), so the map renderer is built exactly once.
        val dem = c.terrain.files.value
        MapController(
            context, LatLon(start.lastLat, start.lastLon), start.lastZoom,
            initialNight = when (start.mapStyle) { MapStyle.SYSTEM -> dark; MapStyle.DAY -> false; MapStyle.NIGHT -> true },
            initialMaps = openedMaps?.files ?: c.maps.installed.value.map { c.maps.mapFile(it.path) }.filter { it.exists() },
            opened = openedMaps,
            initialDemDir = if (start.hillshading && dem.isNotEmpty()) c.terrain.dir else null,
            initialDemVersion = dem.keys.hashCode(),
        )
    }
    // The camera changes on every frame of a pan, zoom or turn: it's read where it's needed (MapOverlay collects
    // it itself), not here, so moving the map doesn't recompose the whole home screen.
    DisposableEffect(controller) { onDispose { controller.destroy() } }

    // ---- Location ----
    var hasPermission by remember { mutableStateOf(c.location.hasPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasPermission = r.values.any { it }
        if (hasPermission) {
            c.location.restart()
            c.location.start("map")
            state.follow = Follow.FOLLOW
        }
    }
    // The map's own use of your position follows the screen: it stops when MapsCtrl leaves the screen and starts
    // again when it's back. Navigation and track recording ask for the position themselves and keep running.
    LifecycleStartEffect(hasPermission) {
        if (hasPermission) c.location.start("map")
        onStopOrDispose { c.location.stop("map") }
    }

    fun askLocation() = permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    // ---- Feed the map ----
    LaunchedEffect(installed) { controller.setMaps(installed.map { c.maps.mapFile(it.path) }.filter { it.exists() }) }
    LaunchedEffect(night) { controller.setNight(night); controller.setNightOverlay(night) }
    val route = nav?.route ?: (plan.status as? PlanStatus.Ready)?.route?.takeIf { !plan.transit }
    LaunchedEffect(route, night) { controller.setRoute(route) }
    // Hillshading: on in the layers sheet, with elevation tiles on the phone.
    val demTiles by c.terrain.files.collectAsStateWithLifecycle()
    LaunchedEffect(settings.hillshading, demTiles) {
        controller.setHillshading(if (settings.hillshading && demTiles.isNotEmpty()) c.terrain.dir else null, demTiles.keys.hashCode())
    }
    // Public transport: the journey you picked, drawn leg by leg.
    val transit by c.transit.state.collectAsStateWithLifecycle()
    val journey = if (plan.transit && plan.destination != null && nav == null) {
        (transit.status as? TransitStatus.Ready)?.plan?.let { c.transit.journeys(it).getOrNull(transit.selected) }
    } else null
    LaunchedEffect(journey, night) {
        controller.setTransit(journey?.legs)
        // Between the planning card on top and the journeys at the bottom.
        if (journey != null) controller.fit(journey.legs.flatMap { it.geometry }, top = 0.3, bottom = 0.5)
    }
    LaunchedEffect(tracks, night) { controller.setTracks(tracks) }
    LaunchedEffect(recordingPts) { controller.setRecording(recordingPts) }
    LaunchedEffect(savedPlaces) { controller.setSaved(savedPlaces) }
    LaunchedEffect(state.selected) { controller.setSelected(state.selected?.point) }
    LaunchedEffect(state.results, state.searchOpen) { controller.setResults(if (state.selected == null) state.results.map { it.place.point } else emptyList()) }
    LaunchedEffect(plan.destination, plan.start, !hasFix) {
        val s = when (val st = plan.start) { RouteStart.MyLocation -> null; is RouteStart.At -> st.place.point }
        controller.setRouteEnds(if (plan.destination != null) s else null, plan.destination?.point)
    }
    // Show a new route whole.
    LaunchedEffect(route) { if (route != null && nav == null) controller.fit(route.points) }

    // Your position and heading on the map, and the map following you.
    PositionFeed(controller, state, navigating = nav != null)
    LaunchedEffect(nav != null) {
        if (nav != null) {
            state.navPanned = false
            controller.moveTo(nav?.route?.points?.firstOrNull() ?: return@LaunchedEffect, 17)
        } else if (controller.camera.value.rotation != 0f) controller.rotate(0f)
    }

    // Remember where the map was.
    LaunchedEffect(controller) {
        controller.camera.drop(1).debounce(1500).collect { cam ->
            c.settings.update { it.copy(lastLat = cam.center.lat, lastLon = cam.center.lon, lastZoom = cam.zoom.toFloat()) }
        }
    }

    // ---- Map input ----
    controller.onUserPan = {
        if (state.follow != Follow.OFF) state.follow = Follow.OFF
        if (nav != null) state.navPanned = true
    }
    controller.onTap = { p, x, y ->
        when {
            state.pickStart -> {
                state.pickStart = false
                c.planner.setStart(RouteStart.At(Place(context.getString(R.string.point_on_map), p)))
            }
            nav != null -> Unit
            else -> {
                val savedHit = controller.savedNear(x, y, savedPlaces)
                if (savedHit != null) {
                    state.selected = savedHit.toPlace()
                } else if (state.selected != null) {
                    state.selected = null
                } else {
                    scope.launch {
                        val radius = (controller.camera.value.metersPerPixel * 28 * context.resources.displayMetrics.density).coerceIn(5.0, 400.0)
                        c.search.nearest(p, radius)?.let { state.selected = it }
                    }
                }
            }
        }
    }
    controller.onLongPress = { p ->
        if (nav == null && !plan.destinationSet()) state.selected = Place(context.getString(R.string.dropped_pin), p)
    }

    // ---- Links from other apps ----
    LaunchedEffect(pendingGeo) {
        val uri = pendingGeo ?: return@LaunchedEffect
        c.pendingGeo.value = null
        state.tab = TAB_MAP
        val p = Coordinates.parse(uri)
        val q = Coordinates.geoUriQuery(uri)
        when {
            p != null && !(p.lat == 0.0 && p.lon == 0.0) -> {
                val label = uri.substringAfter('(', "").substringBefore(')').takeIf { it.isNotBlank() }
                state.selected = Place(label ?: p.format(), p)
                controller.moveTo(p, 16, lift = 0.22)
            }
            q != null -> { state.query = q; state.searchOpen = true }
        }
    }
    LaunchedEffect(openTab) { openTab?.let { state.tab = it; c.openTab.value = null } }

    BackHandler(state.searchOpen || state.selected != null || state.pickStart || plan.destination != null || state.tab != TAB_MAP) {
        when {
            state.searchOpen -> state.searchOpen = false
            state.pickStart -> state.pickStart = false
            plan.destination != null && nav == null -> c.planner.close()
            state.selected != null -> state.selected = null
            state.tab != TAB_MAP -> state.tab = TAB_MAP
        }
    }

    val tabs = listOf(
        TabItem(stringResource(R.string.tab_map), Icons.Outlined.Map, Icons.Filled.Map),
        TabItem(stringResource(R.string.tab_saved), Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
        TabItem(stringResource(R.string.tab_downloads), Icons.Outlined.Download, Icons.Filled.Download),
        TabItem(stringResource(R.string.tab_more), Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
    )
    val planning = plan.destination != null && nav == null
    val showBar = nav == null && !planning && !state.searchOpen

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(factory = { controller.view }, modifier = Modifier.fillMaxSize())

        CompositionLocalProvider(LocalBottomClearance provides if (showBar) 88.dp else 0.dp) {
            when {
                nav != null -> NavigationOverlay(
                    state = nav!!,
                    settings = settings,
                    panned = state.navPanned,
                    onRecenter = { state.navPanned = false },
                    onZoomIn = controller::zoomIn,
                    onZoomOut = controller::zoomOut,
                )
                planning -> RoutePanel(
                    plan = plan,
                    settings = settings,
                    hasLocation = hasFix,
                    hasPermission = hasPermission,
                    pickingStart = state.pickStart,
                    onPickStart = { state.pickStart = true },
                    onAskLocation = ::askLocation,
                    onStart = { demo ->
                        val r = (plan.status as? PlanStatus.Ready)?.route ?: return@RoutePanel
                        val dest = plan.destination ?: return@RoutePanel
                        if (!demo && !hasPermission) { askLocation(); return@RoutePanel }
                        c.navigation.start(r, dest, demo)
                        c.planner.close()
                        state.selected = null
                    },
                )
                state.tab == TAB_MAP -> MapOverlay(
                    state = state,
                    controller = controller,
                    hasMaps = installed.isNotEmpty(),
                    hasPermission = hasPermission,
                    hasFix = hasFix,
                    onAskLocation = ::askLocation,
                    onRoute = { place -> c.planner.open(place, settings.travelMode) },
                )
            }
            if (nav == null && !planning) {
                when (state.tab) {
                    TAB_SAVED -> TabPage { SavedScreen(onShow = { p -> state.tab = TAB_MAP; state.selected = p; controller.moveTo(p.point, 16, lift = 0.22) }, onShowTrack = { pts -> state.tab = TAB_MAP; controller.fit(pts) }) }
                    TAB_DOWNLOADS -> TabPage { DownloadsScreen() }
                    TAB_MORE -> TabPage { MoreScreen(onGoTo = { p -> state.tab = TAB_MAP; state.selected = p; controller.moveTo(p.point, 16, lift = 0.22) }) }
                }
            }
            if (state.searchOpen) {
                SearchScreen(
                    state = state,
                    // Read here, so it's only followed while search is open.
                    near = fixState.value?.point ?: controller.camera.value.center,
                    onPick = { place ->
                        state.searchOpen = false
                        state.tab = TAB_MAP
                        state.selected = place
                        controller.moveTo(place.point, 16, lift = 0.22)
                    },
                    onShowAll = {
                        state.searchOpen = false
                        state.tab = TAB_MAP
                        controller.fit(state.results.map { it.place.point })
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CtrlBottomBar(
                items = tabs,
                selectedIndex = state.tab,
                onSelect = { state.tab = it },
                modifier = Modifier.navigationBarsPadding().padding(start = Gutter, end = Gutter, bottom = 12.dp),
            )
        }
    }
}

private fun com.vknxworks.mapsctrl.nav.PlanState.destinationSet() = destination != null

/**
 * Feeds your position and heading to the map and keeps the map following you (while navigating, heading up).
 * It draws nothing; it's its own composable so the position (once a second) and the compass (five times a
 * second) only recompose this, not the whole home screen.
 */
@Composable
private fun PositionFeed(controller: MapController, state: HomeState, navigating: Boolean) {
    val c = LocalContext.current.container
    val fix by c.location.fix.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasCompass = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }
    // The compass is for standing still and going slowly; while you ride faster the GPS says where you're heading
    // and the sensor is switched off (see HeadingGate, which also keeps the map from turning north when you brake).
    val wanted = state.follow == Follow.COMPASS || navigating
    val gate = remember { HeadingGate() }
    val compass = rememberCompass(gate.sensorNeeded(wanted, hasCompass, fix?.speed, fix?.bearing))
    val heading: Float? = gate.heading(fix != null, wanted, hasCompass, fix?.speed, fix?.bearing, compass)
    LaunchedEffect(fix, heading) { controller.setFix(fix, heading) }
    // Follow your position; while navigating, heading up.
    LaunchedEffect(fix, state.follow, navigating, state.navPanned, heading) {
        val f = fix ?: return@LaunchedEffect
        if (navigating && !state.navPanned) {
            controller.moveTo(f.point, animate = false)
            controller.rotate(heading ?: 0f)
        } else if (state.follow != Follow.OFF && !navigating) {
            controller.moveTo(f.point)
            if (state.follow == Follow.COMPASS) controller.rotate(heading ?: 0f)
        }
    }
}

/** A tab page drawn over the map. */
@Composable
private fun TabPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}

/** The phone's compass heading, while [enabled]. */
@Composable
private fun rememberCompass(enabled: Boolean): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sensor == null) {
            heading = null
            return@DisposableEffect onDispose { }
        }
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = System.currentTimeMillis()
                if (now - last < 200) return
                last = now
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                heading = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}
