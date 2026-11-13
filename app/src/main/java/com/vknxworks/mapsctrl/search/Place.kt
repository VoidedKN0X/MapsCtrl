package com.vknxworks.mapsctrl.search

import com.vknxworks.mapsctrl.geo.LatLon
import kotlinx.serialization.Serializable

/** Anything the place sheet can show: a POI, a saved place, a coordinate or a point you tapped on the map. */
@Serializable
data class Place(
    val name: String,
    val point: LatLon,
    /** OpenStreetMap tags, when the place came from search data. */
    val tags: Map<String, String> = emptyMap(),
    /** Category in the phone's language, from the POI file ("Restaurants"). */
    val category: String? = null,
    /** Set when this place is in Saved. */
    val savedId: String? = null,
) {
    /** "Avenue de la Madone 4, 98000 Monaco", from the address tags when there are any. */
    fun address(): String? {
        val street = tags["addr:street"] ?: tags["addr:place"]
        val number = tags["addr:housenumber"]
        val line1 = listOfNotNull(street, number).joinToString(" ").takeIf { it.isNotBlank() }
        val line2 = listOfNotNull(tags["addr:postcode"], tags["addr:city"]).joinToString(" ").takeIf { it.isNotBlank() }
        return listOfNotNull(line1, line2).joinToString(", ").takeIf { it.isNotBlank() }
    }

    /** The main OSM type of the place ("amenity=restaurant" -> "restaurant"), for its icon. */
    fun kind(): String? {
        for (k in listOf("amenity", "shop", "tourism", "leisure", "highway", "railway", "public_transport", "historic", "natural", "place", "office", "healthcare", "sport", "man_made", "aeroway", "emergency", "craft")) {
            tags[k]?.let { return it }
        }
        return null
    }
}
