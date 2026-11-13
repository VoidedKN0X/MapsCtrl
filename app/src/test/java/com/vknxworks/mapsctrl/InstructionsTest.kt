package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.routing.InstructionWords
import com.vknxworks.mapsctrl.routing.Instructions
import com.vknxworks.mapsctrl.routing.TurnHint
import com.vknxworks.mapsctrl.routing.TurnKind
import com.vknxworks.mapsctrl.routing.Units
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** English words, like strings.xml. */
object FakeWords : InstructionWords {
    override fun action(kind: TurnKind, exit: Int) = when (kind) {
        TurnKind.LEFT -> "turn left"
        TurnKind.RIGHT -> "turn right"
        TurnKind.KEEP_RIGHT -> "keep right"
        TurnKind.ROUNDABOUT, TurnKind.ROUNDABOUT_LEFT -> "take the ${Instructions.ordinal(exit)} exit"
        TurnKind.ARRIVE -> "you have arrived"
        TurnKind.STRAIGHT -> "continue straight"
        else -> kind.name.lowercase()
    }
    override fun inDistance(distance: String, action: String) = "In $distance, $action"
    override fun then(action: String) = "then $action"
    override fun meters(n: Int) = "$n m"
    override fun kilometers(n: String) = "$n km"
    override fun feet(n: Int) = "$n ft"
    override fun miles(n: String) = "$n mi"
}

class InstructionsTest {
    init { Locale.setDefault(Locale.ENGLISH) }

    private val left = TurnHint(10, TurnKind.LEFT, 0, -90)

    @Test
    fun titles() {
        assertEquals("Turn left", Instructions.title(FakeWords, left))
        assertEquals("Take the 2nd exit", Instructions.title(FakeWords, TurnHint(3, TurnKind.ROUNDABOUT, 2, 0)))
    }

    @Test
    fun spoken() {
        assertEquals("In 300 m, turn left", Instructions.spoken(FakeWords, left, 287.0, Units.METRIC))
        assertEquals("In 1.5 km, turn left", Instructions.spoken(FakeWords, left, 1480.0, Units.METRIC))
        assertEquals("In 2 km, turn left", Instructions.spoken(FakeWords, left, 2010.0, Units.METRIC))
        assertEquals("Turn left", Instructions.spoken(FakeWords, left, 20.0, Units.METRIC))
        assertEquals("In 60 m, turn left", Instructions.spoken(FakeWords, left, 57.0, Units.METRIC))
        assertEquals("In 0.2 mi, turn left", Instructions.spoken(FakeWords, left, 320.0, Units.IMPERIAL))
        assertEquals("In 500 ft, turn left", Instructions.spoken(FakeWords, left, 150.0, Units.IMPERIAL))
    }

    @Test
    fun thenFollowing() {
        val next = TurnHint(12, TurnKind.KEEP_RIGHT, 0, 20)
        assertEquals("In 200 m, turn left, then keep right", Instructions.spoken(FakeWords, left, 210.0, Units.METRIC, next, 80.0))
        assertEquals("In 200 m, turn left", Instructions.spoken(FakeWords, left, 210.0, Units.METRIC, next, 400.0))
    }

    @Test
    fun display() {
        assertEquals("350 m", Instructions.displayDistance(FakeWords, 347.0, Units.METRIC))
        assertEquals("1.2 km", Instructions.displayDistance(FakeWords, 1234.0, Units.METRIC))
        assertEquals("12 km", Instructions.displayDistance(FakeWords, 12345.0, Units.METRIC))
        assertEquals("3.4 mi", Instructions.displayDistance(FakeWords, 5472.0, Units.IMPERIAL))
        assertEquals("330 ft", Instructions.displayDistance(FakeWords, 100.0, Units.IMPERIAL))
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "21st"), listOf(1, 2, 3, 4, 11, 12, 21).map { Instructions.ordinal(it) })
    }
}
