package com.vknxworks.mapsctrl.map

import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.routing.NamedWay
import com.vknxworks.mapsctrl.routing.WayNameSource
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.reader.MapFile
import java.io.Closeable
import java.io.File
import java.util.Locale
import kotlin.math.cos

/**
 * Road names from the downloaded Mapsforge .map files, for turn instructions. Reads the named ways of the zoom-16
 * tiles around a point (in the phone's language when the map has it) and keeps a few tiles in memory, so the turns
 * of one route cost a handful of small reads. Plain Java file reading: works off the main thread and in unit tests.
 */
class MapWayNames(files: List<File>, language: String? = Locale.getDefault().language) : WayNameSource, Closeable {

    private val maps: List<MapFile> = files.mapNotNull { f -> runCatching { MapFile(f, language) }.getOrNull() }
    private val cache = object : LinkedHashMap<Long, List<NamedWay>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<NamedWay>>?) = size > 48
    }

    val isEmpty: Boolean get() = maps.isEmpty()

    @Synchronized
    override fun waysNear(p: LatLon, radiusM: Double): List<NamedWay> {
        if (maps.isEmpty()) return emptyList()
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(p.lat)).coerceAtLeast(0.01))
        val x0 = MercatorProjection.longitudeToTileX(p.lon - dLon, ZOOM)
        val x1 = MercatorProjection.longitudeToTileX(p.lon + dLon, ZOOM)
        val y0 = MercatorProjection.latitudeToTileY((p.lat + dLat).coerceAtMost(85.0), ZOOM)
        val y1 = MercatorProjection.latitudeToTileY((p.lat - dLat).coerceAtLeast(-85.0), ZOOM)
        val out = ArrayList<NamedWay>()
        for (x in x0..x1) for (y in y0..y1) out += tile(x, y)
        return out
    }

    private fun tile(x: Int, y: Int): List<NamedWay> = cache.getOrPut((x.toLong() shl 32) or y.toLong()) {
        val t = Tile(x, y, ZOOM, 256)
        maps.filter { m -> runCatching { m.supportsTile(t) }.getOrDefault(false) }.flatMap { m ->
            runCatching { m.readNamedItems(t)?.ways.orEmpty() }.getOrDefault(emptyList()).mapNotNull { w ->
                var name: String? = null
                var ref: String? = null
                var highway: String? = null
                var roundabout = false
                for (tag in w.tags) when (tag.key) {
                    "name" -> name = tag.value
                    "ref" -> ref = tag.value
                    "highway" -> highway = tag.value
                    "junction" -> roundabout = tag.value == "roundabout" || tag.value == "circular"
                }
                // Only roads and paths; named rivers, buildings and areas aren't streets.
                if (highway == null || (name == null && ref == null)) return@mapNotNull null
                val line = w.latLongs.firstOrNull()?.map { LatLon(it.latitude, it.longitude) } ?: return@mapNotNull null
                NamedWay(name, ref, highway, line, roundabout)
            }
        }
    }

    override fun close() {
        maps.forEach { runCatching { it.close() } }
    }

    private companion object {
        const val ZOOM: Byte = 16
    }
}
