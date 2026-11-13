package com.vknxworks.mapsctrl.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.container
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.search.Place
import com.vknxworks.mapsctrl.search.QuickCategory
import com.vknxworks.mapsctrl.search.SearchResult
import com.vknxworks.mapsctrl.ui.common.rememberFmt
import com.vknxworks.mapsctrl.ui.components.CtrlSquareButton
import com.vknxworks.mapsctrl.ui.components.CtrlTextField
import com.vknxworks.mapsctrl.ui.components.MessageView
import com.vknxworks.mapsctrl.ui.components.topDivider
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlShapes
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Offline search: names, addresses, categories and coordinates, from your downloaded search data. */
@Composable
fun SearchScreen(
    state: HomeState,
    near: LatLon,
    onPick: (Place) -> Unit,
    onShowAll: () -> Unit,
    title: String? = null,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var busy by remember { mutableStateOf(false) }
    val hasData by produceState(true) { value = c.search.hasSearchData() }
    val savedPlaces by c.saved.places.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.query) {
        val q = state.query
        if (q.isBlank()) { state.results = emptyList(); return@LaunchedEffect }
        delay(250)
        busy = true
        val saved = savedPlaces.filter { it.name.contains(q.trim(), ignoreCase = true) }
            .map { SearchResult(it.toPlace(), Geo.distance(near, it.point)) }
        state.results = saved + c.search.search(q, near)
        busy = false
    }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = { state.searchOpen = false })
            Spacer(Modifier.width(10.dp))
            CtrlTextField(
                value = state.query,
                onValueChange = { state.query = it },
                placeholder = { Text(title ?: stringResource(R.string.search_hint), maxLines = 1) },
                singleLine = true,
                trailingIcon = if (state.query.isNotEmpty()) {
                    { Icon(Icons.Outlined.Close, stringResource(R.string.clear), Modifier.clickable { state.query = "" }.padding(8.dp)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.results.firstOrNull()?.let { onPick(it.place) } }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        // Quick categories.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (cat in QuickCategory.entries) {
                Chip(stringResource(categoryLabel(cat))) {
                    scope.launch {
                        busy = true
                        state.query = ""
                        delay(300) // after the query effect has cleared the list
                        state.results = c.search.category(cat, near)
                        busy = false
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        when {
            busy && state.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CtrlColors.AccentText)
            }
            !hasData && state.results.isEmpty() -> MessageView(
                Icons.Outlined.SearchOff,
                stringResource(R.string.search_no_data_title),
                stringResource(R.string.search_no_data_body),
            )
            state.results.isEmpty() && state.query.isNotBlank() && !busy -> MessageView(
                Icons.Outlined.SearchOff,
                stringResource(R.string.search_nothing_title),
                stringResource(R.string.search_nothing_body),
            )
            else -> ResultList(state.results, onPick, onShowAll)
        }
    }
}

@Composable
private fun ResultList(results: List<SearchResult>, onPick: (Place) -> Unit, onShowAll: () -> Unit) {
    val fmt = rememberFmt()
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (results.size > 1) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onShowAll).padding(horizontal = Gutter, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Map, null, Modifier.size(20.dp), tint = CtrlColors.AccentText)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.search_show_on_map, results.size), style = MaterialTheme.typography.labelLarge, color = CtrlColors.AccentText)
                }
            }
        }
        items(results, key = { it.place.savedId ?: (it.place.name + it.place.point) }) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .topDivider()
                    .clickable { onPick(r.place) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = Gutter, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Place, null, Modifier.size(22.dp), tint = if (r.place.savedId != null) CtrlColors.AccentText else CtrlColors.Muted)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(r.place.category, r.place.address()).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                r.distance?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(fmt.distance(it, settings.units), style = CtrlType.Data, color = CtrlColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = CtrlColors.Text,
        modifier = Modifier
            .clip(CtrlShapes.Chip)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

fun categoryLabel(cat: QuickCategory): Int = when (cat) {
    QuickCategory.FOOD -> R.string.cat_food
    QuickCategory.FUEL -> R.string.cat_fuel
    QuickCategory.CHARGING -> R.string.cat_charging
    QuickCategory.PARKING -> R.string.cat_parking
    QuickCategory.SUPERMARKET -> R.string.cat_supermarket
    QuickCategory.HOTEL -> R.string.cat_hotel
    QuickCategory.PHARMACY -> R.string.cat_pharmacy
    QuickCategory.ATM -> R.string.cat_atm
    QuickCategory.TOILETS -> R.string.cat_toilets
    QuickCategory.TRANSPORT -> R.string.cat_transport
}
