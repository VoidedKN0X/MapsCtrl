package com.vknxworks.mapsctrl.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.nav.PlanState
import com.vknxworks.mapsctrl.nav.RouteStart
import com.vknxworks.mapsctrl.transit.Itinerary
import com.vknxworks.mapsctrl.transit.LegKind
import com.vknxworks.mapsctrl.transit.TransitLeg
import com.vknxworks.mapsctrl.transit.TransitStatus
import com.vknxworks.mapsctrl.transit.TransitTime
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.CtrlBottomSheet
import com.vknxworks.mapsctrl.ui.components.CtrlButton
import com.vknxworks.mapsctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.mapsctrl.ui.components.SectionLabel
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlShapes
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private const val SOURCES_URL = "https://transitous.org/sources/"

/** The Transit chip next to Car, Bike and Foot. Picking it is what starts a search on Transitous. */
@Composable
fun TransitChip(selected: Boolean, modifier: Modifier) {
    val c = LocalContext.current.container
    Row(
        modifier
            .height(44.dp)
            .clip(CtrlShapes.Control)
            .background(if (selected) CtrlColors.Accent else CtrlColors.Raised)
            .clickable(role = Role.Tab) { if (!selected) c.planner.setTransit() }
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DirectionsTransit, null, Modifier.size(18.dp), tint = if (selected) CtrlColors.OnAccent else CtrlColors.Text)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.mode_transit), style = MaterialTheme.typography.labelMedium, color = if (selected) CtrlColors.OnAccent else CtrlColors.Text, maxLines = 1)
    }
}

/** "Leave now", "Depart 14:30", "Arrive by 9:00 Tue": tap to change. */
@Composable
fun TransitTimeRow() {
    val c = LocalContext.current.container
    val state by c.transit.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val text = when (val t = state.time) {
        TransitTime.Now -> stringResource(R.string.transit_leave_now)
        is TransitTime.DepartAt -> stringResource(R.string.transit_depart_at, dayTime(t.time))
        is TransitTime.ArriveBy -> stringResource(R.string.transit_arrive_by, dayTime(t.time))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CtrlShapes.Control)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
            .clickable(role = Role.Button) { open = true }
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.transit_change_time), style = MaterialTheme.typography.labelLarge, color = CtrlColors.AccentText)
    }
    if (open) TransitTimeSheet(state.time, onDismiss = { open = false }) { t -> open = false; c.planner.setTransitTime(t) }
}

/** Leave now / depart at / arrive by, a day and a time. */
@Composable
private fun TransitTimeSheet(current: TransitTime, onDismiss: () -> Unit, onPick: (TransitTime) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val initial = when (current) {
        TransitTime.Now -> Instant.now()
        is TransitTime.DepartAt -> current.time
        is TransitTime.ArriveBy -> current.time
    }.atZone(zone)
    var kind by remember { mutableIntStateOf(when (current) { TransitTime.Now -> 0; is TransitTime.DepartAt -> 1; is TransitTime.ArriveBy -> 2 }) }
    val today = LocalDate.now(zone)
    var dayOffset by remember { mutableIntStateOf((initial.toLocalDate().toEpochDay() - today.toEpochDay()).toInt().coerceIn(0, 6)) }
    val picker = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).navigationBarsPadding()) {
            Text(stringResource(R.string.transit_when), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.transit_now, R.string.transit_depart, R.string.transit_arrive).forEachIndexed { i, label ->
                    Choice(stringResource(label), kind == i, Modifier.weight(1f)) { kind = i }
                }
            }
            if (kind != 0) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in 0..6) {
                        val label = when (d) {
                            0 -> stringResource(R.string.transit_today)
                            1 -> stringResource(R.string.transit_tomorrow)
                            else -> today.plusDays(d.toLong()).let { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + it.dayOfMonth }
                        }
                        Choice(label, dayOffset == d, Modifier) { dayOffset = d }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeInput(
                        state = picker,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = CtrlColors.Raised,
                            selectorColor = CtrlColors.Accent,
                            containerColor = CtrlColors.Surface,
                            clockDialSelectedContentColor = CtrlColors.OnAccent,
                            clockDialUnselectedContentColor = CtrlColors.Text,
                            periodSelectorSelectedContainerColor = CtrlColors.Accent,
                            periodSelectorSelectedContentColor = CtrlColors.OnAccent,
                            periodSelectorUnselectedContentColor = CtrlColors.Text,
                            timeSelectorSelectedContainerColor = CtrlColors.Accent,
                            timeSelectorSelectedContentColor = CtrlColors.OnAccent,
                            timeSelectorUnselectedContainerColor = CtrlColors.Raised,
                            timeSelectorUnselectedContentColor = CtrlColors.Text,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            CtrlButton(stringResource(R.string.transit_search), onClick = {
                val at = today.plusDays(dayOffset.toLong()).atTime(LocalTime.of(picker.hour, picker.minute)).atZone(zone).toInstant()
                onPick(
                    when (kind) {
                        1 -> TransitTime.DepartAt(at)
                        2 -> TransitTime.ArriveBy(at)
                        else -> TransitTime.Now
                    },
                )
            }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(CtrlShapes.Control)
            .background(if (on) CtrlColors.Accent else CtrlColors.Raised)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (on) CtrlColors.OnAccent else CtrlColors.Text, maxLines = 1)
    }
}

