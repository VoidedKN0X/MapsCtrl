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
        // A number in degrees, with minutes (') and seconds (") when marked.
        val num = """(\d{1,3}(?:[.,]\d+)?)\s*°?\s*(?:(\d{1,2}(?:[.,]\d+)?)\s*'\s*)?(?:(\d{1,2}(?:[.,]\d+)?)\s*"\s*)?"""
        val sep = """\s*[,;/]?\s*"""
        val pre = Regex("""^\s*([NSEWO])\s*$num$sep([NSEWO])\s*$num\s*$""").find(upper)
        val post = Regex("""^\s*$num\s*([NSEWO])$sep$num\s*([NSEWO])\s*$""").find(upper)
        val bare = Regex("""^\s*$num$sep$num\s*$""").find(upper)
        val (first, second) = when {
            pre != null -> pre.groupValues.let { g -> component(g[1], g[2], g[3], g[4], "") to component(g[5], g[6], g[7], g[8], "") }
            post != null -> post.groupValues.let { g -> component("", g[1], g[2], g[3], g[4]) to component("", g[5], g[6], g[7], g[8]) }
            bare != null -> bare.groupValues.let { g -> component("", g[1], g[2], g[3], "") to component("", g[4], g[5], g[6], "") }
            else -> return null
        }
        if (first == null || second == null) return null
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
            return if (q.isNotEmpty()) parse(decodeQuery(q)) else null
        }
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) {
            val q = uri.substringAfter("q=", "").substringBefore('(').substringBefore('&')
            if (q.isNotEmpty()) return parse(decodeQuery(q))
        }
        return make(lat, lon)
    }

    /** The search text of a geo: URI ("geo:0,0?q=Grand Place"), when it has a place name instead of coordinates. */
    fun geoUriQuery(uri: String): String? {
        if (!uri.startsWith("geo:", ignoreCase = true)) return null
        val q = uri.substringAfter("q=", "").substringBefore('&')
        if (q.isEmpty()) return null
        val text = decodeQuery(q).trim()
        return text.takeIf { parse(it) == null && it.isNotEmpty() }
    }

    /**
     * The place name in brackets at the end of a geo: URI ("geo:50.8,4.3?q=50.8,4.3(Gent%20Sint-Pieters)"), decoded,
     * or null when there is none. Everything up to the last ")" counts, so a name with brackets in it
     * ("Station (perron 5)") stays whole.
     */
    fun geoUriLabel(uri: String): String? {
        if (!uri.startsWith("geo:", ignoreCase = true)) return null
        val inner = uri.substringAfter('(', "")
        if (inner.isEmpty()) return null
        val raw = if (inner.contains(')')) inner.substringBeforeLast(')') else inner
        return percentDecode(raw, plusAsSpace = false).trim().takeIf { it.isNotBlank() }
    }

    /** A geo: URI's q= value: "+" is a space, %XX are bytes. Never throws. */
    private fun decodeQuery(q: String): String = percentDecode(q, plusAsSpace = true)

    /**
     * Percent-decoding that never throws: valid %XX sequences become bytes (read as UTF-8), anything else (a stray
     * "%", "%zz", a "%" at the end) is kept as it is. `java.net.URLDecoder` throws on those, and a geo: link from
     * any app or web page could otherwise close MapsCtrl ("geo:0,0?q=100%").
     */
    fun percentDecode(s: String, plusAsSpace: Boolean): String {
        if (s.indexOf('%') < 0 && (!plusAsSpace || s.indexOf('+') < 0)) return s
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '%' && i + 2 < s.length && hex(s[i + 1]) >= 0 && hex(s[i + 2]) >= 0) {
                out.write(hex(s[i + 1]) * 16 + hex(s[i + 2]))
                i += 3
                continue
            }
            if (ch == '+' && plusAsSpace) {
                out.write(' '.code)
                i++
                continue
            }
            // One character as it is (a surrogate pair stays together).
            val end = if (Character.isHighSurrogate(ch) && i + 1 < s.length) i + 2 else i + 1
            val bytes = s.substring(i, end).toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            i = end
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun hex(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private fun make(lat: Double, lon: Double): LatLon? {
        val p = LatLon(lat, lon)
        return if (p.isValid()) p else null
    }
}
