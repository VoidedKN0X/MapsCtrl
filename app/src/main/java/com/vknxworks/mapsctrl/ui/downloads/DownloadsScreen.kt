package com.vknxworks.mapsctrl.ui.downloads

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Update
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.data.CatalogItem
import com.vknxworks.mapsctrl.data.DirectoryIndex
import com.vknxworks.mapsctrl.data.DownloadState
import com.vknxworks.mapsctrl.data.InstalledMap
import com.vknxworks.mapsctrl.data.TerrainJob
import com.vknxworks.mapsctrl.data.TerrainRegion
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.ConfirmDialog
import com.vknxworks.mapsctrl.ui.components.CtrlButton
import com.vknxworks.mapsctrl.ui.components.CtrlProgressBar
import com.vknxworks.mapsctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.Group
import com.vknxworks.mapsctrl.ui.components.GroupRow
import com.vknxworks.mapsctrl.ui.components.GroupSwitch
import com.vknxworks.mapsctrl.ui.components.MessageView
import com.vknxworks.mapsctrl.ui.components.StatRow
import com.vknxworks.mapsctrl.ui.components.StatTile
import com.vknxworks.mapsctrl.ui.components.TabTitle
import com.vknxworks.mapsctrl.ui.components.bottomClearance
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** Downloads: your maps (update, delete), adding maps from the server or a file, routing data and storage. */
@Composable
fun DownloadsScreen() {
    var browsePath by rememberSaveable { mutableStateOf<String?>(null) }
    val path = browsePath
    if (path != null) {
        BrowseScreen(path, onOpen = { browsePath = it }, onBack = { browsePath = if (path.isEmpty()) null else parentOf(path) })
    } else {
        InstalledScreen(onAdd = { browsePath = "" })
    }
}

private fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    return if (!trimmed.contains('/')) "" else trimmed.substringBeforeLast('/') + "/"
}