/** The bottom card in Transit mode: searching, an error, or the journeys with the picked one opened. */
@Composable
fun TransitResults(plan: PlanState) {
    val context = LocalContext.current
    val c = context.container
    val state by c.transit.state.collectAsStateWithLifecycle()
    when (val s = state.status) {
        TransitStatus.Idle, TransitStatus.Loading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = CtrlColors.AccentText, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.transit_searching), style = MaterialTheme.typography.bodyLarge)
            }
            Credit()
        }
        is TransitStatus.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        when {
                            s.busy -> R.string.transit_busy_title
                            s.offline -> R.string.transit_offline_title
                            else -> R.string.transit_error_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(if (s.offline) R.string.transit_offline_body else R.string.transit_error_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
            Spacer(Modifier.height(12.dp))
            CtrlSecondaryButton(onClick = { c.planner.compute() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_again)) }
        }
        is TransitStatus.Ready -> {
            val journeys = remember(s.plan) { c.transit.journeys(s.plan) }
            if (journeys.isEmpty()) {
                Text(stringResource(R.string.transit_none_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.transit_none_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                Credit()
            } else {
                val startName = when (val st = plan.start) {
                    RouteStart.MyLocation -> stringResource(R.string.route_my_location)
                    is RouteStart.At -> st.place.name
                }
                val endName = plan.destination?.name.orEmpty()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(journeys) { i, j ->
                        JourneyCard(j, selected = i == state.selected, expanded = i == state.selected && state.expanded, startName, endName) { c.transit.select(i) }
                    }
                    item { Credit() }
                }
            }
        }
    }
}

@Composable
private fun Credit() {
    val context = LocalContext.current
    Text(
        stringResource(R.string.transit_credit),
        style = MaterialTheme.typography.bodySmall,
        color = CtrlColors.Muted,
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                }
            }
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun JourneyCard(j: Itinerary, selected: Boolean, expanded: Boolean, startName: String, endName: String, onClick: () -> Unit) {
    val fmt = rememberFmt()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Raised.copy(alpha = if (selected) 1f else 0.5f))
            .border(if (selected) 2.dp else 1.dp, if (selected) CtrlColors.AccentText else CtrlColors.Outline, CtrlShapes.Control)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${time(j.start)} – ${time(j.end)}", style = CtrlType.Data.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = CtrlColors.Text)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    j.legs.filterNot { it.stayOnBoard }.forEachIndexed { i, leg ->
                        if (i > 0) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = CtrlColors.Muted)
                        if (leg.kind.isTransit) LineBadge(leg) else Icon(legIcon(leg.kind), null, Modifier.size(18.dp), tint = CtrlColors.Muted)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt.duration(j.durationSeconds.toDouble()), style = MaterialTheme.typography.titleMedium)
                val sub = if (j.transitLegs.isEmpty()) stringResource(R.string.transit_walk_only)
                else if (j.transfers == 0) stringResource(R.string.transit_direct)
                else pluralStringResource(R.plurals.transit_transfers, j.transfers, j.transfers)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            }
        }
        val first = j.transitLegs.firstOrNull()
        if (!expanded && first != null) {
            Spacer(Modifier.height(6.dp))
            val platform = first.from.track ?: first.from.scheduledTrack
            Text(
                if (platform != null) stringResource(R.string.transit_first_from_platform, time(first.start), first.from.name, platform)
                else stringResource(R.string.transit_first_from, time(first.start), first.from.name),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            j.legs.forEachIndexed { i, leg ->
                LegRow(leg, if (i == 0) startName else null, if (i == j.legs.lastIndex) endName else null)
            }
        }
    }
}

