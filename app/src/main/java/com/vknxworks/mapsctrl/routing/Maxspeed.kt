package com.vknxworks.mapsctrl.routing

/**
 * A speed limit read from OpenStreetMap's maxspeed tag.
 * [kmh] is null when there is no limit ("none", German motorways); [signMph] when the sign itself is in mph.
 */
data class SpeedLimit(val kmh: Int?, val signMph: Boolean = false) {
    val none: Boolean get() = kmh == null

    /** The number printed on the sign: mph for mph roads, else km/h. */
    val signValue: Int? get() = kmh?.let { if (signMph) Math.round(it / 1.609344).toInt() else it }
}

object Maxspeed {

    /**
     * Implicit limits for the common "XX:zone" values (country:type). Only well-known values are listed;
     * anything else gives null (unknown), never a guess.
     */
    private val implicit: Map<String, Int?> = mapOf(
        "AT:urban" to 50, "AT:rural" to 100, "AT:trunk" to 100, "AT:motorway" to 130,
        "BE:urban" to 50, "BE-BRU:urban" to 30, "BE-VLG:urban" to 50, "BE-WAL:urban" to 50,
        "BE:rural" to 90, "BE-VLG:rural" to 70, "BE-WAL:rural" to 90, "BE:trunk" to 120, "BE:motorway" to 120,
        "CH:urban" to 50, "CH:rural" to 80, "CH:trunk" to 100, "CH:motorway" to 120,
        "CZ:urban" to 50, "CZ:rural" to 90, "CZ:trunk" to 110, "CZ:motorway" to 130,
        "DE:urban" to 50, "DE:rural" to 100, "DE:bicycle_road" to 30, "DE:motorway" to null, "DE:trunk" to null,
        "DE:zone30" to 30, "DE:zone:30" to 30, "DE:zone20" to 20, "DE:zone:20" to 20,
        "DK:urban" to 50, "DK:rural" to 80, "DK:motorway" to 130,
        "ES:urban" to 50, "ES:rural" to 90, "ES:trunk" to 100, "ES:motorway" to 120,
        "FI:urban" to 50, "FI:rural" to 80, "FI:motorway" to 120,
        "FR:urban" to 50, "FR:rural" to 80, "FR:trunk" to 110, "FR:motorway" to 130, "FR:zone30" to 30,
        "GB:nsl_single" to 97, "GB:nsl_dual" to 113, "GB:motorway" to 113,
        "UK:nsl_single" to 97, "UK:nsl_dual" to 113, "UK:motorway" to 113,
        "HU:urban" to 50, "HU:rural" to 90, "HU:trunk" to 110, "HU:motorway" to 130,
        "IE:urban" to 50, "IE:rural" to 80, "IE:national" to 100, "IE:motorway" to 120,
        "IT:urban" to 50, "IT:rural" to 90, "IT:trunk" to 110, "IT:motorway" to 130,
        "LU:urban" to 50, "LU:rural" to 90, "LU:motorway" to 130,
        "NL:urban" to 50, "NL:rural" to 80, "NL:trunk" to 100, "NL:motorway" to 100,
        "NO:urban" to 50, "NO:rural" to 80, "NO:motorway" to 110,
        "PL:urban" to 50, "PL:rural" to 90, "PL:expressway" to 120, "PL:motorway" to 140,
        "PT:urban" to 50, "PT:rural" to 90, "PT:trunk" to 100, "PT:motorway" to 120,
        "SE:urban" to 50, "SE:rural" to 70, "SE:motorway" to 110,
        "SK:urban" to 50, "SK:rural" to 90, "SK:motorway" to 130,
        "SI:urban" to 50, "SI:rural" to 90, "SI:trunk" to 110, "SI:motorway" to 130,
        "RO:urban" to 50, "RO:rural" to 90, "RO:trunk" to 100, "RO:motorway" to 130,
        "RU:urban" to 60, "RU:rural" to 90, "RU:motorway" to 110,
        "UA:urban" to 50, "UA:rural" to 90, "UA:motorway" to 130,
        "GR:urban" to 50, "GR:rural" to 90, "GR:motorway" to 130,
        "HR:urban" to 50, "HR:rural" to 90, "HR:motorway" to 130,
    )

    /**
     * Parses one maxspeed value: "50", "50 km/h", "30 mph", "20mph", "none", "walk", "DE:urban",
     * "signals"/"variable" (null: shown on electronic signs only), "50;70" (the lowest).
     * Returns null when the value says nothing usable.
     */
    fun parse(raw: String?): SpeedLimit? {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (v.contains(';')) {
            return v.split(';').mapNotNull { parse(it) }.filter { it.kmh != null }.minByOrNull { it.kmh!! }
        }
        val lower = v.lowercase()
        when (lower) {
            "none", "unlimited" -> return SpeedLimit(null)
            "walk" -> return SpeedLimit(6)
            "signals", "variable", "unknown", "no", "yes" -> return null
        }
        Regex("""^(\d+(?:\.\d+)?)\s*(km/h|kmh|kph|mph|knots)?$""").find(lower)?.let { m ->
            val n = m.groupValues[1].toDouble()
            return when (m.groupValues[2]) {
                "mph" -> SpeedLimit(Math.round(n * 1.609344).toInt(), signMph = true)
                "knots" -> SpeedLimit(Math.round(n * 1.852).toInt())
                else -> SpeedLimit(Math.round(n).toInt())
            }
        }
        // BRouter's lookups write "30_mph" for "30 mph".
        Regex("""^(\d+)_mph$""").find(lower)?.let { m ->
            return SpeedLimit(Math.round(m.groupValues[1].toDouble() * 1.609344).toInt(), signMph = true)
        }
        if (v.contains(':')) {
            val key = v.substringBefore(":").uppercase() + ":" + v.substringAfter(":").lowercase()
            if (implicit.containsKey(key)) {
                val kmh = implicit[key]
                val mph = key.startsWith("GB:") || key.startsWith("UK:")
                return SpeedLimit(kmh, signMph = mph)
            }
            // "DE:zone:30", "FR:zone30" and the like: a number in the value is the limit.
            Regex("""zone:?(\d+)""").find(lower)?.let { return SpeedLimit(it.groupValues[1].toInt()) }
        }
        return null
    }

    /**
     * The limit for a way as BRouter describes it ("highway=primary maxspeed=50 reversedirection=yes"),
     * using maxspeed:forward / :backward for the direction of travel when the way has them.
     */
    fun forWay(tags: Map<String, String>): SpeedLimit? {
        val reverse = tags["reversedirection"] == "yes"
        val directional = if (reverse) tags["maxspeed:backward"] else tags["maxspeed:forward"]
        return parse(directional) ?: parse(tags["maxspeed"]) ?: parse(tags["zone:maxspeed"]) ?: parse(tags["source:maxspeed"]?.takeIf { it.contains(':') })
    }
}