@Composable
private fun InstalledScreen(onAdd: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val fmt = rememberFmt()
    val installed by c.maps.installed.collectAsStateWithLifecycle()
    val downloads by c.maps.downloads.collectAsStateWithLifecycle()
    val updates by c.maps.updates.collectAsStateWithLifecycle()
    val segments by c.segments.files.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<InstalledMap?>(null) }
    var deletingTile by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            Toast.makeText(context, R.string.import_started, Toast.LENGTH_SHORT).show()
            val name = c.maps.import(uri)
            Toast.makeText(context, if (name != null) context.getString(R.string.import_done, name) else context.getString(R.string.import_not_map), Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).bottomClearance()) {
        TabTitle(stringResource(R.string.tab_downloads))
        val mapBytes = installed.sumOf { it.mapBytes + it.poiBytes }
        val free = remember(installed, segments) { context.filesDir.usableSpace }
        StatRow(
            { m -> StatTile(stringResource(R.string.stat_maps), fmt.bytes(mapBytes), m, sub = pluralStringResource(R.plurals.stat_maps_count, installed.size, installed.size)) },
            { m -> StatTile(stringResource(R.string.stat_routing), fmt.bytes(segments.sumOf { it.bytes }), m, sub = stringResource(R.string.stat_free, fmt.bytes(free))) },
        )

        // Maps from MapsCtrl 0.2.0, which this version can't read.
        var legacy by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
        var confirmLegacy by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { legacy = c.maps.legacyMaps() }
        if (legacy.isNotEmpty()) {
            Group(stringResource(R.string.legacy_label), footer = stringResource(R.string.legacy_footer)) {
                GroupRow(
                    stringResource(R.string.legacy_title),
                    stringResource(R.string.legacy_subtitle, legacy.size, fmt.bytes(legacy.sumOf { it.length() })),
                    icon = Icons.Outlined.Delete,
                    onClick = { confirmLegacy = true },
                )
            }
        }
        if (confirmLegacy) {
            ConfirmDialog(
                title = stringResource(R.string.legacy_title),
                body = stringResource(R.string.legacy_confirm),
                confirm = stringResource(R.string.delete),
                onDismiss = { confirmLegacy = false },
                onConfirm = {
                    confirmLegacy = false
                    scope.launch { c.maps.deleteLegacyMaps(); legacy = c.maps.legacyMaps() }
                },
            )
        }

        // Downloads still running for maps not yet installed.
        val pending = downloads.filterKeys { k -> installed.none { it.path == k } }
        Group(stringResource(R.string.group_your_maps), footer = if (installed.isEmpty() && pending.isEmpty()) stringResource(R.string.your_maps_empty) else null) {
            for ((p, st) in pending) DownloadRow(DirectoryIndex.displayName(p.substringAfterLast('/')), p, st)
            for (m in installed) {
                val st = downloads[m.path]
                if (st != null) {
                    DownloadRow(m.name, m.path, st)
                } else {
                    val sub = buildString {
                        append(stringResource(R.string.map_size, fmt.bytes(m.mapBytes)))
                        if (m.poiBytes > 0) append(" · ").append(stringResource(R.string.search_size, fmt.bytes(m.poiBytes)))
                        if (m.imported) append(" · ").append(stringResource(R.string.imported))
                    }
                    GroupRow(
                        m.name,
                        sub,
                        icon = Icons.Outlined.Map,
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (m.path in updates) CtrlSquareButton(Icons.Outlined.Update, stringResource(R.string.update), onClick = { c.maps.update(m, settings.withSearchData) }, tint = CtrlColors.AccentText, size = 40.dp)
                                CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { deleting = m }, size = 40.dp)
                            }
                        },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CtrlButton(stringResource(R.string.add_maps), onClick = onAdd, icon = Icons.Outlined.Public, modifier = Modifier.weight(1f))
            CtrlSecondaryButton(onClick = { importer.launch(arrayOf("application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_map), maxLines = 1)
            }
        }
        if (installed.any { !it.imported }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    checking = true
                    scope.launch {
                        c.maps.checkUpdates()
                        checking = false
                        if (c.maps.updates.value.isEmpty()) Toast.makeText(context, R.string.updates_none, Toast.LENGTH_SHORT).show()
                    }
                }, enabled = !checking) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (checking) R.string.updates_checking else R.string.updates_check))
                }
            }
        }

        Group(stringResource(R.string.group_download_options)) {
            GroupSwitch(
                stringResource(R.string.with_search_data),
                stringResource(R.string.with_search_data_subtitle),
                checked = settings.withSearchData,
                onChange = { v -> scope.launch { c.settings.update { it.copy(withSearchData = v) } } },
            )
        }

        Group(stringResource(R.string.group_routing_data), footer = stringResource(R.string.routing_data_footer)) {
            if (segments.isEmpty()) GroupRow(stringResource(R.string.routing_data_empty), icon = Icons.Outlined.Route)
            for (s in segments) {
                GroupRow(
                    s.tile.replace('_', ' '),
                    fmt.bytes(s.bytes),
                    icon = Icons.Outlined.Route,
                    trailing = { CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { deletingTile = s.tile }, size = 40.dp) },
                )
            }
        }
        TerrainGroup()
        Text(
            stringResource(R.string.downloads_sources),
            style = MaterialTheme.typography.bodySmall,
            color = CtrlColors.Muted,
            modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
        )
    }

    deleting?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.delete_map_title, m.name),
            body = stringResource(R.string.delete_map_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = { scope.launch { c.maps.delete(m.path) }; deleting = null },
        )
    }
    deletingTile?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.delete_routing_title, t),
            body = stringResource(R.string.delete_routing_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { deletingTile = null },
            onConfirm = { scope.launch { c.segments.delete(t) }; deletingTile = null },
        )
    }
}

