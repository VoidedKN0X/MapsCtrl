package com.vknxworks.mapsctrl.routing

import java.util.Locale
import kotlin.math.roundToInt

enum class Units { METRIC, IMPERIAL }

/** The words for turn instructions; the app fills these from strings.xml, tests from a fake. */
interface InstructionWords {
    /** The action in lower case, as in the middle of a sentence: "turn left", "take the 2nd exit". */
    fun action(kind: TurnKind, exit: Int): String

    /** "In %1$s, %2$s" -> "In 300 m, turn left". */
    fun inDistance(distance: String, action: String): String

    /** "Then %1$s" for a second turn right after the first. */
    fun then(action: String): String

    /** Distance units: "%1$d m", "%1$s km", "%1$d ft", "%1$s mi". */
    fun meters(n: Int): String
    fun kilometers(n: String): String
    fun feet(n: Int): String
    fun miles(n: String): String
}

object Instructions {

    /** "Turn left" for the next-turn card: the action with a capital letter. */
    fun title(words: InstructionWords, hint: TurnHint): String =
        words.action(hint.kind, hint.exit).replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /**
     * What the voice says [distanceM] before a turn: "In 300 m, turn left", or just "Turn left" when it's right
     * there. [next] is the turn after it when that one follows within 150 m ("..., then keep right").
     */
    fun spoken(words: InstructionWords, hint: TurnHint, distanceM: Double, units: Units, next: TurnHint? = null, nextGapM: Double = Double.MAX_VALUE): String {
        val action = words.action(hint.kind, hint.exit)
        val first = if (distanceM < 40) action.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        else words.inDistance(voiceDistance(words, distanceM, units), action)
        if (next != null && nextGapM < 150 && next.kind != TurnKind.STRAIGHT) {
            return "$first, " + words.then(words.action(next.kind, next.exit))
        }
        return first
    }

    /** A distance rounded the way it's spoken: 10 m steps under 100 m, 50 m under 1 km, then 0.1 km. */
    fun voiceDistance(words: InstructionWords, meters: Double, units: Units): String = when (units) {
        Units.METRIC -> when {
            meters < 100 -> words.meters(((meters / 10).roundToInt() * 10).coerceAtLeast(10))
            meters < 1000 -> words.meters(((meters / 50).roundToInt() * 50).coerceAtMost(950).coerceAtLeast(100))
            else -> words.kilometers(oneDecimal(meters / 1000.0))
        }
        Units.IMPERIAL -> {
            val feet = meters * 3.28084
            when {
                feet < 1000 -> words.feet(((feet / 50).roundToInt() * 50).coerceAtLeast(50))
                else -> words.miles(oneDecimal(meters / 1609.344))
            }
        }
    }

    /** A distance for the screen: "350 m", "1.2 km", "12 km", "800 ft", "3.4 mi". */
    fun displayDistance(words: InstructionWords, meters: Double, units: Units): String = when (units) {
        Units.METRIC -> when {
            meters < 1000 -> words.meters(((meters / 10).roundToInt() * 10))
            meters < 10_000 -> words.kilometers(oneDecimal(meters / 1000.0))
            else -> words.kilometers((meters / 1000.0).roundToInt().toString())
        }
        Units.IMPERIAL -> {
            val miles = meters / 1609.344
            when {
                miles < 0.19 -> words.feet(((meters * 3.28084 / 10).roundToInt() * 10))
                miles < 10 -> words.miles(oneDecimal(miles))
                else -> words.miles(miles.roundToInt().toString())
            }
        }
    }

    private fun oneDecimal(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else String.format(Locale.getDefault(), "%.1f", r)
    }

    /** "1st", "2nd", "3rd", "4th" in English; other languages use their own strings for exits. */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}
