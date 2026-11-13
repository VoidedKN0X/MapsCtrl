package com.vknxworks.mapsctrl.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.nav.NavState
import com.vknxworks.mapsctrl.routing.Instructions
import com.vknxworks.mapsctrl.routing.SpeedLimit
import com.vknxworks.mapsctrl.routing.TurnKind
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.CtrlCard
import com.vknxworks.mapsctrl.ui.components.CtrlHeroCard
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.SectionLabel
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import com.vknxworks.mapsctrl.ui.theme.JetBrainsMono

/** Turn-by-turn: next-turn card on top; time, distance, speed and the speed limit at the bottom. */
@Composable
fun NavigationOverlay(
    state: NavState,
    settings: AppSettings,
    panned: Boolean,
    onRecenter: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    val c = LocalContext.current.container
    val fmt = rememberFmt()
    val p = state.progress
    Box(Modifier.fillMaxSize()) {
        // Keeps the status bar readable over a light map.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(CtrlColors.Background.copy(alpha = 0.85f)))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val hint = p?.nextHint
            val label = when {
                state.rerouting -> stringResource(R.string.nav_rerouting)
                state.waitingForLocation -> stringResource(R.string.nav_waiting)
                p?.offRoute == true -> stringResource(R.string.nav_off_route)
                else -> stringResource(R.string.nav_next)
            }
            CtrlHeroCard(label = label, meta = if (state.demo) stringResource(R.string.nav_demo) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.rerouting) Icons.Outlined.Sync else turnIcon(hint?.kind ?: TurnKind.STRAIGHT),
                        null,
                        Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (p != null) fmt.distance(p.distanceToNext, settings.units) else "–",
                            style = CtrlType.Readout,
                            maxLines = 1,
                        )
                        Text(
                            if (hint != null) Instructions.title(fmt.words, hint) else state.destination.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val then = p?.followingHint
                if (then != null && p.gapToFollowing < 300 && hint?.kind != TurnKind.ARRIVE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.nav_then).uppercase(), style = CtrlType.LabelStrong)
                        Spacer(Modifier.width(8.dp))
                        Icon(turnIcon(then.kind), null, Modifier.size(20.dp))
                    }
                }
            }
            p?.cameraAhead?.takeIf { settings.speedCameras }?.let { d ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(CtrlColors.Error).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(20.dp), tint = CtrlColors.OnError)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.nav_camera, fmt.distance(d, settings.units)), style = MaterialTheme.typography.titleSmall, color = CtrlColors.OnError)
                }
            }
        }

        // Right side: zoom and recenter.
        Column(Modifier.align(Alignment.CenterEnd).padding(end = Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.zoom_in), onClick = onZoomIn)
            CtrlSquareButton(Icons.Outlined.Remove, stringResource(R.string.zoom_out), onClick = onZoomOut)
            if (panned) CtrlSquareButton(Icons.Filled.MyLocation, stringResource(R.string.nav_recenter), onClick = onRecenter, tint = CtrlColors.AccentText)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val limit = p?.speedLimit?.takeIf { settings.speedLimit }
                if (limit != null) SpeedLimitSign(limit)
                val speed = state.fix?.speed
                if (speed != null) {
                    val over = limit?.kmh != null && speed * 3.6f > limit.kmh + 3
                    Column(
                        Modifier.clip(MaterialTheme.shapes.large).background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, MaterialTheme.shapes.large).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(fmt.speed(speed, settings.units), style = CtrlType.Readout.copy(fontSize = 30.sp), color = if (over) CtrlColors.Error else CtrlColors.Text)
                        Text(fmt.speedUnit(settings.units), style = CtrlType.Label, color = CtrlColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CtrlCard(Modifier.fillMaxWidth()) {
                if (p?.arrived == true) {
                    SectionLabel(stringResource(R.string.nav_arrived_label))
                    Text(state.destination.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Readout(stringResource(R.string.nav_arrival), p?.let { fmt.arrival(it.timeLeftSeconds) } ?: "–", Modifier.weight(1f))
                    Readout(stringResource(R.string.nav_time_left), p?.let { fmt.duration(it.timeLeftSeconds) } ?: "–", Modifier.weight(1f))
                    Readout(stringResource(R.string.nav_distance_left), p?.let { fmt.distance(it.distanceLeft, settings.units) } ?: "–", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtrlSquareButton(
                        if (state.muted || !settings.voice) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(if (state.muted) R.string.nav_unmute else R.string.nav_mute),
                        onClick = { c.navigation.toggleMute() },
                        size = 52.dp,
                    )
                    com.vknxworks.mapsctrl.ui.components.CtrlButton(
                        stringResource(R.string.nav_end),
                        onClick = { c.navigation.stop() },
                        icon = Icons.Outlined.Close,
                        container = CtrlColors.Raised,
                        content = CtrlColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = CtrlType.Data.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = CtrlColors.Text, maxLines = 1)
    }
}

/** A round speed-limit sign: red ring, the number in mono. */
@Composable
fun SpeedLimitSign(limit: SpeedLimit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(Color.White).border(6.dp, Color(0xFFD93636), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val v = limit.signValue
        Text(
            v?.toString() ?: "∞",
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = if ((v ?: 0) >= 100) 19.sp else 22.sp,
            color = Color(0xFF0B0B0D),
            textAlign = TextAlign.Center,
        )
    }
}

fun turnIcon(kind: TurnKind): ImageVector = when (kind) {
    TurnKind.STRAIGHT, TurnKind.BEELINE, TurnKind.OFF_ROUTE -> Icons.Filled.Straight
    TurnKind.LEFT -> Icons.Filled.TurnLeft
    TurnKind.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    TurnKind.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    TurnKind.RIGHT -> Icons.Filled.TurnRight
    TurnKind.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    TurnKind.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    TurnKind.KEEP_LEFT -> Icons.Filled.ForkLeft
    TurnKind.KEEP_RIGHT -> Icons.Filled.ForkRight
    TurnKind.U_TURN_LEFT, TurnKind.U_TURN -> Icons.Filled.UTurnLeft
    TurnKind.U_TURN_RIGHT -> Icons.Filled.UTurnRight
    TurnKind.ROUNDABOUT -> Icons.Filled.RoundaboutRight
    TurnKind.ROUNDABOUT_LEFT -> Icons.Filled.RoundaboutLeft
    TurnKind.EXIT_LEFT -> Icons.Filled.RampLeft
    TurnKind.EXIT_RIGHT -> Icons.Filled.RampRight
    TurnKind.ARRIVE -> Icons.Filled.Flag
}
