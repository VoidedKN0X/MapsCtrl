package com.vknxworks.mapsctrl

import android.content.Context
import com.vknxworks.mapsctrl.data.DownloadService
import com.vknxworks.mapsctrl.data.DownloadState
import com.vknxworks.mapsctrl.data.MapRepository
import com.vknxworks.mapsctrl.data.SegmentRepository
import com.vknxworks.mapsctrl.data.TerrainJob
import com.vknxworks.mapsctrl.data.TerrainRepository
import com.vknxworks.mapsctrl.data.lock.LockVault
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.ui.lock.AppLock
import com.vknxworks.mapsctrl.data.crypto.DataKeyStore
import com.vknxworks.mapsctrl.data.crypto.KeystoreWrapper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)
    val maps = MapRepository(context, appScope)
    val segments = SegmentRepository(context, appScope)
    val search = SearchRepository(maps)
    /** The key that encrypts the saved places and tracks at rest; wrapped by a Keystore key, see data/crypto. */
    val dataKeys = DataKeyStore(context.filesDir, KeystoreWrapper(DATA_KEY_ALIAS))
    val saved = SavedRepository(context, appScope, dataKeys)
    val location = LocationSource(context)
    val transit = TransitPlanner(appScope)
    val terrain = TerrainRepository(context, appScope)
    val planner = RoutePlanner(appScope, segments, mapFiles = { maps.mapFiles() }, transit = transit, avoid = { settings.settings.value.avoid }) { location.fix.value?.point }
    val navigation = NavigationSession(context, appScope, location, planner, search, settings)
    val recorder = TrackRecorder(context, appScope, location, saved)

    /** A "geo:" link another app opened MapsCtrl with, waiting for the map screen. */
    val pendingGeo = MutableStateFlow<String?>(null)

    /** The bottom-bar tab to show (set from other screens, e.g. "Download maps"). */
    val openTab = MutableStateFlow<Int?>(null)

    /** The app lock's PIN and fingerprint key (the shared Ctrl series lock). It covers the screens only. */
    val vault = LockVault(context, "mapsctrl")

    suspend fun enableLock(pin: String) {
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
    }

    fun disableLock() {
        vault.disable()
    }

    /**
     * "Forgot PIN?": the PIN can't be recovered. Your saved places, tracks and recent searches are deleted and
     * MapsCtrl's settings reset (the ETA sharing server and topic included), and the lock is turned off. The
     * downloaded maps, search, routing and terrain data stay. A navigation that's running goes on.
     */
    suspend fun forgetEverything() {
        saved.deleteAll()
        settings.update { AppSettings() }
        vault.disable()
        AppLock.markUnlocked()
    }

    fun onLocked() {
        vault.close()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }

    /** Any map, search, routing or terrain download is running. */
    private val downloading = combine(
        maps.downloads.map { d -> d.values.any { it is DownloadState.Running } },
        segments.active,
        terrain.job.map { it is TerrainJob.Running },
    ) { a, b, c -> a || b || c }.distinctUntilChanged()

    init {
        AppLock.onLocked = { onLocked() }
        // A "Downloading maps" foreground service while a download runs, so leaving the app doesn't stop it.
        // It stops a moment after the last download ends (not between two files of the same download).
        appScope.launch {
            downloading.collectLatest { active ->
                if (active) {
                    DownloadService.start(context)
                } else {
                    delay(DOWNLOAD_SERVICE_LINGER_MS)
                    DownloadService.stop(context)
                }
            }
        }
    }

    private companion object {
        const val DATA_KEY_ALIAS = "mapsctrl_data_at_rest"
        const val DOWNLOAD_SERVICE_LINGER_MS = 1_500L
    }
}

val Context.container: AppContainer
    get() = (applicationContext as MapsCtrlApp).container
