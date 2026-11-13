package com.vknxworks.mapsctrl.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.CtrlButton
import com.vknxworks.mapsctrl.ui.components.CtrlCard
import com.vknxworks.mapsctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.SectionLabel
import com.vknxworks.mapsctrl.ui.components.topDivider
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** The place sheet: name, category, address, details from the OSM tags, and Route to / Save / Share. */
@Composable
fun PlaceSheet(place: Place, onClose: () -> Unit, onRoute: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val fmt = rememberFmt()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val fix by c.location.fix.collectAsStateWithLifecycle()
    val saved by c.saved.places.collectAsStateWithLifecycle()
    val savedEntry = saved.firstOrNull { it.id == place.savedId } ?: saved.firstOrNull { it.point == place.point && it.name == place.name }
    var expanded by remember(place) { mutableStateOf(false) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp).heightIn(max = maxHeight)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val label = place.category ?: place.kind()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
                    ?: if (savedEntry != null) stringResource(R.string.saved_place) else stringResource(R.string.place)
                SectionLabel(label)
                Spacer(Modifier.size(4.dp))
                Text(place.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    place.address(),
                    fix?.let { fmt.distance(Geo.distance(it.point, place.point), settings.units) },
                ).joinToString("  ·  ")
                if (sub.isNotEmpty()) {
                    Spacer(Modifier.size(2.dp))
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = onClose, size = 40.dp)
        }
        Spacer(Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlButton(stringResource(R.string.route_to), onClick = onRoute, icon = Icons.Outlined.Directions, modifier = Modifier.weight(1f))
            CtrlSecondaryButton(onClick = {
                scope.launch {
                    if (savedEntry != null) {
                        c.saved.deletePlace(savedEntry.id)
                        Toast.makeText(context, R.string.place_removed, Toast.LENGTH_SHORT).show()
                    } else {
                        c.saved.save(place)
                        Toast.makeText(context, R.string.place_saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(if (savedEntry != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null, Modifier.size(20.dp), tint = if (savedEntry != null) CtrlColors.AccentText else CtrlColors.Text)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (savedEntry != null) R.string.saved else R.string.save))
            }
            CtrlSquareButton(Icons.Outlined.Share, stringResource(R.string.share), onClick = { share(context, place) }, size = 56.dp)
        }

        val details = details(context, place)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.size(10.dp))
            DetailRow(Icons.Outlined.Place, place.point.format(), mono = true, onClick = { copy(context, place.point.format()) }, trailing = Icons.Outlined.ContentCopy)
            val shown = if (expanded) details else details.take(3)
            for (d in shown) DetailRow(d.icon, d.text, onClick = d.action?.let { a -> { a(context) } })
            if (details.size > 3 && !expanded) {
                Text(
                    stringResource(R.string.more_details, details.size - 3),
                    style = MaterialTheme.typography.labelLarge,
                    color = CtrlColors.AccentText,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

private data class Detail(val icon: ImageVector, val text: String, val action: ((Context) -> Unit)? = null)

private fun details(context: Context, p: Place): List<Detail> {
    val t = p.tags
    val out = mutableListOf<Detail>()
    t["opening_hours"]?.let { out += Detail(Icons.Outlined.AccessTime, it.replace(";", "\n").replace("; ", "\n")) }
    (t["phone"] ?: t["contact:phone"])?.let { ph -> out += Detail(Icons.Outlined.Phone, ph) { ctx -> open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.split(';').first().trim()))) } }
    (t["website"] ?: t["contact:website"] ?: t["url"])?.let { w ->
        val url = if (w.startsWith("http")) w else "https://$w"
        out += Detail(Icons.Outlined.Language, w.removePrefix("https://").removePrefix("http://").trimEnd('/')) { ctx -> open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    t["cuisine"]?.let { out += Detail(Icons.Outlined.Restaurant, it.split(';').joinToString(", ") { s -> s.trim().replace('_', ' ') }) }
    t["wheelchair"]?.let { out += Detail(Icons.Outlined.Accessible, context.getString(R.string.detail_wheelchair, it)) }
    // Everything else that's informative, as "key: value".
    val skip = setOf("name", "opening_hours", "phone", "contact:phone", "website", "contact:website", "url", "cuisine", "wheelchair", "source", "note:name")
    t.filterKeys { k -> k !in skip && !k.startsWith("addr:") && !k.startsWith("name:") && !k.startsWith("wikidata") && !k.startsWith("wikimedia") && !k.contains("wikipedia") && !k.startsWith("brand:") && !k.startsWith("source:") && !k.startsWith("check_date") }
        .toSortedMap()
        .forEach { (k, v) -> out += Detail(Icons.Outlined.Info, "${k.replace('_', ' ')}: ${v.replace('_', ' ')}") }
    return out
}

@Composable
private fun DetailRow(icon: ImageVector, text: String, mono: Boolean = false, onClick: (() -> Unit)? = null, trailing: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .topDivider()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = CtrlColors.Muted)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = if (mono) CtrlType.Data else MaterialTheme.typography.bodyMedium,
            color = if (onClick != null && !mono) CtrlColors.AccentText else CtrlColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Icon(trailing, null, Modifier.size(16.dp), tint = CtrlColors.Muted)
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(text, text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

/** Shares the place as text with a geo: link, which other map apps understand. Nothing goes online. */
private fun share(context: Context, p: Place) {
    val geo = "geo:${p.point.lat},${p.point.lon}?q=${p.point.lat},${p.point.lon}(${Uri.encode(p.name)})"
    val text = listOfNotNull(p.name, p.address(), p.point.format(), geo).joinToString("\n")
    open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}
