package com.vknxworks.mapsctrl.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.vknxworks.mapsctrl.geo.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A position fix. [bearing] and [speed] (m/s) are null when the phone doesn't know them. */
data class Fix(val point: LatLon, val accuracy: Float, val bearing: Float?, val speed: Float?, val time: Long)

/**
 * Your position from Android's own LocationManager: the "fused" provider when the phone has one (it's part of
 * Android, not Google Play services), otherwise GPS. No Google location library.
 * Several users (map, navigation, track recording) share one listener.
 */
class LocationSource(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)

    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix

    private val users = HashSet<String>()
    private var listening: String? = null

    /** Injected positions (debug builds' demo drive); while set, the real location is ignored. */
    @Volatile var simulated: Boolean = false
        private set

    private val listener = LocationListener { loc -> if (!simulated) _fix.value = loc.toFix() }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(): Boolean = runCatching { lm.isLocationEnabled }.getOrDefault(false)

    private fun provider(): String? {
        val all = lm.allProviders
        return when {
            LocationManager.FUSED_PROVIDER in all -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in all -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in all -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    /** Starts updates for [user] ("map", "nav", "track"). Does nothing without the permission. */
    @SuppressLint("MissingPermission")
    fun start(user: String) {
        users += user
        if (listening != null || !hasPermission()) return
        val p = provider() ?: return
        runCatching {
            lm.getLastKnownLocation(p)?.let { if (_fix.value == null && !simulated) _fix.value = it.toFix() }
            lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
            listening = p
        }
    }

    fun stop(user: String) {
        users -= user
        if (users.isEmpty() && listening != null) {
            runCatching { lm.removeUpdates(listener) }
            listening = null
        }
    }

    /** After the permission was granted: start for whoever asked before. */
    fun restart() {
        val u = users.toList()
        if (listening != null) { runCatching { lm.removeUpdates(listener) }; listening = null }
        u.forEach { start(it) }
    }

    fun simulate(fix: Fix?) {
        simulated = fix != null
        if (fix != null) _fix.value = fix
    }

    private fun Location.toFix() = Fix(
        LatLon(latitude, longitude),
        if (hasAccuracy()) accuracy else 30f,
        if (hasBearing()) bearing else null,
        if (hasSpeed()) speed else null,
        time,
    )
}
