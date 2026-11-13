package com.vknxworks.mapsctrl

import android.content.Context
import com.vknxworks.mapsctrl.data.MapRepository
import com.vknxworks.mapsctrl.data.SegmentRepository
import com.vknxworks.mapsctrl.data.TerrainRepository
import com.vknxworks.mapsctrl.transit.TransitPlanner
import com.vknxworks.mapsctrl.location.LocationSource
import com.vknxworks.mapsctrl.nav.NavigationSession
import com.vknxworks.mapsctrl.nav.RoutePlanner
import com.vknxworks.mapsctrl.saved.SavedRepository
import com.vknxworks.mapsctrl.saved.TrackRecorder
import com.vknxworks.mapsctrl.search.SearchRepository
import com.vknxworks.mapsctrl.settings.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)
    val maps = MapRepository(context, appScope)
    val segments = SegmentRepository(context, appScope)
    val search = SearchRepository(maps)
    val saved = SavedRepository(context, appScope)
    val location = LocationSource(context)
    val transit = TransitPlanner(appScope)
    val terrain = TerrainRepository(context, appScope)
    val planner = RoutePlanner(appScope, segments, mapFiles = { maps.mapFiles() }, transit = transit) { location.fix.value?.point }
    val navigation = NavigationSession(context, appScope, location, planner, search, settings)
    val recorder = TrackRecorder(context, appScope, location, saved)

    /** A "geo:" link another app opened MapsCtrl with, waiting for the map screen. */
    val pendingGeo = MutableStateFlow<String?>(null)

    /** The bottom-bar tab to show (set from other screens, e.g. "Download maps"). */
    val openTab = MutableStateFlow<Int?>(null)
}

val Context.container: AppContainer
    get() = (applicationContext as MapsCtrlApp).container
