package com.vknxworks.mapsctrl.search

import com.vknxworks.mapsctrl.data.MapRepository
import com.vknxworks.mapsctrl.geo.Coordinates
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.cos

/** A search result, with how far it is from where you're looking. */
data class SearchResult(val place: Place, val distance: Double?)

/** Quick category searches on the search screen; [names] are the POI files' English category names. */
enum class QuickCategory(val names: Set<String>) {
    FOOD(setOf("Restaurants", "Fast food restaurants", "Cafes", "Pubs", "Bars")),
    FUEL(setOf("Fuel stations")),
    CHARGING(setOf("Electric vehicle charging facilities")),
    PARKING(setOf("Car parking places")),
    SUPERMARKET(setOf("Supermarkets", "Convenience")),
    HOTEL(setOf("Hotels")),
    PHARMACY(setOf("Pharmacies", "Hospitals")),
    ATM(setOf("ATMs and cash points", "Banks")),
    TOILETS(setOf("Public toilets")),
    TRANSPORT(setOf("Stations", "Bus stops", "Platforms", "Stop positions")),
}

/**
 * Offline search over every downloaded POI file: names, addresses, any tag text, categories, plus typed
 * coordinates. Nothing is sent anywhere.
 */
class SearchRepository(private val maps: MapRepository) {
    private val open = HashMap<String, PoiDatabase>()
    private val mutex = Mutex()
    private val lang = Locale.getDefault().language

    private suspend fun databases(): List<PoiDatabase> = mutex.withLock {
        val files = maps.poiFiles()
        val keep = files.map { it.path }.toSet()
        open.keys.filterNot { it in keep }.forEach { open.remove(it)?.close() }
        files.mapNotNull { f -> open[f.path] ?: runCatching { PoiDatabase(f) }.onFailure { android.util.Log.w("Search", "can't open $f", it) }.getOrNull()?.also { open[f.path] = it } }
    }

    suspend fun hasSearchData(): Boolean = maps.poiFiles().isNotEmpty()

    suspend fun search(query: String, near: LatLon?): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        Coordinates.parse(q)?.let { p ->
            return@withContext listOf(SearchResult(Place(p.format(), p), near?.let { Geo.distance(it, p) }))
        }
        val norm = PoiDatabase.normalize(q)
        val words = norm.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val textWords = words.filter { w -> w.any { it.isLetter() } }
        val numbers = words.filter { w -> w.all { it.isDigit() } }
        val hits = databases().flatMap { db ->
            val byText = if (textWords.isNotEmpty()) db.searchText(textWords) else emptyList()
            // A category name typed as text ("restaurant", "pharmacies") finds that category nearby too.
            val cats = db.categories.values.filter { c -> c.names.values.any { PoiDatabase.normalize(it).contains(norm) } && norm.length >= 4 }.map { it.id }.toSet()
            val byCategory = if (cats.isNotEmpty() && near != null) nearbyIn(db, db.withChildren(cats), near) else emptyList()
            (byText + byCategory).map { it to db }
        }.distinctBy { it.first.source + it.first.id }

        hits.map { (hit, db) ->
            val name = hit.tags["name"]
            val n = hit.tags["normalized_name"] ?: name?.let { PoiDatabase.normalize(it) } ?: ""
            var score = when {
                n == norm -> 0
                n.startsWith(norm) -> 1
                n.split(' ', '-').any { it.startsWith(textWords.firstOrNull() ?: norm) } -> 2
                n.contains(norm) -> 3
                else -> 5
            }
            // "Avenue de la Madone 4": the house number must match when one was typed.
            if (numbers.isNotEmpty()) {
                score = if (hit.tags["addr:housenumber"] in numbers) score.coerceAtMost(1) else score + 3
            }
            val place = toPlace(hit, db)
            Triple(SearchResult(place, near?.let { Geo.distance(it, hit.point) }), score, place)
        }.sortedWith(compareBy({ it.second }, { it.first.distance ?: 0.0 }))
            .map { it.first }
            .take(80)
    }

    suspend fun category(cat: QuickCategory, near: LatLon): List<SearchResult> = withContext(Dispatchers.IO) {
        databases().flatMap { db ->
            val roots = db.categories.values.filter { it.names["en"] in cat.names }.map { it.id }.toSet()
            nearbyIn(db, db.withChildren(roots), near).map { toPlace(it, db) }
        }.map { SearchResult(it, Geo.distance(near, it.point)) }
            .sortedBy { it.distance }
            .take(80)
    }

    /** The named POI closest to [p] within [radiusM] metres, for a tap on the map. */
    suspend fun nearest(p: LatLon, radiusM: Double): Place? = withContext(Dispatchers.IO) {
        val dLat = radiusM / 111_000.0
        val dLon = radiusM / (111_000.0 * cos(Math.toRadians(p.lat)).coerceAtLeast(0.1))
        databases().flatMap { db ->
            db.inBox(p.lat - dLat, p.lon - dLon, p.lat + dLat, p.lon + dLon)
                .filter { it.tags["name"] != null || it.tags["addr:housenumber"] != null }
                .map { it to db }
        }.minByOrNull { Geo.distance(p, it.first.point) }
            ?.takeIf { Geo.distance(p, it.first.point) <= radiusM }
            ?.let { (hit, db) -> toPlace(hit, db) }
    }

    /** Speed cameras in the POI data inside a box (for navigation warnings). */
    suspend fun speedCameras(south: Double, west: Double, north: Double, east: Double): List<LatLon> = withContext(Dispatchers.IO) {
        databases().flatMap { db -> db.withTag("highway=speed_camera", south, west, north, east).map { it.point } }
    }

    /** Looks wider and wider around [near] until there are enough results. */
    private fun nearbyIn(db: PoiDatabase, ids: Set<Int>, near: LatLon): List<PoiHit> {
        if (ids.isEmpty()) return emptyList()
        var result = emptyList<PoiHit>()
        for (km in listOf(1.5, 5.0, 20.0, 80.0)) {
            val dLat = km / 111.0
            val dLon = km / (111.0 * cos(Math.toRadians(near.lat)).coerceAtLeast(0.1))
            result = db.inCategories(ids, near.lat - dLat, near.lon - dLon, near.lat + dLat, near.lon + dLon)
            if (result.size >= 25) break
        }
        return result
    }

    private fun toPlace(hit: PoiHit, db: PoiDatabase): Place {
        val cat = hit.categories.firstNotNullOfOrNull { db.categories[it] }?.label(lang)
        val name = hit.tags["name:$lang"] ?: hit.tags["name"] ?: hit.tags["brand"] ?: hit.tags["operator"]
            ?: listOfNotNull(hit.tags["addr:street"], hit.tags["addr:housenumber"]).joinToString(" ").ifBlank { null }
            ?: cat ?: hit.point.format()
        return Place(name, hit.point, hit.tags - "normalized_name", cat)
    }

    suspend fun close() = mutex.withLock {
        open.values.forEach { it.close() }
        open.clear()
    }
}
