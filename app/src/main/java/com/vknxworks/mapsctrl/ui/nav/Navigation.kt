package com.vknxworks.mapsctrl.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
