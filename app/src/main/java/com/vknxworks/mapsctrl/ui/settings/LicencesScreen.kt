package com.vknxworks.mapsctrl.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.ui.components.Group
import com.vknxworks.mapsctrl.ui.components.GroupRow
import com.vknxworks.mapsctrl.ui.components.ScreenTopBar

/** Map data credit and the licences of everything MapsCtrl is built with. */
@Composable
fun LicencesScreen() {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.licences_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.group_map_data), footer = stringResource(R.string.osm_footer)) {
                GroupRow(stringResource(R.string.osm_credit), stringResource(R.string.osm_licence), value = stringResource(R.string.lic_odbl), onClick = { open("https://www.openstreetmap.org/copyright") })
            }
            Group(stringResource(R.string.group_libraries)) {
                GroupRow(stringResource(R.string.name_mapsforge), stringResource(R.string.lib_mapsforge), value = stringResource(R.string.lic_lgpl3), onClick = { open("https://github.com/mapsforge/mapsforge") })
                GroupRow(stringResource(R.string.name_brouter), stringResource(R.string.lib_brouter), value = stringResource(R.string.lic_mit), onClick = { open("https://github.com/abrensch/brouter") })
                GroupRow(stringResource(R.string.name_androidsvg), stringResource(R.string.lib_androidsvg), value = stringResource(R.string.lic_apache2), onClick = { open("https://github.com/BigBadaboom/androidsvg") })
                GroupRow(stringResource(R.string.name_sqlite), stringResource(R.string.lib_sqlite), value = stringResource(R.string.lic_public_domain), onClick = { open("https://sqlite.org/copyright.html") })
                GroupRow(stringResource(R.string.name_androidx), stringResource(R.string.lib_androidx), value = stringResource(R.string.lic_apache2), onClick = { open("https://developer.android.com/jetpack/androidx") })
                GroupRow(stringResource(R.string.name_kotlin), stringResource(R.string.lib_kotlin), value = stringResource(R.string.lic_apache2), onClick = { open("https://kotlinlang.org") })
            }
            Group(stringResource(R.string.group_fonts)) {
                GroupRow(stringResource(R.string.name_space_grotesk), stringResource(R.string.font_bundled), value = stringResource(R.string.lic_ofl), onClick = { open("https://github.com/floriankarsten/space-grotesk") })
                GroupRow(stringResource(R.string.name_jetbrains_mono), stringResource(R.string.font_bundled), value = stringResource(R.string.lic_ofl), onClick = { open("https://github.com/JetBrains/JetBrainsMono") })
            }
        }
    }
}
