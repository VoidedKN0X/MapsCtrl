package com.vknxworks.mapsctrl.location

/**
 * Which way you're facing, from the GPS while you move and from the compass while you stand still or go slowly,
 * and whether the compass sensor has to run for that.
 *
 * The rule for the heading itself is the one the map always had: above [MOVING] m/s with a GPS bearing it's the GPS
 * bearing, otherwise the compass. What this adds is that the compass sensor is switched off while you ride well
 * above that speed, where its readings were thrown away anyway:
 *
 * - the sensor goes off above [OFF_ABOVE] m/s and comes back on below [ON_BELOW] m/s (two speeds, so it doesn't
 *   flip on and off around one), which is well before the compass is needed again at [MOVING] m/s;
 * - and if the compass is needed before it has given its first reading (hard braking between two positions, or
 *   a position without a bearing), the last GPS bearing is kept until that reading is there, so the map never
 *   turns north for a moment.
 *
 * Not thread-safe: use it from one thread. Calling it twice with the same values gives the same answers.
 */
class HeadingGate {
    private var sensorOn = false
    /** The last GPS bearing, kept only while the compass that has to take over has no reading yet. */
    private var held: Float? = null

    /**
     * Whether the compass sensor should run. [wanted] is what switched it on before (compass mode or navigating);
     * [hasSensor] false (a phone without one) keeps everything as it was.
     */
    fun sensorNeeded(wanted: Boolean, hasSensor: Boolean, speed: Float?, bearing: Float?): Boolean {
        sensorOn = when {
            !wanted -> false
            !hasSensor -> true
            bearing == null -> true
            sensorOn -> (speed ?: 0f) <= OFF_ABOVE
            else -> (speed ?: 0f) < ON_BELOW
        }
        if (!wanted || !hasSensor) held = null
        return sensorOn
    }

    /**
     * The heading to show for a position with [speed] and [bearing] ([hasFix] false: no position, no heading) and
     * the compass's reading [compass] (null while it's off or hasn't given one yet). Call [sensorNeeded] first.
     */
    fun heading(hasFix: Boolean, wanted: Boolean, hasSensor: Boolean, speed: Float?, bearing: Float?, compass: Float?): Float? {
        if (!hasFix) return null
        if ((speed ?: 0f) > MOVING && bearing != null) {
            // Remember it for as long as the compass couldn't take over right now.
            held = if (wanted && hasSensor && compass == null) bearing else null
            return bearing
        }
        if (compass != null) {
            held = null
            return compass
        }
        return if (wanted && hasSensor) held else null
    }

    companion object {
        /** Above this speed (m/s) the GPS bearing is used instead of the compass. */
        const val MOVING = 1.5f
        /** The sensor comes back on below this speed (m/s)... */
        const val ON_BELOW = 4f
        /** ...and goes off above this one. */
        const val OFF_ABOVE = 6f
    }
}
