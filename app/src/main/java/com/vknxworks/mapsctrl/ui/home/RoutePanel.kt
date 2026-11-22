package com.vknxworks.mapsctrl.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import com.vknxworks.mapsctrl.geo.ElevationProfile
import com.vknxworks.mapsctrl.ui.common.ElevationChart
import com.vknxworks.mapsctrl.ui.common.height
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.nav.AlternativesState
import com.vknxworks.mapsctrl.nav.MAX_STOPS
import com.vknxworks.mapsctrl.nav.PlanState
import com.vknxworks.mapsctrl.nav.PlanStatus
import com.vknxworks.mapsctrl.nav.RouteStart
import com.vknxworks.mapsctrl.routing.Avoid
import com.vknxworks.mapsctrl.routing.RouteOptions
import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.ui.components.CtrlSwitch
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.search.SearchResult
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.CtrlBottomSheet
import com.vknxworks.mapsctrl.ui.components.CtrlButton
import com.vknxworks.mapsctrl.ui.components.CtrlCard
import com.vknxworks.mapsctrl.ui.components.CtrlProgressBar
import com.vknxworks.mapsctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.CtrlTextField
import com.vknxworks.mapsctrl.ui.components.SectionLabel
import com.vknxworks.mapsctrl.ui.components.topDivider
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlShapes
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Route planning: from and to at the top, travel mode, and the result with Start at the bottom. */
@Composable
fun RoutePanel(
    plan: PlanState,
    settings: AppSettings,
    hasLocation: Boolean,
    hasPermission: Boolean,
    pickingStart: Boolean,
    onPickStart: () -> Unit,
    pickingStop: Boolean,
    onPickStop: () -> Unit,
    onAskLocation: () -> Unit,
    onStart: () -> Unit,
) {
    val c = LocalContext.current.container
    val fmt = rememberFmt()
    var choosingStart by remember { mutableStateOf(false) }
    var addingStop by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Keeps the status bar readable over a light map.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(CtrlColors.Background.copy(alpha = 0.85f)))
        // ---- Top: from / to / mode ----
        CtrlCard(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gutter, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = { c.planner.close() }, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val startText = when (val s = plan.start) {
                        RouteStart.MyLocation -> stringResource(R.string.route_my_location)
                        is RouteStart.At -> s.place.name
                    }
                    EndRow(stringResource(R.string.route_from), if (pickingStart) stringResource(R.string.route_tap_map) else startText, Icons.Filled.MyLocation) { choosingStart = true }
                    plan.stops.forEachIndexed { i, stop ->
                        StopRow(stop.name, canMoveUp = i > 0, onMoveUp = { c.planner.moveStop(i, up = true) }, onRemove = { c.planner.removeStop(i) })
                    }
                    if (!plan.transit && (plan.stops.size < MAX_STOPS || pickingStop)) {
                        EndRow(
                            stringResource(R.string.route_via),
                            if (pickingStop) stringResource(R.string.route_tap_map) else stringResource(R.string.route_add_stop),
                            Icons.Outlined.AddLocationAlt,
                            divider = true,
                            muted = !pickingStop,
                        ) { addingStop = true }
                    }
                    EndRow(stringResource(R.string.route_to_label), plan.destination?.name.orEmpty(), Icons.Outlined.Place, divider = true, onClick = null)
                }
                if (plan.start is RouteStart.At) {
                    Spacer(Modifier.width(8.dp))
                    CtrlSquareButton(Icons.Outlined.SwapVert, stringResource(R.string.route_swap), onClick = { c.planner.swap() }, size = 40.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mode = plan.mode.takeIf { !plan.transit }
                // Four chips need smaller labels to fit a phone's width.
                val compact = true
                ModeChip(TravelMode.CAR, Icons.Filled.DirectionsCar, R.string.mode_car, mode, Modifier.weight(1f), compact)
                ModeChip(TravelMode.MOPED, Icons.Filled.Moped, R.string.mode_moped, mode, Modifier.weight(1f), compact)
                ModeChip(TravelMode.BIKE, Icons.AutoMirrored.Filled.DirectionsBike, R.string.mode_bike, mode, Modifier.weight(1f), compact)
                ModeChip(TravelMode.FOOT, Icons.AutoMirrored.Filled.DirectionsWalk, R.string.mode_foot, mode, Modifier.weight(1f), compact)
                if (settings.transitEnabled) TransitChip(plan.transit, Modifier.weight(1f))
            }
            if (plan.transit) {
                Spacer(Modifier.height(10.dp))
                TransitTimeRow()
            } else {
                val active = RouteOptions.active(plan.mode, settings.avoid)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(CtrlShapes.Control).clickable { options = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (active.isEmpty()) stringResource(R.string.route_options) else stringResource(R.string.route_avoiding, active.map { stringResource(avoidLabel(it)) }.joinToString(", ")),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active.isEmpty()) CtrlColors.Text else CtrlColors.AccentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Transit hidden in Settings while it was picked: back to the usual mode.
        LaunchedEffect(settings.transitEnabled) { if (!settings.transitEnabled && plan.transit) c.planner.setMode(plan.mode) }

        // ---- Bottom: status and Start ----
        CtrlCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp)) {
            if (plan.transit && plan.status != PlanStatus.NoLocation) {
                TransitResults(plan)
            } else when (val s = plan.status) {
                PlanStatus.Idle, PlanStatus.Computing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = CtrlColors.AccentText, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.route_computing), style = MaterialTheme.typography.bodyLarge)
                }
                PlanStatus.NoLocation -> {
                    Text(stringResource(R.string.route_no_location_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(if (hasPermission) R.string.route_no_location_wait else R.string.route_no_location_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!hasPermission) CtrlButton(stringResource(R.string.location_allow), onClick = onAskLocation, modifier = Modifier.weight(1f))
                        CtrlSecondaryButton(onClick = { choosingStart = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.route_choose_start)) }
                    }
                    LaunchedEffect(hasLocation) { if (hasLocation) c.planner.compute() }
                }
                is PlanStatus.NeedSegments -> {
                    SectionLabel(stringResource(R.string.route_data_label))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.route_data_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val total = s.sizes?.values?.sum()
                    val body = when {
                        s.sizes == null -> stringResource(R.string.route_data_body_checking, s.tiles.size)
                        s.sizeError -> stringResource(R.string.route_data_body_unknown, s.tiles.size)
                        s.sizes.size < s.tiles.size -> stringResource(R.string.route_data_missing_on_server)
                        else -> stringResource(R.string.route_data_body, s.tiles.size, fmt.bytes(total ?: 0))
                    }
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    Text(s.tiles.sorted().joinToString("  "), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    CtrlButton(
                        if (total != null && total > 0) stringResource(R.string.route_data_download_size, fmt.bytes(total)) else stringResource(R.string.route_data_download),
                        onClick = { c.planner.downloadSegments() },
                        icon = Icons.Outlined.Download,
                        enabled = s.sizes != null && (s.sizeError || s.sizes.size == s.tiles.size),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PlanStatus.DownloadingSegments -> {
                    val progress by c.segments.progress.collectAsStateWithLifecycle()
                    val (done, total) = progress.values.fold(0L to 0L) { acc, p -> (acc.first + p.first) to (acc.second + p.second.coerceAtLeast(0)) }
                    Text(stringResource(R.string.route_data_downloading), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    CtrlProgressBar(if (total > 0) done.toFloat() / total else 0f, color = CtrlColors.Accent)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(if (total > 0) "${fmt.bytes(done)} / ${fmt.bytes(total)}" else fmt.bytes(done), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { c.segments.cancel() }) { Text(stringResource(R.string.cancel)) }
                    }
                }
                is PlanStatus.Failed -> {
                    Text(stringResource(R.string.route_failed_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.route_failed_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    if (s.detail.isNotBlank()) Text(s.detail, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    CtrlSecondaryButton(onClick = { c.planner.compute() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_again)) }
                }
                is PlanStatus.Ready -> {
                    val r = plan.chosen ?: s.route
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmt.duration(r.durationSeconds), style = CtrlType.Readout, color = CtrlColors.Text, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(12.dp))
                        Text(fmt.distance(r.distanceMeters, settings.units), style = MaterialTheme.typography.titleLarge, color = CtrlColors.Muted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(stringResource(R.string.route_arrive_at, fmt.arrival(r.durationSeconds)), style = CtrlType.Data, color = CtrlColors.Muted)
                    AlternativesRow(plan, settings)
                    RouteElevation(r, settings)
                    if (r.mode == TravelMode.MOPED) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = CtrlColors.Muted)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.moped_notice), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    CtrlButton(stringResource(R.string.nav_start), onClick = onStart, icon = Icons.Filled.Navigation, modifier = Modifier.fillMaxWidth())
                    if (plan.start is RouteStart.At) {
                        Text(stringResource(R.string.route_start_elsewhere), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    if (options) {
        OptionsSheet(plan.mode, settings, onDismiss = { options = false })
    }

    if (addingStop) {
        StartSheet(
            title = stringResource(R.string.route_add_stop),
            showMyLocation = false,
            onDismiss = { addingStop = false },
            onMyLocation = {},
            onPickOnMap = { addingStop = false; onPickStop() },
            onPlace = { p -> addingStop = false; c.planner.addStop(p) },
        )
    }

    if (choosingStart) {
        StartSheet(
            title = stringResource(R.string.route_choose_start),
            showMyLocation = true,
            onDismiss = { choosingStart = false },
            onMyLocation = { choosingStart = false; c.planner.setStart(RouteStart.MyLocation); if (!hasPermission) onAskLocation() },
            onPickOnMap = { choosingStart = false; onPickStart() },
            onPlace = { p -> choosingStart = false; c.planner.setStart(RouteStart.At(p)) },
        )
    }
}

/** Other routes: a button that asks for them, then one chip per route to pick from. */
@Composable
private fun AlternativesRow(plan: PlanState, settings: AppSettings) {
    val c = LocalContext.current.container
    val fmt = rememberFmt()
    val routes = plan.routes
    if (routes.size > 1) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            routes.forEachIndexed { i, route ->
                val on = i == plan.selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CtrlShapes.Control)
                        .background(if (on) CtrlColors.Accent else CtrlColors.Raised)
                        .clickable { c.planner.select(i) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(fmt.duration(route.durationSeconds), style = MaterialTheme.typography.labelLarge, color = if (on) CtrlColors.OnAccent else CtrlColors.Text, maxLines = 1)
                    Text(fmt.distance(route.distanceMeters, settings.units), style = MaterialTheme.typography.labelSmall, color = if (on) CtrlColors.OnAccent else CtrlColors.Muted, maxLines = 1)
                }
            }
        }
    }
    when (plan.alternativesState) {
        AlternativesState.NONE -> TextButton(onClick = { c.planner.findAlternatives() }, modifier = Modifier.padding(top = 2.dp)) {
            Text(stringResource(R.string.route_alternatives))
        }
        AlternativesState.COMPUTING -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = CtrlColors.AccentText, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.route_alternatives_computing), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
        AlternativesState.DONE -> if (routes.size < 2) {
            Text(stringResource(R.string.route_alternatives_none), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The route's climb and descent; tap to see its elevation profile. Only when the routing data has heights. */
@Composable
private fun RouteElevation(route: com.vknxworks.mapsctrl.routing.Route, settings: AppSettings) {
    val fmt = rememberFmt()
    val profile = remember(route) { ElevationProfile.of(route.points, route.elevations) } ?: return
    var open by remember(route) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(CtrlShapes.Control).clickable { open = !open }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Terrain, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.elevation_row, fmt.height(profile.ascent, settings.units), fmt.height(profile.descent, settings.units)),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(if (open) R.string.elevation_hide else R.string.elevation_show), Modifier.size(20.dp), tint = CtrlColors.Muted)
    }
    if (open) ElevationChart(profile, settings.units, Modifier.fillMaxWidth().padding(top = 4.dp))
}

/** What routes avoid, for the travel mode that's picked (only what its routing profile can do). */
@Composable
private fun OptionsSheet(mode: TravelMode, settings: AppSettings, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).navigationBarsPadding()) {
            Text(stringResource(R.string.route_options), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.route_options_body), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            Spacer(Modifier.height(8.dp))
            for (a in RouteOptions.supported(mode)) {
                val on = a in settings.avoid
                Row(
                    Modifier.fillMaxWidth().clip(CtrlShapes.Control).clickable {
                        scope.launch { setAvoid(c, a, !on) }
                    }.heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(avoidLabel(a)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    CtrlSwitch(on, onCheckedChange = { v -> scope.launch { setAvoid(c, a, v) } })
                }
            }
            if (mode == TravelMode.CAR) {
                Text(stringResource(R.string.route_options_car_ferries), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            if (mode == TravelMode.MOPED) {
                Text(stringResource(R.string.route_options_moped), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Saves an avoid choice and works the route out again once the settings show it. */
private suspend fun setAvoid(c: com.vknxworks.mapsctrl.AppContainer, a: Avoid, on: Boolean) {
    c.settings.update { it.withAvoid(a, on) }
    kotlinx.coroutines.withTimeoutOrNull(1_000) { c.settings.settings.first { (a in it.avoid) == on } }
    c.planner.optionsChanged()
}

fun avoidLabel(a: Avoid): Int = when (a) {
    Avoid.TOLLS -> R.string.avoid_tolls
    Avoid.MOTORWAYS -> R.string.avoid_motorways
    Avoid.UNPAVED -> R.string.avoid_unpaved
    Avoid.FERRIES -> R.string.avoid_ferries
}

/** A stop on the way, with buttons to move it earlier and to take it out. */
@Composable
private fun StopRow(name: String, canMoveUp: Boolean, onMoveUp: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().topDivider().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.FiberManualRecord, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.route_via).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.width(44.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (canMoveUp) {
            Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.route_stop_up), Modifier.clip(CtrlShapes.Control).clickable(onClick = onMoveUp).padding(8.dp).size(18.dp), tint = CtrlColors.Muted)
        }
        Icon(Icons.Outlined.Close, stringResource(R.string.route_stop_remove), Modifier.clip(CtrlShapes.Control).clickable(onClick = onRemove).padding(8.dp).size(18.dp), tint = CtrlColors.Muted)
    }
}

@Composable
private fun EndRow(label: String, value: String, icon: ImageVector, divider: Boolean = false, muted: Boolean = false, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.topDivider() else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.width(10.dp))
        Text(label.uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.width(44.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (muted) CtrlColors.Muted else CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModeChip(mode: TravelMode, icon: ImageVector, label: Int, selected: TravelMode?, modifier: Modifier, compact: Boolean = false) {
    val c = LocalContext.current.container
    val on = mode == selected
    // Five chips (four modes and Transit) fit a phone's width with the label under the icon.
    Column(
        modifier
            .height(if (compact) 52.dp else 44.dp)
            .clip(CtrlShapes.Control)
            .background(if (on) CtrlColors.Accent else CtrlColors.Raised)
            .clickable {
                c.planner.setMode(mode)
            }
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (on) CtrlColors.OnAccent else CtrlColors.Text)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (on) CtrlColors.OnAccent else CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    // Remember the last mode for next time.
    LaunchedEffect(on) { if (on) c.settings.update { it.copy(travelMode = mode) } }
}

/** Where to start: your position, a point you tap on the map, or a place or coordinates you type. */
@Composable
private fun StartSheet(title: String, showMyLocation: Boolean, onDismiss: () -> Unit, onMyLocation: () -> Unit, onPickOnMap: () -> Unit, onPlace: (Place) -> Unit) {
    val c = LocalContext.current.container
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    val camera = c.location.fix.value?.point
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        results = c.search.search(query, camera)
    }
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (showMyLocation) SheetRow(Icons.Filled.MyLocation, stringResource(R.string.route_my_location), onMyLocation)
            SheetRow(Icons.Outlined.TouchApp, stringResource(R.string.route_pick_on_map), onPickOnMap)
            Spacer(Modifier.height(10.dp))
            CtrlTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.route_start_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(results.take(30)) { r ->
                    SheetRow(Icons.Outlined.Place, r.place.name, { onPlace(r.place) }, r.place.address() ?: r.place.category)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, text: String, onClick: () -> Unit, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(CtrlShapes.Control).clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
