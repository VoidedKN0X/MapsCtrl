package com.vknxworks.mapsctrl.nav

import android.content.Context
import com.vknxworks.mapsctrl.R
import com.vknxworks.mapsctrl.routing.InstructionWords
import com.vknxworks.mapsctrl.routing.TurnKind

/** Turn instruction words from strings.xml, in the phone's language. */
class ResourceWords(private val context: Context) : InstructionWords {
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    override fun action(kind: TurnKind, exit: Int): String = when (kind) {
        TurnKind.STRAIGHT -> s(R.string.turn_straight)
        TurnKind.LEFT -> s(R.string.turn_left)
        TurnKind.SLIGHT_LEFT -> s(R.string.turn_slight_left)
        TurnKind.SHARP_LEFT -> s(R.string.turn_sharp_left)
        TurnKind.RIGHT -> s(R.string.turn_right)
        TurnKind.SLIGHT_RIGHT -> s(R.string.turn_slight_right)
        TurnKind.SHARP_RIGHT -> s(R.string.turn_sharp_right)
        TurnKind.KEEP_LEFT -> s(R.string.turn_keep_left)
        TurnKind.KEEP_RIGHT -> s(R.string.turn_keep_right)
        TurnKind.U_TURN_LEFT, TurnKind.U_TURN_RIGHT, TurnKind.U_TURN -> s(R.string.turn_u_turn)
        TurnKind.OFF_ROUTE -> s(R.string.turn_off_route)
        TurnKind.ROUNDABOUT, TurnKind.ROUNDABOUT_LEFT ->
            if (exit > 0) context.resources.getStringArray(R.array.turn_roundabout_exit).let { a -> if (exit <= a.size) a[exit - 1] else s(R.string.turn_roundabout_n, exit) }
            else s(R.string.turn_roundabout)
        TurnKind.BEELINE -> s(R.string.turn_beeline)
        TurnKind.EXIT_LEFT -> s(R.string.turn_exit_left)
        TurnKind.EXIT_RIGHT -> s(R.string.turn_exit_right)
        TurnKind.ARRIVE -> s(R.string.turn_arrive)
    }

    override fun actionOnto(kind: TurnKind, exit: Int, street: String): String = when (kind) {
        TurnKind.ROUNDABOUT, TurnKind.ROUNDABOUT_LEFT ->
            if (exit > 0) context.resources.getStringArray(R.array.turn_roundabout_exit_onto).let { a -> if (exit <= a.size) a[exit - 1].format(street) else s(R.string.turn_roundabout_n_onto, exit, street) }
            else s(R.string.turn_roundabout_onto, street)
        else -> s(R.string.turn_onto, action(kind, exit), street)
    }

    override fun inDistance(distance: String, action: String) = s(R.string.voice_in_distance, distance, action)
    override fun then(action: String) = s(R.string.voice_then, action)
    override fun meters(n: Int) = s(R.string.unit_m, n)
    override fun kilometers(n: String) = s(R.string.unit_km, n)
    override fun feet(n: Int) = s(R.string.unit_ft, n)
    override fun miles(n: String) = s(R.string.unit_mi, n)
}
