package com.vknxworks.mapsctrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import com.vknxworks.mapsctrl.ui.theme.Gutter

/** A small stat: mono label above a value. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    CtrlCard(modifier, padding = PaddingValues(14.dp)) {
        SectionLabel(label)
        Spacer(Modifier.size(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text, maxLines = 1)
        if (sub != null) Text(sub, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1)
    }
}

/** Two stat tiles side by side. */
@Composable
fun StatRow(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}
