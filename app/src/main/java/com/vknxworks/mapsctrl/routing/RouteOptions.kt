package com.vknxworks.mapsctrl.routing

/** Something a route can avoid. Each one only works where the travel mode's BRouter profile has a switch for it. */
enum class Avoid { TOLLS, MOTORWAYS, UNPAVED, FERRIES }

/**
 * Turns the avoid choices into the switches of the bundled BRouter profiles. Only switches the profile itself
 * declares are used (BRouter takes them as numbers: 1 on, 0 off):
 * - car-vario: `avoid_toll`, `avoid_motorways`, `avoid_unpaved` (it has no ferry switch);
 * - moped: `avoid_unpaved`, `avoid_ferries` (motorways are never used; Belgium has no toll roads a moped may use);
 * - trekking (bike) and hiking-mountain (foot): `allow_ferries`.
 */
object RouteOptions {
    fun supported(mode: TravelMode): List<Avoid> = when (mode) {
        TravelMode.CAR -> listOf(Avoid.TOLLS, Avoid.MOTORWAYS, Avoid.UNPAVED)
        TravelMode.MOPED -> listOf(Avoid.UNPAVED, Avoid.FERRIES)
        TravelMode.BIKE, TravelMode.FOOT -> listOf(Avoid.FERRIES)
    }

    /** The profile parameters for [avoid] in [mode]; switches the profile doesn't have are left out. */
    fun profileParams(mode: TravelMode, avoid: Set<Avoid>): Map<String, String> {
        fun flag(b: Boolean) = if (b) "1" else "0"
        return when (mode) {
            TravelMode.CAR -> mapOf(
                "avoid_toll" to flag(Avoid.TOLLS in avoid),
                "avoid_motorways" to flag(Avoid.MOTORWAYS in avoid),
                "avoid_unpaved" to flag(Avoid.UNPAVED in avoid),
            )
            TravelMode.MOPED -> mapOf(
                "avoid_unpaved" to flag(Avoid.UNPAVED in avoid),
                "avoid_ferries" to flag(Avoid.FERRIES in avoid),
            )
            TravelMode.BIKE, TravelMode.FOOT -> mapOf("allow_ferries" to flag(Avoid.FERRIES !in avoid))
        }
    }

    /** The choices that apply to [mode]. */
    fun active(mode: TravelMode, avoid: Set<Avoid>): List<Avoid> = supported(mode).filter { it in avoid }
}
