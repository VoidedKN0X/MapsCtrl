package com.vknxworks.mapsctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.mapsctrl.BuildConfig
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.ui.lock.AppLock
import com.vknxworks.mapsctrl.settings.BackupFormatException
import com.vknxworks.mapsctrl.settings.SettingsBackup
import com.vknxworks.mapsctrl.settings.SettingsBackups
import com.vknxworks.mapsctrl.ui.components.ConfirmDialog
import com.vknxworks.mapsctrl.ui.components.Group
import com.vknxworks.mapsctrl.ui.components.GroupRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Back up and restore settings: a JSON file in a place you pick, with MapsCtrl's settings but not the ntfy topic
 * or where the map was last. Restoring shows which settings it will replace and asks first.
 */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SettingsBackup?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = SettingsBackups.export(c.settings.settings.value, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException()
                }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.backup_saved else R.string.backup_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // A settings file is a few kilobytes; anything far bigger isn't one.
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) throw BackupFormatException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw IOException()
                }
                SettingsBackups.parse(text)
            }
            result.onSuccess { pending = it }.onFailure { e ->
                val msg = if (e is BackupFormatException && e.newer) R.string.backup_too_new else R.string.backup_not_valid
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Group(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer)) {
        GroupRow(stringResource(R.string.backup_save), stringResource(R.string.backup_save_sub), Icons.Outlined.FileDownload, onClick = {
            AppLock.expectReturn()
            save.launch("mapsctrl-settings-${LocalDate.now()}.json")
        })
        GroupRow(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_sub), Icons.Outlined.FileUpload, onClick = {
            AppLock.expectReturn()
            open.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        })
    }

    pending?.let { b ->
        val current = c.settings.settings.value
        val changes = SettingsBackups.changes(b, current)
        val next = SettingsBackups.apply(b, current)
        val names = changes.map {
            // A different ETA sharing server is shown in full: sharing turns off until you've checked it.
            if (it == "ntfy_server") stringResource(R.string.backup_ntfy_server_changed, next.ntfyServer.ifEmpty { "–" })
            else stringResource(keyLabel(it))
        }
        ConfirmDialog(
            stringResource(R.string.backup_restore_title),
            if (names.isEmpty()) stringResource(R.string.backup_restore_same, b.createdAt?.take(10) ?: "?")
            else stringResource(R.string.backup_restore_body, b.createdAt?.take(10) ?: "?", names.joinToString("\n") { "• $it" }),
            stringResource(R.string.backup_restore_confirm),
            onDismiss = { pending = null },
        ) {
            pending = null
            if (changes.isEmpty()) return@ConfirmDialog
            scope.launch {
                val before = c.settings.settings.value
                c.settings.update { SettingsBackups.apply(b, it) }
                // Recent searches switched off by the file: the list is forgotten, as with the switch itself.
                if (before.keepRecentSearches && b.settings.keepRecentSearches == false) c.saved.clearRecent()
                Toast.makeText(context, R.string.backup_restored, Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun keyLabel(key: String): Int = when (key) {
    "units" -> R.string.units
    "map_style" -> R.string.map_style
    "travel_mode" -> R.string.default_mode
    "voice" -> R.string.voice
    "speed_cameras" -> R.string.speed_cameras
    "speed_limit" -> R.string.speed_limit
    "search_data" -> R.string.with_search_data
    "ntfy" -> R.string.ntfy_title_setting
    "keep_screen_on" -> R.string.keep_screen_on
    "hide_screenshots" -> R.string.hide_screenshots
    "hide_recents" -> R.string.hide_recents
    "lock_timeout" -> R.string.lock_again
    "hillshading" -> R.string.hillshading
    "transit" -> R.string.transit_setting
    "avoid" -> R.string.route_options
    "over_speed" -> R.string.over_speed
    else -> R.string.keep_recent
}

private const val MAX_BYTES = 256 * 1024
