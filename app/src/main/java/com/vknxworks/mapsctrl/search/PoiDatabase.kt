package com.vknxworks.mapsctrl.search

import android.database.sqlite.SQLiteDatabase
import com.vknxworks.mapsctrl.geo.LatLon
import java.io.Closeable
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** A point of interest from a POI file. */
data class PoiHit(val id: Long, val point: LatLon, val tags: Map<String, String>, val categories: List<Int>, val source: String)

data class PoiCategory(val id: Int, val names: Map<String, String>, val parent: Int?) {
    fun label(lang: String): String = names[lang] ?: names["en"] ?: names.values.firstOrNull() ?: ""
}

/**
 * Reads a Mapsforge POI file (version 5): an SQLite database with the tags of each POI as "key=value" lines,
 * an R-tree of positions, a category tree, and (in newer files) a trigram full-text index over the tags.
 * Opened read-only; nothing is ever written to it.
 */
class PoiDatabase(val file: File) : Closeable {
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.path,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
    )

    val categories: Map<Int, PoiCategory> = runCatching {
        db.rawQuery("SELECT id, name, parent FROM poi_categories", null).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val names = c.getString(1).orEmpty().split('\r', '\n').mapNotNull { kv ->
                        val i = kv.indexOf('='); if (i <= 0) null else kv.substring(0, i) to kv.substring(i + 1)
                    }.toMap().ifEmpty { mapOf("en" to c.getString(1).orEmpty()) }
                    put(c.getInt(0), PoiCategory(c.getInt(0), names, if (c.isNull(2)) null else c.getInt(2)))
                }
            }
        }
    }.getOrDefault(emptyMap())

    /** Whether the file has the trigram index (and the phone's SQLite can use it). */
    private val hasFts: Boolean = runCatching {
        db.rawQuery("SELECT rowid FROM poi_data_fts WHERE data LIKE ? LIMIT 1", arrayOf("%abc%")).use { true }
    }.getOrDefault(false)

    /** POIs whose tags contain every word of [words] (each at least 3 letters for the fast index). */
    fun searchText(words: List<String>, limit: Int = 300): List<PoiHit> {
        val usable = words.filter { it.length >= 2 }.ifEmpty { return emptyList() }
        val (from, idCol) = if (hasFts && usable.all { it.length >= 3 }) "poi_data_fts f" to "f.rowid" else "poi_data f" to "f.id"
        val where = usable.joinToString(" AND ") { "f.data LIKE ?" }
        val sql = "SELECT d.id, d.data, i.minLat, i.minLon FROM $from JOIN poi_data d ON d.id = $idCol " +
            "JOIN poi_index i ON i.id = d.id WHERE $where LIMIT $limit"
        return query(sql, usable.map { "%${it.replace("%", "").replace("_", "")}%" }.toTypedArray())
    }

    /** POIs in any of [categoryIds] inside the box. */
    fun inCategories(categoryIds: Set<Int>, south: Double, west: Double, north: Double, east: Double, limit: Int = 300): List<PoiHit> {
        if (categoryIds.isEmpty()) return emptyList()
        val ids = categoryIds.joinToString(",")
        val sql = "SELECT DISTINCT d.id, d.data, i.minLat, i.minLon FROM poi_index i JOIN poi_category_map m ON m.id = i.id " +
            "JOIN poi_data d ON d.id = i.id WHERE i.minLat <= ? AND i.maxLat >= ? AND i.minLon <= ? AND i.maxLon >= ? " +
            "AND m.category IN ($ids) LIMIT $limit"
        return query(sql, arrayOf(north.toString(), south.toString(), east.toString(), west.toString()))
    }

    /** Any POIs inside the box (for taps on the map). */
    fun inBox(south: Double, west: Double, north: Double, east: Double, limit: Int = 50): List<PoiHit> {
        val sql = "SELECT d.id, d.data, i.minLat, i.minLon FROM poi_index i JOIN poi_data d ON d.id = i.id " +
            "WHERE i.minLat <= ? AND i.maxLat >= ? AND i.minLon <= ? AND i.maxLon >= ? LIMIT $limit"
        return query(sql, arrayOf(north.toString(), south.toString(), east.toString(), west.toString()))
    }

    /** POIs inside the box whose tags contain [tag] ("highway=speed_camera"). */
    fun withTag(tag: String, south: Double, west: Double, north: Double, east: Double, limit: Int = 500): List<PoiHit> {
        val sql = "SELECT d.id, d.data, i.minLat, i.minLon FROM poi_index i JOIN poi_data d ON d.id = i.id " +
            "WHERE i.minLat <= ? AND i.maxLat >= ? AND i.minLon <= ? AND i.maxLon >= ? AND d.data LIKE ? LIMIT $limit"
        return query(sql, arrayOf(north.toString(), south.toString(), east.toString(), west.toString(), "%$tag%"))
            .filter { hit -> tag.substringBefore('=').let { k -> hit.tags[k] == tag.substringAfter('=') } }
    }

    /** The category ids under [roots], including the roots. */
    fun withChildren(roots: Set<Int>): Set<Int> {
        val out = roots.toMutableSet()
        var grew = true
        while (grew) {
            grew = false
            for (c in categories.values) if (c.parent != null && c.parent in out && out.add(c.id)) grew = true
        }
        return out
    }

    private fun query(sql: String, args: Array<String>): List<PoiHit> = runCatching {
        db.rawQuery(sql, args).use { c ->
            val list = ArrayList<PoiHit>(c.count)
            val ids = ArrayList<Long>()
            while (c.moveToNext()) {
                val id = c.getLong(0)
                ids += id
                list += PoiHit(id, LatLon(c.getDouble(2), c.getDouble(3)), parseTags(c.getString(1)), emptyList(), file.name)
            }
            val cats = categoriesOf(ids)
            list.map { it.copy(categories = cats[it.id].orEmpty()) }
        }
    }.getOrDefault(emptyList())

    private fun categoriesOf(ids: List<Long>): Map<Long, List<Int>> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<Long, MutableList<Int>>()
        ids.chunked(500).forEach { chunk ->
            db.rawQuery("SELECT id, category FROM poi_category_map WHERE id IN (${chunk.joinToString(",")})", null).use { c ->
                while (c.moveToNext()) out.getOrPut(c.getLong(0)) { mutableListOf() } += c.getInt(1)
            }
        }
        return out
    }

    override fun close() = db.close()

    companion object {
        fun parseTags(data: String?): Map<String, String> {
            if (data.isNullOrEmpty()) return emptyMap()
            return data.split('\r', '\n').mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i <= 0) null else kv.substring(0, i) to kv.substring(i + 1)
            }.toMap()
        }

        /** Lower case without accents, as the POI files' normalized_name: "Café Paris" -> "cafe paris". */
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
    }
}