/** Terrain data for hillshading, per downloaded map: download (after showing the size), progress, delete. */
@Composable
private fun TerrainGroup() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val fmt = rememberFmt()
    val installed by c.maps.installed.collectAsStateWithLifecycle()
    val tiles by c.terrain.files.collectAsStateWithLifecycle()
    val job by c.terrain.job.collectAsStateWithLifecycle()
    var regions by remember { mutableStateOf<List<TerrainRegion>>(emptyList()) }
    var deleting by remember { mutableStateOf<TerrainRegion?>(null) }
    LaunchedEffect(installed, tiles) { regions = c.terrain.regions(installed.map { it to c.maps.mapFile(it.path) }) }

    Group(stringResource(R.string.group_terrain), footer = stringResource(R.string.terrain_footer, fmt.bytes(tiles.values.sum()))) {
        if (regions.isEmpty()) GroupRow(stringResource(R.string.terrain_needs_map), icon = Icons.Outlined.Terrain)
        for (r in regions) {
            val j = job
            val mine = when (j) {
                is TerrainJob.Sizing -> j.mapPath == r.mapPath
                is TerrainJob.Confirm -> j.mapPath == r.mapPath
                is TerrainJob.Running -> j.mapPath == r.mapPath
                is TerrainJob.Failed -> j.mapPath == r.mapPath
                null -> false
            }
            val sub = when {
                mine && j is TerrainJob.Sizing -> stringResource(R.string.terrain_checking)
                mine && j is TerrainJob.Running -> stringResource(R.string.terrain_downloading, j.done, j.total, fmt.bytes(j.bytes))
                mine && j is TerrainJob.Failed -> stringResource(R.string.download_failed, j.message)
                r.tooBig -> stringResource(R.string.terrain_too_big)
                r.complete -> stringResource(R.string.terrain_complete, pluralStringResource(R.plurals.terrain_tiles, r.tiles.size, r.tiles.size))
                r.have > 0 -> stringResource(R.string.terrain_partial, r.have, r.tiles.size)
                else -> pluralStringResource(R.plurals.terrain_tiles, r.tiles.size, r.tiles.size)
            }
            GroupRow(
                r.mapName,
                sub,
                icon = if (mine && j is TerrainJob.Failed) Icons.Outlined.ErrorOutline else Icons.Outlined.Terrain,
                iconTint = if (mine && j is TerrainJob.Failed) CtrlColors.Error else CtrlColors.Text,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            mine && (j is TerrainJob.Running || j is TerrainJob.Sizing) ->
                                CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.cancel), onClick = { c.terrain.cancel() }, size = 40.dp)
                            mine && j is TerrainJob.Failed ->
                                CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = { c.terrain.dismiss() }, size = 40.dp)
                            else -> {
                                if (!r.complete && !r.tooBig) {
                                    CtrlSquareButton(Icons.Outlined.Download, stringResource(R.string.download), onClick = { c.terrain.prepare(r) }, enabled = j == null || j is TerrainJob.Failed, size = 40.dp)
                                }
                                if (r.have > 0) CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { deleting = r }, size = 40.dp)
                            }
                        }
                    }
                },
            )
            if (mine && j is TerrainJob.Running) {
                Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 12.dp)) {
                    CtrlProgressBar(if (j.total > 0) j.done.toFloat() / j.total else 0f, color = CtrlColors.Accent)
                }
            }
        }
    }

    (job as? TerrainJob.Confirm)?.let { cf ->
        val name = regions.firstOrNull { it.mapPath == cf.mapPath }?.mapName ?: ""
        com.vknxworks.mapsctrl.ui.components.CtrlAlertDialog(
            onDismissRequest = { c.terrain.dismiss() },
            title = { Text(stringResource(R.string.terrain_confirm_title, name), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.terrain_confirm_body, pluralStringResource(R.plurals.terrain_tiles, cf.tiles.size, cf.tiles.size), fmt.bytes(cf.bytes)), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { c.terrain.download() }, enabled = cf.tiles.isNotEmpty()) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton(onClick = { c.terrain.dismiss() }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
    deleting?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.terrain_delete_title, r.mapName),
            body = stringResource(R.string.terrain_delete_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = { scope.launch { c.terrain.delete(r, regions) }; deleting = null },
        )
    }
}

