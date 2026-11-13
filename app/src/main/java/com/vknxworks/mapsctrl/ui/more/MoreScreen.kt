package com.vknxworks.mapsctrl.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.geo.Coordinates
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.settings.MapStyle
import com.vknxworks.mapsctrl.ui.components.CtrlAlertDialog
import com.vknxworks.mapsctrl.ui.components.CtrlTextField
import com.vknxworks.mapsctrl.ui.components.Group
import com.vknxworks.mapsctrl.ui.components.GroupChoice
import com.vknxworks.mapsctrl.ui.components.GroupRow
import com.vknxworks.mapsctrl.ui.components.TabTitle
import com.vknxworks.mapsctrl.ui.components.bottomClearance
import com.vknxworks.mapsctrl.ui.nav.LicencesRoute
import com.vknxworks.mapsctrl.ui.nav.LocalNavigator
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

/** Tools that don't fit elsewhere: go to coordinates, map colours, a voice test, credits. */
@Composable
fun MoreScreen(onGoTo: (Place) -> Unit) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var coords by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).bottomClearance()) {
        TabTitle(stringResource(R.string.tab_more))
        Group(stringResource(R.string.group_tools)) {
            GroupRow(stringResource(R.string.goto_coords), stringResource(R.string.goto_coords_subtitle), icon = Icons.Outlined.MyLocation, showChevron = true, onClick = { coords = true })
            GroupChoice(
                stringResource(R.string.map_style),
                MapStyle.entries.map { it to stringResource(it.label) },
                settings.mapStyle,
                icon = Icons.Outlined.Contrast,
            ) { v -> scope.launch { c.settings.update { it.copy(mapStyle = v) } } }
            GroupRow(stringResource(R.string.voice_test), stringResource(R.string.voice_test_subtitle), icon = Icons.Outlined.RecordVoiceOver, onClick = { c.navigation.testVoice() })
            GroupRow(
                stringResource(R.string.ntfy_title_setting),
                stringResource(if (settings.ntfyEnabled) R.string.ntfy_on else R.string.ntfy_off),
                icon = Icons.Outlined.Share,
            )
        }
        Group(stringResource(R.string.group_about)) {
            GroupRow(stringResource(R.string.licences_title), stringResource(R.string.licences_subtitle), icon = Icons.Outlined.Gavel, showChevron = true, onClick = { nav.navigate(LicencesRoute) })
        }
    }

    if (coords) {
        var text by remember { mutableStateOf("") }
        val parsed = Coordinates.parse(text)
        CtrlAlertDialog(
            onDismissRequest = { coords = false },
            title = { Text(stringResource(R.string.goto_coords), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    CtrlTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.goto_coords_example)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (text.isNotBlank() && parsed == null) stringResource(R.string.goto_coords_invalid) else stringResource(R.string.goto_coords_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (text.isNotBlank() && parsed == null) CtrlColors.Error else CtrlColors.Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { parsed?.let { onGoTo(Place(it.format(), it)) }; coords = false }, enabled = parsed != null) { Text(stringResource(R.string.goto_go)) }
            },
            dismissButton = { TextButton(onClick = { coords = false }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}
