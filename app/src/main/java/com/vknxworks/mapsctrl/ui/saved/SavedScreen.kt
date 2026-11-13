package com.vknxworks.mapsctrl.ui.saved

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.saved.SavedPlace
import com.vknxworks.mapsctrl.saved.SavedTrack
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.ConfirmDialog
import com.vknxworks.mapsctrl.ui.components.CtrlButton
import com.vknxworks.mapsctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.Group
import com.vknxworks.mapsctrl.ui.components.GroupRow
import com.vknxworks.mapsctrl.ui.components.NameDialog
import com.vknxworks.mapsctrl.ui.components.TabTitle
import com.vknxworks.mapsctrl.ui.components.bottomClearance
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Saved places and tracks, track recording, and GPX import and export. */
@Composable
fun SavedScreen(onShow: (Place) -> Unit, onShowTrack: (List<LatLon>) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val fmt = rememberFmt()
    val places by c.saved.places.collectAsStateWithLifecycle()
    val tracks by c.saved.tracks.collectAsStateWithLifecycle()
    val recording by c.recorder.recording.collectAsStateWithLifecycle()
    val recPoints by c.recorder.points.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val fix by c.location.fix.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<SavedPlace?>(null) }
    var renamingTrack by remember { mutableStateOf<SavedTrack?>(null) }
    var deleting by remember { mutableStateOf<SavedPlace?>(null) }
    var deletingTrack by remember { mutableStateOf<SavedTrack?>(null) }
    var exportOnly by remember { mutableStateOf<SavedTrack?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching { c.saved.importGpx(uri) }.getOrNull()
            Toast.makeText(
                context,
                if (r != null) context.getString(R.string.gpx_imported, r.first, r.second) else context.getString(R.string.gpx_import_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val only = exportOnly
        exportOnly = null
        if (uri != null) scope.launch {
            val ok = runCatching {
                if (only != null) c.saved.exportGpx(uri, emptyList(), listOf(only)) else c.saved.exportGpx(uri)
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.gpx_exported else R.string.gpx_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).bottomClearance()) {
        TabTitle(stringResource(R.string.tab_saved))

        Group(stringResource(R.string.group_recording), footer = if (!c.location.hasPermission()) stringResource(R.string.recording_needs_location) else null) {
            if (recording) {
                val len = recPoints.zipWithNext().sumOf { (a, b) -> Geo.distance(a, b) }
                GroupRow(
                    stringResource(R.string.recording_now),
                    stringResource(R.string.recording_stats, recPoints.size, fmt.distance(len, settings.units)),
                    icon = Icons.Filled.FiberManualRecord,
                    iconTint = CtrlColors.Error,
                    trailing = { CtrlSquareButton(Icons.Outlined.Stop, stringResource(R.string.recording_stop), onClick = { c.recorder.stop() }) },
                )
            } else {
                GroupRow(
                    stringResource(R.string.recording_start),
                    stringResource(R.string.recording_subtitle),
                    icon = Icons.Filled.FiberManualRecord,
                    iconTint = CtrlColors.AccentText,
                    onClick = {
                        if (!c.recorder.start()) Toast.makeText(context, R.string.recording_needs_location, Toast.LENGTH_LONG).show()
                    },
                )
            }
        }

        Group(stringResource(R.string.group_places), footer = if (places.isEmpty()) stringResource(R.string.places_empty) else null) {
            for (p in places.sortedByDescending { it.created }) {
                val sub = listOfNotNull(p.category, fix?.let { fmt.distance(Geo.distance(it.point, p.point), settings.units) } ?: p.point.format()).joinToString(" · ")
                GroupRow(
                    p.name,
                    sub,
                    icon = Icons.Outlined.Bookmark,
                    iconTint = CtrlColors.AccentText,
                    onClick = { onShow(p.toPlace()) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.rename), onClick = { renaming = p }, size = 40.dp)
                            CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { deleting = p }, size = 40.dp)
                        }
                    },
                )
            }
        }

        Group(stringResource(R.string.group_tracks), footer = if (tracks.isEmpty()) stringResource(R.string.tracks_empty) else null) {
            for (t in tracks.sortedByDescending { it.created }) {
                GroupRow(
                    t.name,
                    stringResource(R.string.track_subtitle, fmt.distance(t.lengthMeters, settings.units), DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.created))),
                    icon = Icons.Outlined.Timeline,
                    onClick = { onShowTrack(t.segments.flatten()) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CtrlSquareButton(
                                if (t.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                stringResource(if (t.visible) R.string.track_hide else R.string.track_show),
                                onClick = { scope.launch { c.saved.updateTrack(t.copy(visible = !t.visible)) } },
                                size = 40.dp,
                            )
                            CtrlSquareButton(Icons.Outlined.FileUpload, stringResource(R.string.gpx_export_one), onClick = { exportOnly = t; exporter.launch(safeName(t.name) + ".gpx") }, size = 40.dp)
                            CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.rename), onClick = { renamingTrack = t }, size = 40.dp)
                            CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { deletingTrack = t }, size = 40.dp)
                        }
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CtrlButton(stringResource(R.string.gpx_import), onClick = { importer.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) }, icon = Icons.Outlined.FileDownload, modifier = Modifier.weight(1f))
            CtrlSecondaryButton(onClick = { exportOnly = null; exporter.launch("MapsCtrl.gpx") }, enabled = places.isNotEmpty() || tracks.isNotEmpty(), modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Outlined.FileUpload, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.gpx_export))
            }
        }
    }

    renaming?.let { p ->
        NameDialog(stringResource(R.string.rename), p.name, onDismiss = { renaming = null }) { name ->
            scope.launch { c.saved.updatePlace(p.copy(name = name)) }
            renaming = null
        }
    }
    renamingTrack?.let { t ->
        NameDialog(stringResource(R.string.rename), t.name, onDismiss = { renamingTrack = null }) { name ->
            scope.launch { c.saved.updateTrack(t.copy(name = name)) }
            renamingTrack = null
        }
    }
    deleting?.let { p ->
        ConfirmDialog(stringResource(R.string.delete_place_title, p.name), stringResource(R.string.delete_saved_body), stringResource(R.string.delete), onDismiss = { deleting = null }) {
            scope.launch { c.saved.deletePlace(p.id) }
            deleting = null
        }
    }
    deletingTrack?.let { t ->
        ConfirmDialog(stringResource(R.string.delete_place_title, t.name), stringResource(R.string.delete_saved_body), stringResource(R.string.delete), onDismiss = { deletingTrack = null }) {
            scope.launch { c.saved.deleteTrack(t.id) }
            deletingTrack = null
        }
    }
}

private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9 ._-]"), "_").take(60).ifBlank { "track" }
