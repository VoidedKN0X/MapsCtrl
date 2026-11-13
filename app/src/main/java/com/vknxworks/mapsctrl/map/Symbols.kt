package com.vknxworks.mapsctrl.map

/**
 * MapsCtrl's own map symbols: a rounded square in a muted colour with a simple glyph, drawn for this app.
 * Each glyph is SVG content in a 24x24 box; "{g}" is replaced by the glyph colour.
 */
object Symbols {

    private fun fill(d: String, evenOdd: Boolean = false) =
        """<path fill="{g}"${if (evenOdd) " fill-rule=\"evenodd\"" else ""} d="$d"/>"""

    private fun stroke(d: String, w: Double = 1.6) =
        """<path fill="none" stroke="{g}" stroke-width="$w" stroke-linecap="round" stroke-linejoin="round" d="$d"/>"""

    private val bus = fill("M7,6 h10 a1,1 0 0 1 1,1 v9 h-1 v2 h-2 v-2 h-6 v2 h-2 v-2 h-1 v-9 a1,1 0 0 1 1,-1 z M8,8 h8 v4 h-8 z M8.5,13.5 h1.5 v1.2 h-1.5 z M14,13.5 h1.5 v1.2 h-1.5 z", evenOdd = true)

    val glyphs: Map<String, String> = linkedMapOf(
        "food" to fill("M8,5 v6 a2,2 0 0 0 1.5,1.94 V19 h1.5 v-6.06 A2,2 0 0 0 12.5,11 V5 h-1 v5 h-0.75 V5 h-1 v5 H9 V5 z M15.5,5 c-1.2,0 -1.8,2.2 -1.8,4.5 v3 h1.3 v6.5 h1.5 V5 z"),
        "cafe" to fill("M6,9 h9 v4 a4,4 0 0 1 -4,4 h-1 a4,4 0 0 1 -4,-4 z") + stroke("M15,10.5 h1 a1.8,1.8 0 0 1 0,3.6 h-1", 1.4),
        "bar" to fill("M6.5,6 h11 l-4.6,6.2 v4.3 h2.6 v1.5 h-6.9 v-1.5 h2.8 v-4.3 z"),
        "hospital" to fill("M10,6 h4 v4 h4 v4 h-4 v4 h-4 v-4 h-4 v-4 h4 z"),
        "pharmacy" to fill("M11,6 h2 v5 h5 v2 h-5 v5 h-2 v-5 h-5 v-2 h5 z"),
        "doctor" to stroke("M12,6.5 v11 M6.5,12 h11", 2.2),
        "money" to fill("M5,8 h14 v8 h-14 z M12,10 a2,2 0 1 0 0.01,0 z", evenOdd = true),
        "toilets" to fill("M7,7.2 a1.5,1.5 0 1 0 3,0 a1.5,1.5 0 1 0 -3,0 z M7,9.8 h3 v8.2 h-3 z M14,7.2 a1.5,1.5 0 1 0 3,0 a1.5,1.5 0 1 0 -3,0 z M13,9.8 h5 l-1,5 h-0.6 v3.2 h-1.8 v-3.2 h-0.6 z"),
        "school" to fill("M5.5,7 h5 a1.5,1.5 0 0 1 1.5,1.5 v10 a1.2,1.2 0 0 0 -1.2,-1 h-5.3 z M18.5,7 h-5 a1.5,1.5 0 0 0 -1.5,1.5 v10 a1.2,1.2 0 0 1 1.2,-1 h5.3 z"),
        "worship" to fill("M12,4.5 l3.2,4.5 v9.5 h-6.4 v-9.5 z M6,13 h2 v5.5 h-2 z M16,13 h2 v5.5 h-2 z"),
        "civic" to fill("M8,5 h1.5 v14 h-1.5 z M9.5,6 h7.5 l-2,3 2,3 h-7.5 z"),
        "culture" to fill("M12,5 l2.1,4.4 4.8,0.6 -3.5,3.3 0.9,4.7 -4.3,-2.3 -4.3,2.3 0.9,-4.7 -3.5,-3.3 4.8,-0.6 z"),
        "bicycle" to stroke("M8,16 m-3,0 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0 M16,16 m-3,0 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0 M8,16 l3,-6 h4 l1,6 M10,8 h2", 1.4),
        "water" to fill("M12,5 C12,5 7,11 7,14 a5,5 0 0 0 10,0 C17,11 12,5 12,5 z"),
        "shop" to fill("M6.5,9 h11 l-1,10 h-9 z") + stroke("M9.5,9 v-1.2 a2.5,2.5 0 0 1 5,0 V9", 1.4),
        "hotel" to fill("M5,17 v-8 h1.6 v4 h11.4 a1,1 0 0 1 1,1 v3 h-1.6 v-1.6 h-10.8 v1.6 z M8,10.5 h3.5 v2 h-3.5 z"),
        "museum" to fill("M5,9.5 l7,-4 7,4 z M6.5,10.5 h2 v6 h-2 z M11,10.5 h2 v6 h-2 z M15.5,10.5 h2 v6 h-2 z M5,17.5 h14 v1.5 h-14 z"),
        "viewpoint" to fill("M4,12 C6,7.8 18,7.8 20,12 C18,16.2 6,16.2 4,12 z M12,9.6 a2.4,2.4 0 1 0 0.01,0 z", evenOdd = true),
        "info" to fill("M11,10 h2 v7.5 h-2 z M12,5.8 a1.4,1.4 0 1 0 0.01,0 z"),
        "bus" to bus,
        "tram" to bus + fill("M11.2,3.5 h1.6 v2.5 h-1.6 z"),
        "train" to bus + stroke("M7.5,20 h9", 1.2),
        "ferry" to fill("M5,14 h14 l-2.2,4.5 h-9.6 z M8,9 h8 v4 h-8 z M11,6 h2 v3 h-2 z"),
        "airport" to fill("M12,4 c0.8,0 1.2,0.8 1.2,1.6 v4.4 l6,3.5 v1.6 l-6,-1.8 v3.7 l1.6,1.2 v1.3 l-2.8,-0.8 -2.8,0.8 v-1.3 l1.6,-1.2 v-3.7 l-6,1.8 v-1.6 l6,-3.5 V5.6 C10.8,4.8 11.2,4 12,4 z"),
        "parking" to fill("M8,6 h5 a4,4 0 0 1 0,8 h-2.5 v4 H8 z M10.5,8.3 v3.4 h2.4 a1.7,1.7 0 0 0 0,-3.4 z", evenOdd = true),
        "fuel" to fill("M6,6 h7 v13 h-7 z M7.5,7.5 h4 v3.5 h-4 z", evenOdd = true) + stroke("M13,9.5 h2 v6.5 a1,1 0 0 0 2,0 v-5.5 l-1.8,-2", 1.3),
        "charging" to fill("M13.5,4 l-6.5,9 h4.2 l-1.2,7 6.5,-9 h-4.2 z"),
        "peak" to fill("M12,6 l7,12 h-14 z"),
        "leisure" to fill("M12,6 a6,6 0 1 0 0.01,0 z"),
    )

    /** A complete SVG symbol: rounded square of [bg] with the glyph in [glyph]. */
    fun svg(content: String, bg: String, glyph: String): String =
        """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">""" +
            """<rect x="0.5" y="0.5" width="23" height="23" rx="6.5" fill="$bg" stroke="$glyph" stroke-opacity="0.85" stroke-width="1"/>""" +
            content.replace("{g}", glyph) + "</svg>"
}