@Composable
private fun DownloadRow(name: String, path: String, st: DownloadState) {
    val c = LocalContext.current.container
    val fmt = rememberFmt()
    when (st) {
        is DownloadState.Running -> GroupRow(
            name,
            null,
            icon = Icons.Outlined.Download,
            trailing = { CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.cancel), onClick = { c.maps.cancel(path) }, size = 40.dp) },
        ).also {
            Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 12.dp)) {
                CtrlProgressBar(if (st.total > 0) st.done.toFloat() / st.total else 0f, color = CtrlColors.Accent)
                Spacer(Modifier.height(4.dp))
                val what = stringResource(if (st.searchData) R.string.downloading_search else R.string.downloading_map)
                Text(
                    "$what  " + (if (st.total > 0) "${fmt.bytes(st.done)} / ${fmt.bytes(st.total)}" else fmt.bytes(st.done)),
                    style = CtrlType.Data,
                    color = CtrlColors.Muted,
                )
            }
        }
        is DownloadState.Failed -> GroupRow(
            name,
            stringResource(R.string.download_failed, st.message),
            icon = Icons.Outlined.ErrorOutline,
            iconTint = CtrlColors.Error,
            trailing = { CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = { c.maps.dismissError(path) }, size = 40.dp) },
        )
    }
}

/** The download server's folders and regions. */
@Composable
private fun BrowseScreen(path: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val fmt = rememberFmt()
    val installed by c.maps.installed.collectAsStateWithLifecycle()
    val downloads by c.maps.downloads.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var items by remember(path) { mutableStateOf<List<CatalogItem>?>(null) }
    var error by remember(path) { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf<CatalogItem?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(path, reload) {
        error = false
        items = null
        runCatching { c.maps.browse(path) }.onSuccess { items = it }.onFailure { error = true }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (path.isEmpty()) stringResource(R.string.add_maps) else DirectoryIndex.displayName(path.trimEnd('/').substringAfterLast('/')),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.browse_source), style = CtrlType.Data, color = CtrlColors.Muted)
            }
        }
        val list = items
        when {
            error -> MessageView(Icons.Outlined.CloudOff, stringResource(R.string.browse_error_title), stringResource(R.string.browse_error_body), actionLabel = stringResource(R.string.try_again), onAction = { reload++ })
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(list, key = { it.path }) { item ->
                    val have = installed.any { it.path == item.path }
                    val st = downloads[item.path]
                    when {
                        item.isFolder -> GroupRow(item.name, icon = Icons.Outlined.Folder, showChevron = true, onClick = { onOpen(item.path + "/") })
                        st != null -> DownloadRow(item.name, item.path, st)
                        else -> {
                            val size = listOfNotNull(
                                item.mapBytes?.let { stringResource(R.string.map_size, "~" + fmt.bytes(it)) },
                                item.poiBytes?.takeIf { settings.withSearchData }?.let { stringResource(R.string.search_size, "~" + fmt.bytes(it)) },
                            ).joinToString(" · ")
                            GroupRow(
                                item.name,
                                size,
                                icon = Icons.Outlined.Map,
                                onClick = if (have) null else ({ confirm = item }),
                                trailing = {
                                    if (have) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.downloaded), tint = CtrlColors.AccentText)
                                    else Icon(Icons.Outlined.Download, stringResource(R.string.download), tint = CtrlColors.Text)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    confirm?.let { item ->
        val total = (item.mapBytes ?: 0) + if (settings.withSearchData) (item.poiBytes ?: 0) else 0
        com.vknxworks.mapsctrl.ui.components.CtrlAlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.download_confirm_title, item.name), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.download_confirm_body, fmt.bytes(total)), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { c.maps.download(item, settings.withSearchData); confirm = null }) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}
