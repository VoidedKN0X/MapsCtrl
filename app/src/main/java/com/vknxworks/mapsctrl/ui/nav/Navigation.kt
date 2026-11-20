package com.vknxworks.mapsctrl.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.ui.components.ConfirmDialog
import com.vknxworks.mapsctrl.ui.components.MessageView
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vknxworks.mapsctrl.ui.home.HomeScreen
import com.vknxworks.mapsctrl.ui.settings.LicencesScreen
import com.vknxworks.mapsctrl.ui.settings.SettingsScreen
import com.vknxworks.mapsctrl.ui.settings.YouScreen
import kotlinx.serialization.Serializable

// The map with its tabs (Map, Saved, Downloads, More)
@Serializable data object HomeRoute

// The You page (from the profile picture), Settings and licences
@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object LicencesRoute

/** One place that knows where things open. */
open class Navigator(val nav: NavHostController) {
    fun navigate(route: Any) = nav.navigate(route) { launchSingleTop = true }

    open fun back() {
        nav.popBackStack()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

@Composable
fun AppRoot() {
    val saved = LocalContext.current.container.saved
    val unreadable by saved.unreadable.collectAsStateWithLifecycle()
    if (unreadable) {
        UnreadableSaved()
        return
    }
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavHost(navController, startDestination = HomeRoute) {
            composable<HomeRoute> { HomeScreen() }
            composable<YouRoute> { Page { YouScreen() } }
            composable<SettingsRoute> { Page { SettingsScreen() } }
            composable<LicencesRoute> { Page { LicencesScreen() } }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}

/**
 * The saved places and tracks are encrypted and can't be opened (the key in the phone's secure storage is gone
 * or changed). Nothing was deleted or changed: say so, offer to try again, and only start empty when asked to,
 * keeping the unreadable file aside. Shown instead of the map, so nothing new (a recorded track, a saved place)
 * can be made while it couldn't be stored.
 */
@Composable
private fun UnreadableSaved() {
    val saved = LocalContext.current.container.saved
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        MessageView(
            Icons.Outlined.Lock,
            stringResource(R.string.unreadable_title),
            stringResource(R.string.unreadable_body),
            actionLabel = stringResource(R.string.unreadable_retry),
            onAction = { saved.retry() },
        )
        TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.unreadable_start_empty), color = CtrlColors.Muted)
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.unreadable_start_empty_title),
            body = stringResource(R.string.unreadable_start_empty_body),
            confirm = stringResource(R.string.unreadable_start_empty),
            onDismiss = { confirm = false },
        ) {
            confirm = false
            scope.launch { saved.startEmpty() }
        }
    }
}
