package com.vknxworks.mapsctrl.ui.common

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.nav.ResourceWords
import com.vknxworks.mapsctrl.routing.Instructions
import com.vknxworks.mapsctrl.routing.Units
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Formatting for the screen, in the phone's language and the units chosen in Settings. */
class Fmt(private val context: Context) {
    val words = ResourceWords(context)

    fun distance(meters: Double, units: Units): String = Instructions.displayDistance(words, meters, units)

    /** "1 h 05 min", "12 min", "< 1 min". */
    fun duration(seconds: Double): String {
        val min = (seconds / 60.0).roundToInt()
        return when {
            seconds < 30 -> context.getString(R.string.duration_less_than_min)
            min < 60 -> context.getString(R.string.duration_min, min)
            else -> context.getString(R.string.duration_h_min, min / 60, "%02d".format(min % 60))
        }
    }

    /** The clock time [seconds] from now: "14:32". */
    fun arrival(seconds: Double): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (seconds * 1000).toLong()))

    fun bytes(n: Long): String = Formatter.formatShortFileSize(context, n)

    /** Speed in km/h or mph, rounded. */
    fun speed(mps: Float, units: Units): String = when (units) {
        Units.METRIC -> (mps * 3.6f).roundToInt().toString()
        Units.IMPERIAL -> (mps * 2.23694f).roundToInt().toString()
    }

    fun speedUnit(units: Units): String = context.getString(if (units == Units.METRIC) R.string.unit_kmh else R.string.unit_mph)
}

@Composable
fun rememberFmt(): Fmt {
    val context = LocalContext.current
    return remember(context) { Fmt(context) }
}
