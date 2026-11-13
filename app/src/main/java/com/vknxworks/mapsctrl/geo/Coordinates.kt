package com.vknxworks.mapsctrl.geo

/**
 * Reads coordinates typed into search: decimal degrees ("50.8466, 4.3528", "50.8466 4.3528",
 * "N 50.8466 E 4.3528", "50.8466N, 4.3528E"), degrees-minutes-seconds ("50°50'47.8\"N 4°21'10.1\"E"),
 * degrees and decimal minutes ("N 50° 50.797' E 4° 21.168'") and geo: URIs ("geo:50.8466,4.3528?z=15").
 * Returns null when the text isn't a coordinate.
 */
object Coordinates {

    fun parse(input: String): LatLon? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("geo:", ignoreCase = true)) return parseGeoUri(s)
        s = s.replace('′', '\'').replace('″', '"').replace("''", "\"")
            .replace('’', '\'').replace('”', '"').replace('º', '°')

        // Plain "lat, lon" or "lat lon" in decimal degrees (a comma as a decimal separator only with ";" or spaces between).
        Regex("""^\s*([-+]?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)\s*$""").find(s)?.let { m ->
            return make(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
        }
        Regex("""^\s*([-+]?\d{1,2},\d+)\s*[;\s]\s*([-+]?\d{1,3},\d+)\s*$""").find(s)?.let { m ->
            return make(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2].replace(',', '.').toDouble())
        }

        // Two parts with hemisphere letters, each in decimal degrees, DM or DMS.
        val upper = s.uppercase()
        val part = """([NSEWO])?\s*(\d{1,3}(?:[.,]\d+)?)\s*°?\s*(?:(\d{1,2}(?:[.,]\d+)?)\s*'?\s*)?(?:(\d{1,2}(?:[.,]\d+)?)\s*"?\s*)?([NSEWO])?"""
        val m = Regex("""^\s*$part\s*[,;/]?\s*$part\s*$""").find(upper) ?: return null
        val g = m.groupValues
        val first = component(g[1], g[2], g[3], g[4], g[5]) ?: return null
        val second = component(g[6], g[7], g[8], g[9], g[10]) ?: return null
        // The hemisphere letters decide which is latitude; without letters the first part is latitude.
        val (lat, lon) = when {
            first.second in "EWO" || second.second in "NS" -> second.first to first.first
            else -> first.first to second.first
        }
        if (first.second.isEmpty() && second.second.isEmpty() && !s.contains('°')) return null
        return make(lat, lon)
    }

    /** One coordinate part: (signed value, hemisphere letter or ""). */
    private fun component(pre: String, deg: String, min: String, sec: String, post: String): Pair<Double, String>? {
        val hemi = pre.ifEmpty { post }
        if (pre.isNotEmpty() && post.isNotEmpty()) return null
        var v = deg.replace(',', '.').toDoubleOrNull() ?: return null
        if (min.isNotEmpty()) {
            val mm = min.replace(',', '.').toDouble()
            if (mm >= 60.0 || v % 1.0 != 0.0) return null
            v += mm / 60.0
        }
        if (sec.isNotEmpty()) {
            val ss = sec.replace(',', '.').toDouble()
            if (ss >= 60.0) return null
            v += ss / 3600.0
        }
        // "O" is east (Ost, oost, ouest is west in French, but "O" alone is too ambiguous to be west).
        if (hemi == "S" || hemi == "W") v = -v
        return v to hemi
    }

    private fun parseGeoUri(uri: String): LatLon? {
        val body = uri.substringAfter(':').substringBefore('?').substringBefore(';')
        val parts = body.split(',')
        if (parts.size < 2) {
            // geo:0,0?q=lat,lon(label)
            val q = uri.substringAfter("q=", "").substringBefore('(').substringBefore('&')
            return if (q.isNotEmpty()) parse(java.net.URLDecoder.decode(q, "UTF-8")) else null
        }
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) {
            val q = uri.substringAfter("q=", "").substringBefore('(').substringBefore('&')
            if (q.isNotEmpty()) return parse(java.net.URLDecoder.decode(q, "UTF-8"))
        }
        return make(lat, lon)
    }

    /** The search text of a geo: URI ("geo:0,0?q=Grand Place"), when it has a place name instead of coordinates. */
    fun geoUriQuery(uri: String): String? {
        if (!uri.startsWith("geo:", ignoreCase = true)) return null
        val q = uri.substringAfter("q=", "").substringBefore('&')
        if (q.isEmpty()) return null
        val text = java.net.URLDecoder.decode(q, "UTF-8").trim()
        return text.takeIf { parse(it) == null && it.isNotEmpty() }
    }

    private fun make(lat: Double, lon: Double): LatLon? {
        val p = LatLon(lat, lon)
        return if (p.isValid()) p else null
    }
}