/** One leg, opened: walk to where, or the ride with line, direction, platform, stops and times. */
@Composable
private fun LegRow(leg: TransitLeg, startName: String?, endName: String?) {
    val fmt = rememberFmt()
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val toName = endName ?: leg.to.name
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(time(leg.start), style = CtrlType.Data, color = CtrlColors.Text, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            if (!leg.kind.isTransit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(legIcon(leg.kind), null, Modifier.size(18.dp), tint = CtrlColors.Muted)
                    Spacer(Modifier.width(6.dp))
                    val dist = leg.distanceMeters?.let { fmt.distance(it, settings.units) }
                    Text(
                        if (dist != null) stringResource(R.string.transit_walk_to_dist, dist, fmt.duration(leg.durationSeconds.toDouble()), toName)
                        else stringResource(R.string.transit_walk_to, fmt.duration(leg.durationSeconds.toDouble()), toName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                TransitLegBody(leg, startName)
            }
        }
    }
    if (leg.kind.isTransit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(time(leg.end), style = CtrlType.Data, color = CtrlColors.Text, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.transit_get_off, toName), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                (leg.to.track ?: leg.to.scheduledTrack)?.let { Text(stringResource(R.string.transit_platform, it), style = CtrlType.Data, color = CtrlColors.Muted) }
            }
        }
    }
}

@Composable
private fun TransitLegBody(leg: TransitLeg, startName: String?) {
    val fmt = rememberFmt()
    Column {
        if (leg.stayOnBoard) {
            Text(stringResource(R.string.transit_stay_on_board), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
        val fromName = startName ?: leg.from.name
        val platform = leg.from.track ?: leg.from.scheduledTrack
        Text(fromName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (platform != null) {
            val changed = leg.from.track != null && leg.from.scheduledTrack != null && leg.from.track != leg.from.scheduledTrack
            Text(
                stringResource(R.string.transit_platform, platform) + if (changed) " · " + stringResource(R.string.transit_platform_changed) else "",
                style = CtrlType.Data,
                color = if (changed) CtrlColors.Error else CtrlColors.Muted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(leg)
            Spacer(Modifier.width(8.dp))
            Text(
                leg.headsign?.let { stringResource(R.string.transit_towards, it) } ?: leg.lineLongName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val extra = buildList {
            add(pluralStringResource(R.plurals.transit_stops, leg.intermediateStops + 1, leg.intermediateStops + 1))
            add(fmt.duration(leg.durationSeconds.toDouble()))
            leg.agency?.let { add(it) }
        }.joinToString(" · ")
        Text(extra, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        when {
            leg.cancelled -> Text(stringResource(R.string.transit_cancelled), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Error)
            (leg.delayedMinutes ?: 0L) > 0L -> Text(stringResource(R.string.transit_delay, leg.delayedMinutes ?: 0L), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Error)
            leg.realTime -> Text(stringResource(R.string.transit_on_time), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        }
    }
}

/** The line's name on its own colour, like the signs at the stop. */
@Composable
private fun LineBadge(leg: TransitLeg) {
    val bg = leg.color?.let { Color(it) } ?: CtrlColors.Text
    val fg = leg.textColor?.let { Color(it) } ?: if (leg.color == null) CtrlColors.Background else readableOn(bg)
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(legIcon(leg.kind), null, Modifier.size(14.dp), tint = fg)
        val line = leg.line
        if (line != null) {
            Spacer(Modifier.width(4.dp))
            Text(line, style = CtrlType.LabelStrong, color = fg, maxLines = 1)
        }
    }
}

private fun readableOn(bg: Color): Color {
    val l = 0.2126 * bg.red + 0.7152 * bg.green + 0.0722 * bg.blue
    return if (l > 0.55) Color(0xFF0B0B0D) else Color.White
}

private fun legIcon(kind: LegKind): ImageVector = when (kind) {
    LegKind.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    LegKind.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    LegKind.CAR -> Icons.Filled.DirectionsCar
    LegKind.BUS, LegKind.COACH -> Icons.Filled.DirectionsBus
    LegKind.TRAM -> Icons.Filled.Tram
    LegKind.SUBWAY -> Icons.Filled.DirectionsSubway
    LegKind.RAIL -> Icons.Filled.Train
    LegKind.FERRY -> Icons.Filled.DirectionsBoat
    LegKind.AIRPLANE -> Icons.Filled.Flight
    LegKind.CABLE, LegKind.OTHER -> Icons.Filled.DirectionsTransit
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** A clock time in the phone's time zone. */
private fun time(i: Instant): String = timeFormat.format(i.atZone(ZoneId.systemDefault()))

/** "14:30", or "Tue 14:30" when it isn't today. */
private fun dayTime(i: Instant): String {
    val z = i.atZone(ZoneId.systemDefault())
    return if (z.toLocalDate() == LocalDate.now()) time(i)
    else z.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + time(i)
}
