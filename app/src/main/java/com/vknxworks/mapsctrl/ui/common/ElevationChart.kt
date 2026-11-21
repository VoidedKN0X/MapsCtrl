package com.vknxworks.mapsctrl.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.geo.ElevationProfile
import com.vknxworks.mapsctrl.routing.Units
import com.vknxworks.mapsctrl.ui.theme.CtrlColors
import com.vknxworks.mapsctrl.ui.theme.CtrlType
import kotlin.math.roundToInt

/** Climb, descent, lowest and highest point, and the profile drawn as a filled line. */
@Composable
fun ElevationChart(profile: ElevationProfile, units: Units, modifier: Modifier = Modifier) {
    val fmt = rememberFmt()
    val p = remember(profile) { profile.downsample(240) }
    val up = fmt.height(profile.ascent, units)
    val down = fmt.height(profile.descent, units)
    val lo = fmt.height(profile.min.toDouble(), units)
    val hi = fmt.height(profile.max.toDouble(), units)
    val summary = stringResource(R.string.elevation_summary, up, down, lo, hi)
    Column(modifier) {
        Row {
            Text(stringResource(R.string.elevation_up, up), style = CtrlType.Data, color = CtrlColors.Text)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.elevation_down, down), style = CtrlType.Data, color = CtrlColors.Text)
            Spacer(Modifier.weight(1f))
            Text("$lo – $hi", style = CtrlType.Data, color = CtrlColors.Muted)
        }
        Spacer(Modifier.height(6.dp))
        val line = CtrlColors.Text
        val fill = CtrlColors.Muted.copy(alpha = 0.25f)
        val grid = CtrlColors.Divider
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = summary }) {
            val len = p.length.coerceAtLeast(1.0)
            val min = p.min
            val span = (p.max - min).coerceAtLeast(20f)
            fun x(d: Double) = (d / len * size.width).toFloat()
            fun y(h: Float) = size.height - (h - min) / span * size.height * 0.9f - size.height * 0.05f
            drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            val path = Path()
            val area = Path()
            area.moveTo(0f, size.height)
            for (i in p.heights.indices) {
                val px = x(p.distances[i])
                val py = y(p.heights[i])
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                area.lineTo(px, py)
            }
            area.lineTo(x(p.distances.last()), size.height)
            area.close()
            drawPath(area, fill)
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        }
        Row {
            Text(fmt.distance(0.0, units), style = CtrlType.Label, color = CtrlColors.Muted)
            Spacer(Modifier.weight(1f))
            Text(fmt.distance(p.length, units), style = CtrlType.Label, color = CtrlColors.Muted)
        }
    }
}

/** Metres or feet, rounded: "120 m", "394 ft". */
fun Fmt.height(meters: Double, units: Units): String = when (units) {
    Units.METRIC -> "${meters.roundToInt()} m"
    Units.IMPERIAL -> "${(meters * 3.28084).roundToInt()} ft"
}
