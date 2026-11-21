package com.vknxworks.mapsctrl.settings

import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.Units
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A settings file: MapsCtrl's settings as JSON, to keep or to move to another phone. Pure logic, unit-tested.
 *
 * Left out on purpose: the ntfy topic (anyone who knows it can read and post to it, so it's treated like a
 * password), and where the map was last (your position). Saved places, tracks, recent searches and downloaded
 * maps are not settings and are not in it (places and tracks go to GPX in Saved).
 */
@Serializable
data class SettingsBackup(
    /** Always "MapsCtrl" in a real file; empty when missing, so any other JSON is refused. */
    val app: String = "",
    val format: Int = 0,
    val appVersion: String = "",
    val createdAt: String? = null,
    val settings: BackupSettings = BackupSettings(),
) {
    companion object {
        const val APP = "MapsCtrl"
        const val FORMAT = 1
    }
}

/** Every field is optional: a missing one leaves that setting as it is. */
@Serializable
data class BackupSettings(
    val units: String? = null,
    val mapStyle: String? = null,
    val travelMode: String? = null,
    val voice: Boolean? = null,
    val speedCameras: Boolean? = null,
    val speedLimit: Boolean? = null,
    val withSearchData: Boolean? = null,
    val ntfyEnabled: Boolean? = null,
    val ntfyServer: String? = null,
    val keepScreenOn: Boolean? = null,
    val hideScreenshots: Boolean? = null,
    val hillshading: Boolean? = null,
    val transitEnabled: Boolean? = null,
    val avoidTolls: Boolean? = null,
    val avoidMotorways: Boolean? = null,
    val avoidUnpaved: Boolean? = null,
    val avoidFerries: Boolean? = null,
    val overSpeedAlert: Boolean? = null,
    val overSpeedMargin: Int? = null,
    val keepRecentSearches: Boolean? = null,
)

class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun export(s: AppSettings, appVersion: String, createdAt: String): String = json.encodeToString(
        SettingsBackup.serializer(),
        SettingsBackup(
            app = SettingsBackup.APP,
            format = SettingsBackup.FORMAT,
            appVersion = appVersion,
            createdAt = createdAt,
            settings = BackupSettings(
                units = s.units.name,
                mapStyle = s.mapStyle.name,
                travelMode = s.travelMode.name,
                voice = s.voice,
                speedCameras = s.speedCameras,
                speedLimit = s.speedLimit,
                withSearchData = s.withSearchData,
                ntfyEnabled = s.ntfyEnabled,
                ntfyServer = s.ntfyServer,
                keepScreenOn = s.keepScreenOn,
                hideScreenshots = s.hideScreenshots,
                hillshading = s.hillshading,
                transitEnabled = s.transitEnabled,
                avoidTolls = s.avoidTolls,
                avoidMotorways = s.avoidMotorways,
                avoidUnpaved = s.avoidUnpaved,
                avoidFerries = s.avoidFerries,
                overSpeedAlert = s.overSpeedAlert,
                overSpeedMargin = s.overSpeedMargin,
                keepRecentSearches = s.keepRecentSearches,
            ),
        ),
    )

    /** Reads a settings file; throws [BackupFormatException] when it isn't one (or is from a newer MapsCtrl). */
    fun parse(text: String): SettingsBackup {
        val b = try {
            json.decodeFromString(SettingsBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException(newer = false)
        }
        if (b.app != SettingsBackup.APP) throw BackupFormatException(newer = false)
        if (b.format > SettingsBackup.FORMAT) throw BackupFormatException(newer = true)
        if (b.format < 1) throw BackupFormatException(newer = false)
        return b
    }

    /**
     * The settings after applying [b] to [current]. Unknown names and values out of range are ignored. Two
     * things a file can never do: turn "Hide in screenshots" off (only on), and turn ETA sharing on when this
     * phone has no ntfy topic set up (the topic isn't in the file).
     */
    fun apply(b: SettingsBackup, current: AppSettings): AppSettings {
        val f = b.settings
        var s = current
        f.units?.let { n -> Units.entries.firstOrNull { it.name == n } }?.let { s = s.copy(units = it) }
        f.mapStyle?.let { n -> MapStyle.entries.firstOrNull { it.name == n } }?.let { s = s.copy(mapStyle = it) }
        f.travelMode?.let { n -> TravelMode.entries.firstOrNull { it.name == n } }?.let { s = s.copy(travelMode = it) }
        f.voice?.let { s = s.copy(voice = it) }
        f.speedCameras?.let { s = s.copy(speedCameras = it) }
        f.speedLimit?.let { s = s.copy(speedLimit = it) }
        f.withSearchData?.let { s = s.copy(withSearchData = it) }
        f.ntfyServer?.let { v -> if (v.isEmpty() || v.startsWith("https://")) s = s.copy(ntfyServer = v.trim().take(300)) }
        f.ntfyEnabled?.let { on -> s = s.copy(ntfyEnabled = on && s.ntfyTopic.isNotBlank() && s.ntfyServer.isNotBlank()) }
        f.keepScreenOn?.let { s = s.copy(keepScreenOn = it) }
        if (f.hideScreenshots == true) s = s.copy(hideScreenshots = true)
        f.hillshading?.let { s = s.copy(hillshading = it) }
        f.transitEnabled?.let { s = s.copy(transitEnabled = it) }
        f.avoidTolls?.let { s = s.copy(avoidTolls = it) }
        f.avoidMotorways?.let { s = s.copy(avoidMotorways = it) }
        f.avoidUnpaved?.let { s = s.copy(avoidUnpaved = it) }
        f.avoidFerries?.let { s = s.copy(avoidFerries = it) }
        f.overSpeedAlert?.let { s = s.copy(overSpeedAlert = it) }
        f.overSpeedMargin?.takeIf { it in 0..20 }?.let { s = s.copy(overSpeedMargin = it) }
        f.keepRecentSearches?.let { s = s.copy(keepRecentSearches = it) }
        return s
    }

    /** Which settings [apply] would change, as keys for the confirmation (see the strings `backup_key_*`). */
    fun changes(b: SettingsBackup, current: AppSettings): List<String> {
        val next = apply(b, current)
        return buildList {
            if (next.units != current.units) add("units")
            if (next.mapStyle != current.mapStyle) add("map_style")
            if (next.travelMode != current.travelMode) add("travel_mode")
            if (next.voice != current.voice) add("voice")
            if (next.speedCameras != current.speedCameras) add("speed_cameras")
            if (next.speedLimit != current.speedLimit) add("speed_limit")
            if (next.withSearchData != current.withSearchData) add("search_data")
            if (next.ntfyEnabled != current.ntfyEnabled || next.ntfyServer != current.ntfyServer) add("ntfy")
            if (next.keepScreenOn != current.keepScreenOn) add("keep_screen_on")
            if (next.hideScreenshots != current.hideScreenshots) add("hide_screenshots")
            if (next.hillshading != current.hillshading) add("hillshading")
            if (next.transitEnabled != current.transitEnabled) add("transit")
            if (next.avoid != current.avoid) add("avoid")
            if (next.overSpeedAlert != current.overSpeedAlert || next.overSpeedMargin != current.overSpeedMargin) add("over_speed")
            if (next.keepRecentSearches != current.keepRecentSearches) add("recent")
        }
    }
}
