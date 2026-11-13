package com.vknxworks.mapsctrl.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.Units
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "mapsctrl")

/** Map colours: follow the phone's dark theme, or always day / always night. */
enum class MapStyle(@StringRes val label: Int) {
    SYSTEM(R.string.map_style_system),
    DAY(R.string.map_style_day),
    NIGHT(R.string.map_style_night),
}

data class AppSettings(
    val units: Units = Units.METRIC,
    val mapStyle: MapStyle = MapStyle.SYSTEM,
    val travelMode: TravelMode = TravelMode.CAR,
    val voice: Boolean = true,
    val speedCameras: Boolean = true,
    val speedLimit: Boolean = true,
    /** Download the search data (POI file) together with each map. */
    val withSearchData: Boolean = true,
    /** Share your ETA to an ntfy topic while navigating. Off until you set it up. */
    val ntfyEnabled: Boolean = false,
    val ntfyServer: String = "",
    val ntfyTopic: String = "",
    val keepScreenOn: Boolean = true,
    /** Where the map was last, so it opens there again. */
    val lastLat: Double = 50.8467,
    val lastLon: Double = 4.3525,
    val lastZoom: Float = 4f,
    val hideScreenshots: Boolean = false,
    /** Terrain shading on the map (needs elevation data from Downloads). */
    val hillshading: Boolean = false,
    /** Show the Transit mode (public transport from Transitous) in route planning. */
    val transitEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val units = stringPreferencesKey("units")
        val mapStyle = stringPreferencesKey("map_style")
        val travelMode = stringPreferencesKey("travel_mode")
        val voice = booleanPreferencesKey("voice")
        val speedCameras = booleanPreferencesKey("speed_cameras")
        val speedLimit = booleanPreferencesKey("speed_limit")
        val withSearchData = booleanPreferencesKey("with_search_data")
        val ntfyEnabled = booleanPreferencesKey("ntfy_enabled")
        val ntfyServer = stringPreferencesKey("ntfy_server")
        val ntfyTopic = stringPreferencesKey("ntfy_topic")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val lastLat = doublePreferencesKey("last_lat")
        val lastLon = doublePreferencesKey("last_lon")
        val lastZoom = floatPreferencesKey("last_zoom")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hillshading = booleanPreferencesKey("hillshading")
        val transitEnabled = booleanPreferencesKey("transit_enabled")
    }

    private val prefs: Flow<Preferences> = context.appDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: StateFlow<AppSettings> = prefs
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun loaded(): AppSettings = prefs.first().toSettings()

    private fun Preferences.toSettings() = AppSettings(
        units = enumOr(this[K.units], Units.METRIC),
        mapStyle = enumOr(this[K.mapStyle], MapStyle.SYSTEM),
        travelMode = enumOr(this[K.travelMode], TravelMode.CAR),
        voice = this[K.voice] ?: true,
        speedCameras = this[K.speedCameras] ?: true,
        speedLimit = this[K.speedLimit] ?: true,
        withSearchData = this[K.withSearchData] ?: true,
        ntfyEnabled = this[K.ntfyEnabled] ?: false,
        ntfyServer = this[K.ntfyServer] ?: "",
        ntfyTopic = this[K.ntfyTopic] ?: "",
        keepScreenOn = this[K.keepScreenOn] ?: true,
        lastLat = this[K.lastLat] ?: 50.8467,
        lastLon = this[K.lastLon] ?: 4.3525,
        lastZoom = this[K.lastZoom] ?: 4f,
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hillshading = this[K.hillshading] ?: false,
        transitEnabled = this[K.transitEnabled] ?: true,
    )

    /** Saves a change. If the phone's storage fails, the change is dropped instead of closing the app. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            context.appDataStore.edit { p ->
                val s = transform(p.toSettings())
                p[K.units] = s.units.name
                p[K.mapStyle] = s.mapStyle.name
                p[K.travelMode] = s.travelMode.name
                p[K.voice] = s.voice
                p[K.speedCameras] = s.speedCameras
                p[K.speedLimit] = s.speedLimit
                p[K.withSearchData] = s.withSearchData
                p[K.ntfyEnabled] = s.ntfyEnabled
                p[K.ntfyServer] = s.ntfyServer
                p[K.ntfyTopic] = s.ntfyTopic
                p[K.keepScreenOn] = s.keepScreenOn
                p[K.lastLat] = s.lastLat
                p[K.lastLon] = s.lastLon
                p[K.lastZoom] = s.lastZoom
                p[K.hideScreenshots] = s.hideScreenshots
                p[K.hillshading] = s.hillshading
                p[K.transitEnabled] = s.transitEnabled
            }
        } catch (_: IOException) {
        }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
