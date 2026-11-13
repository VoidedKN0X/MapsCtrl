package com.vknxworks.mapsctrl.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.Units
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.settings.MapStyle
import com.vknxworks.mapsctrl.share.Ntfy
import com.vknxworks.mapsctrl.ui.components.CtrlTextField
import com.vknxworks.mapsctrl.ui.components.Group
import com.vknxworks.mapsctrl.ui.components.GroupChoice
import com.vknxworks.mapsctrl.ui.components.GroupRow
import com.vknxworks.mapsctrl.ui.components.GroupSwitch
import com.vknxworks.mapsctrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun set(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.group_map)) {
                GroupChoice(stringResource(R.string.map_style), MapStyle.entries.map { it to stringResource(it.label) }, s.mapStyle, icon = Icons.Outlined.Contrast) { v -> set { it.copy(mapStyle = v) } }
                GroupChoice(
                    stringResource(R.string.units),
                    listOf(Units.METRIC to stringResource(R.string.units_metric), Units.IMPERIAL to stringResource(R.string.units_imperial)),
                    s.units,
                    icon = Icons.Outlined.Straighten,
                ) { v -> set { it.copy(units = v) } }
            }

            Group(stringResource(R.string.group_navigation)) {
                GroupChoice(
                    stringResource(R.string.default_mode),
                    listOf(TravelMode.CAR to stringResource(R.string.mode_car), TravelMode.BIKE to stringResource(R.string.mode_bike), TravelMode.FOOT to stringResource(R.string.mode_foot)),
                    s.travelMode,
                    icon = Icons.Outlined.DirectionsCar,
                ) { v -> set { it.copy(travelMode = v) } }
                GroupSwitch(stringResource(R.string.voice), stringResource(R.string.voice_subtitle), s.voice, Icons.Outlined.RecordVoiceOver) { v -> set { it.copy(voice = v) } }
                GroupRow(stringResource(R.string.voice_test), stringResource(R.string.voice_test_subtitle), onClick = { c.navigation.testVoice() })
                GroupRow(stringResource(R.string.voice_settings), stringResource(R.string.voice_settings_subtitle), showChevron = true, onClick = {
                    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
                GroupSwitch(stringResource(R.string.speed_limit), stringResource(R.string.speed_limit_subtitle), s.speedLimit, Icons.Outlined.Speed) { v -> set { it.copy(speedLimit = v) } }
                GroupSwitch(stringResource(R.string.speed_cameras), stringResource(R.string.speed_cameras_subtitle), s.speedCameras, Icons.Outlined.CameraAlt) { v -> set { it.copy(speedCameras = v) } }
                GroupSwitch(stringResource(R.string.keep_screen_on), stringResource(R.string.keep_screen_on_subtitle), s.keepScreenOn, Icons.Outlined.LightMode) { v -> set { it.copy(keepScreenOn = v) } }
            }

            NtfyGroup(s, ::set)

            Group(stringResource(R.string.group_privacy)) {
                GroupRow(stringResource(R.string.location_permission), stringResource(if (c.location.hasPermission()) R.string.location_permission_on else R.string.location_permission_off), icon = Icons.Outlined.LocationOn, showChevron = true, onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                GroupSwitch(stringResource(R.string.hide_screenshots), stringResource(R.string.hide_screenshots_subtitle), s.hideScreenshots, Icons.Outlined.Screenshot) { v -> set { it.copy(hideScreenshots = v) } }
            }
        }
    }
}

@Composable
private fun NtfyGroup(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(s.ntfyServer) }
    var topic by remember { mutableStateOf(s.ntfyTopic) }
    val valid = Ntfy.topicUrl(server, topic) != null
    Group(stringResource(R.string.ntfy_title_setting), footer = stringResource(R.string.ntfy_footer)) {
        GroupSwitch(
            stringResource(R.string.ntfy_enable),
            stringResource(if (valid) R.string.ntfy_enable_subtitle else R.string.ntfy_needs_setup),
            s.ntfyEnabled && valid,
            Icons.Outlined.Share,
        ) { v -> if (valid || !v) set { it.copy(ntfyEnabled = v, ntfyServer = server.trim(), ntfyTopic = topic.trim()) } }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            CtrlTextField(
                value = server,
                onValueChange = { server = it; set { st -> st.copy(ntfyServer = it.trim()) } },
                label = { Text(stringResource(R.string.ntfy_server)) },
                placeholder = { Text(stringResource(R.string.ntfy_server_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            CtrlTextField(
                value = topic,
                onValueChange = { topic = it; set { st -> st.copy(ntfyTopic = it.trim()) } },
                label = { Text(stringResource(R.string.ntfy_topic)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GroupRow(stringResource(R.string.ntfy_test), stringResource(R.string.ntfy_test_subtitle), icon = Icons.Outlined.Send, onClick = {
            if (!valid) return@GroupRow
            scope.launch {
                val ok = Ntfy.send(server, topic, context.getString(R.string.app_name), context.getString(R.string.ntfy_test_message))
                Toast.makeText(context, if (ok) R.string.ntfy_test_ok else R.string.ntfy_test_failed, Toast.LENGTH_SHORT).show()
            }
        })
    }
}
