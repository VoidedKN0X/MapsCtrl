package com.vknxworks.mapsctrl.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.map.Camera
import com.vknxworks.mapsctrl.map.MapController
import com.vknxworks.mapsctrl.routing.Units
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.CtrlButton
import com.vknxworks.mapsctrl.ui.components.CtrlCard
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.CtrlWordmark
import com.vknxworks.mapsctrl.ui.components.LocalBottomClearance
import com.vknxworks.mapsctrl.ui.components.ProfileButton
import com.vknxworks.mapsctrl.ui.components.SectionLabel
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlShapes
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** The Map tab over the map: wordmark and profile, search pill, map buttons, scale, credit and the place sheet. */
@Composable
fun MapOverlay(
    state: HomeState,
    controller: MapController,
    camera: Camera,
    hasMaps: Boolean,
    hasPermission: Boolean,
    hasFix: Boolean,
    onAskLocation: () -> Unit,
    onRoute: (Place) -> Unit,
) {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        // Top: a soft fade from the background so the wordmark reads over any map.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(CtrlColors.Background.copy(alpha = 0.92f), CtrlColors.Background.copy(alpha = 0f))))
                .statusBarsPadding()
                .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                CtrlWordmark("Maps", Modifier.weight(1f))
                ProfileButton()
            }
            Spacer(Modifier.height(12.dp))
            SearchPill(onClick = { state.searchOpen = true })
        }

        // Right: compass, zoom, location.
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = Gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (camera.rotation != 0f) {
                CtrlSquareButton(Icons.Filled.Navigation, stringResource(R.string.north_up), onClick = {
                    if (state.follow == Follow.COMPASS) state.follow = Follow.FOLLOW
                    controller.rotate(0f)
                }, modifier = Modifier.rotate(camera.rotation))
            }
            CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.zoom_in), onClick = controller::zoomIn)
            CtrlSquareButton(Icons.Outlined.Remove, stringResource(R.string.zoom_out), onClick = controller::zoomOut)
            val (icon, desc) = when (state.follow) {
                Follow.OFF -> Icons.Outlined.LocationSearching to R.string.location_show
                Follow.FOLLOW -> Icons.Filled.MyLocation to R.string.location_compass
                Follow.COMPASS -> Icons.Filled.Explore to R.string.location_stop
            }
            CtrlSquareButton(
                icon,
                stringResource(desc),
                tint = if (state.follow != Follow.OFF) CtrlColors.AccentText else CtrlColors.Text,
                onClick = {
                    if (!hasPermission) {
                        onAskLocation()
                    } else {
                        state.follow = when (state.follow) {
                            Follow.OFF -> Follow.FOLLOW
                            Follow.FOLLOW -> Follow.COMPASS
                            Follow.COMPASS -> { controller.rotate(0f); Follow.OFF }
                        }
                    }
                },
            )
        }

        // Bottom: scale and credit, then the place sheet or the "no maps" card.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = LocalBottomClearance.current),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                ScaleBar(camera.metersPerPixel, settings.units, Modifier.weight(1f))
                Text(
                    stringResource(R.string.osm_credit),
                    style = CtrlType.Data.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    color = CtrlColors.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CtrlColors.Background.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            val sel = state.selected
            when {
                sel != null -> PlaceSheet(sel, onClose = { state.selected = null }, onRoute = { onRoute(sel) })
                !hasMaps -> NoMapsCard()
            }
            if (!hasFix && state.follow != Follow.OFF && hasPermission && sel == null) {
                Text(
                    stringResource(R.string.location_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = stringResource(R.string.search_hint)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface.copy(alpha = 0.96f))
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A map scale: the longest round distance that fits in about 100dp. */
@Composable
fun ScaleBar(metersPerPixel: Double, units: Units, modifier: Modifier = Modifier) {
    if (metersPerPixel <= 0.0) return
    val density = LocalDensity.current.density
    val fmt = rememberFmt()
    val maxMeters = metersPerPixel * 100 * density
    val (meters, label) = when (units) {
        Units.METRIC -> {
            val nice = niceNumber(maxMeters)
            nice to fmt.distance(nice, units)
        }
        Units.IMPERIAL -> {
            val feet = maxMeters * 3.28084
            if (feet < 5280) {
                val nf = niceNumber(feet)
                nf / 3.28084 to stringResource(R.string.unit_ft, nf.toInt())
            } else {
                val nm = niceNumber(feet / 5280)
                nm * 1609.344 to stringResource(R.string.unit_mi, if (nm % 1.0 == 0.0) nm.toInt().toString() else nm.toString())
            }
        }
    }
    val widthDp = (meters / metersPerPixel / density).toFloat().dp
    Column(modifier) {
        Text(label, style = CtrlType.Data, color = CtrlColors.Text, modifier = Modifier.background(CtrlColors.Background.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(widthDp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(CtrlColors.Text))
    }
}

private fun niceNumber(max: Double): Double {
    val exp = 10.0.pow(floor(log10(max)))
    val f = max / exp
    val n = when { f >= 5 -> 5.0; f >= 2 -> 2.0; else -> 1.0 }
    return n * exp
}

@Composable
private fun NoMapsCard() {
    val c = LocalContext.current.container
    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        SectionLabel(stringResource(R.string.no_maps_label))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.no_maps_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.no_maps_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
        Spacer(Modifier.height(14.dp))
        CtrlButton(stringResource(R.string.no_maps_action), onClick = { c.openTab.value = TAB_DOWNLOADS }, icon = Icons.Outlined.Download, modifier = Modifier.fillMaxWidth())
    }
}
