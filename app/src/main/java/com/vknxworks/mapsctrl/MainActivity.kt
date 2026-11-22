package com.vknxworks.mapsctrl

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.ui.lock.AppLockGate
import com.vknxworks.mapsctrl.ui.lock.SensitiveWindowEffect
import com.vknxworks.mapsctrl.ui.lock.lockCovering
import com.vknxworks.mapsctrl.ui.nav.AppRoot
import com.vknxworks.mapsctrl.ui.theme.MapsCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        // Hidden from screenshots until the saved settings say otherwise (and while the app lock covers it).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { CtrlWindow(this) { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** "geo:" links from other apps: shown on the map (coordinates) or searched (a place name). */
    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "geo") container.pendingGeo.value = data.toString()
    }
}

@Composable
fun CtrlWindow(activity: ComponentActivity, content: @Composable () -> Unit) {
    val c = activity.container
    val dark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose { }
    }
    // Read before the settings: once this is true, the settings read below are the saved ones.
    val loaded by c.settings.isLoaded.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val nav by c.navigation.state.collectAsStateWithLifecycle()
    // "Hide in screenshots" and "Hide in recent apps" (both off by default); while the lock or a PIN screen is on
    // screen the window is always kept out of screenshots, and with the app lock on the Recents card is blank.
    val secure = !loaded || settings.hideScreenshots || lockCovering(c.vault)
    val blankRecents = settings.hideRecents || c.vault.enabled
    LaunchedEffect(secure, blankRecents) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setRecentsScreenshotEnabled(!blankRecents)
    }
    // While a lock or PIN screen covers the app: no other apps' overlays on top, and hidden from apps that misuse
    // accessibility (real screen readers still work).
    SensitiveWindowEffect(enabled = lockCovering(c.vault), sensitiveData = true)
    // The screen stays on while navigating (when that's on in Settings).
    LaunchedEffect(nav != null, settings.keepScreenOn) {
        if (nav != null && settings.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    // The lock covers only the screens: navigation, spoken directions, track recording and ETA sharing go on.
    MapsCtrlTheme(dark) { AppLockGate { content() } }
}
