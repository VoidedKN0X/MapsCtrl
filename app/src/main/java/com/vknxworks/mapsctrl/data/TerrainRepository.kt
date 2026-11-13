package com.vknxworks.mapsctrl.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.floor

/** A rectangle in degrees. */
data class GeoBox(val south: Double, val west: Double, val north: Double, val east: Double)

/** Terrain data for one downloaded map: the 1°x1° elevation tiles its area needs and how many are on the phone. */
data class TerrainRegion(val mapPath: String, val mapName: String, val tiles: List<String>, val have: Int) {
    val complete: Boolean get() = tiles.isNotEmpty() && have == tiles.size
    val tooBig: Boolean get() = tiles.size > DemTiles.MAX_TILES_PER_REGION
}

sealed interface TerrainJob {
    /** Asking the server how big the tiles are. */
    data class Sizing(val mapPath: String) : TerrainJob
    data class Confirm(val mapPath: String, val tiles: List<String>, val bytes: Long) : TerrainJob
    data class Running(val mapPath: String, val done: Int, val total: Int, val bytes: Long) : TerrainJob
    data class Failed(val mapPath: String, val message: String) : TerrainJob
}

/**
 * Elevation tiles in the SRTM .hgt layout, named after their south-west corner (N43E007), from the public
 * "Terrain Tiles" set on AWS Open Data (the Mapzen/Tilezen "skadi" tiles). Pure logic, unit-tested.
 */
object DemTiles {
    const val BASE_URL = "https://elevation-tiles-prod.s3.amazonaws.com/skadi/"

    /** A big country is a few hundred tiles; more than this is a continent and not worth offering. */
    const val MAX_TILES_PER_REGION = 400

    /** 1 and 3 arc-second tiles: 3601² or 1201² big-endian 16-bit heights. */
    val VALID_SIZES = setOf(3601L * 3601 * 2, 1201L * 1201 * 2)

    private val NAME = Regex("^([NS])(\\d{2})([EW])(\\d{3})$")

    fun name(lat: Int, lon: Int): String {
        val ns = if (lat < 0) "S" else "N"
        val ew = if (lon < 0) "W" else "E"
        return String.format(java.util.Locale.ROOT, "%s%02d%s%03d", ns, kotlin.math.abs(lat), ew, kotlin.math.abs(lon))
    }

    fun isName(s: String) = NAME.matches(s)

    /** "N43E007" -> https://…/skadi/N43/N43E007.hgt.gz */
    fun url(name: String): String {
        require(isName(name))
        return BASE_URL + name.substring(0, 3) + "/" + name + ".hgt.gz"
    }

    /** Every tile touching [box] (west to east, south to north), or the first [cap] + 1 when there are more. */
    fun tilesFor(box: GeoBox, cap: Int = MAX_TILES_PER_REGION): List<String> {
        val s = floor(box.south.coerceIn(-90.0, 89.999)).toInt()
        val n = floor(box.north.coerceIn(-90.0, 89.999)).toInt()
        val w = floor(box.west.coerceIn(-180.0, 179.999)).toInt()
        val e = floor(box.east.coerceIn(-180.0, 179.999)).toInt()
        val out = ArrayList<String>()
        for (lat in s..n) for (lon in w..e) {
            out += name(lat, lon)
            if (out.size > cap) return out
        }
        return out
    }

    /**
     * Turns a downloaded .hgt.gz into the zip Mapsforge reads (one N43E007.hgt entry), checking that it really is
     * a tile of a known size. Writes to [dest]; returns the height data's size.
     */
    fun gzToZip(name: String, gz: InputStream, dest: File): Long {
        require(isName(name))
        var total = 0L
        try {
            ZipOutputStream(dest.outputStream().buffered()).use { zip ->
                zip.setLevel(6)
                zip.putNextEntry(ZipEntry("$name.hgt"))
                GZIPInputStream(gz, 64 * 1024).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > VALID_SIZES.max()) throw IOException("Not an elevation tile")
                        zip.write(buf, 0, n)
                    }
                }
                zip.closeEntry()
            }
            if (total !in VALID_SIZES) throw IOException("Not an elevation tile")
            return total
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }
}

/**
 * Terrain data for hillshading: elevation tiles in filesDir/dem, downloaded per map region only after MapsCtrl
 * has shown their size and you agreed, and deleted per region.
 */
class TerrainRepository(private val context: Context, private val scope: CoroutineScope) {
    val dir = File(context.filesDir, "dem")

    /** Tiles the server doesn't have (beyond the data's reach), remembered so their regions count as complete. */
    private val absentFile = File(dir, "absent.txt")
    @Volatile private var absent: Set<String> = emptySet()

    /** Tile name -> bytes on the phone. */
    private val _files = MutableStateFlow<Map<String, Long>>(emptyMap())
    val files: StateFlow<Map<String, Long>> = _files

    private val _job = MutableStateFlow<TerrainJob?>(null)
    val job: StateFlow<TerrainJob?> = _job

    private val boxes = HashMap<String, GeoBox?>()
    private var running: Job? = null
    @Volatile private var cancelled = false

    init {
        scope.launch(Dispatchers.IO) {
            dir.mkdirs()
            dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            refresh()
        }
    }

    private fun refresh() {
        absent = runCatching { absentFile.readLines().filter { DemTiles.isName(it) }.toSet() }.getOrDefault(emptySet())
        _files.value = dir.listFiles().orEmpty()
            .filter { it.name.endsWith(".zip") && DemTiles.isName(it.name.removeSuffix(".zip")) }
            .associate { it.name.removeSuffix(".zip") to it.length() }
    }

    private fun markAbsent(tiles: Collection<String>) {
        if (tiles.isEmpty()) return
        dir.mkdirs()
        absentFile.writeText((absent + tiles).sorted().joinToString("\n"))
        refresh()
    }

    fun bytesOnPhone(): Long = _files.value.values.sum()

    fun hasAny(): Boolean = _files.value.isNotEmpty()

    /** The area a downloaded map covers, from its header. */
    private suspend fun box(map: File): GeoBox? = withContext(Dispatchers.IO) {
        synchronized(boxes) { if (boxes.containsKey(map.path)) return@withContext boxes[map.path] }
        val b = runCatching {
            val f = MapFile(map)
            try { f.boundingBox().let { GeoBox(it.minLatitude, it.minLongitude, it.maxLatitude, it.maxLongitude) } } finally { f.close() }
        }.getOrNull()
        synchronized(boxes) { boxes[map.path] = b }
        b
    }

    /** One region per downloaded map, with the tiles it needs. */
    suspend fun regions(maps: List<Pair<InstalledMap, File>>): List<TerrainRegion> {
        val have = _files.value.keys + absent
        return maps.mapNotNull { (m, f) ->
            val b = box(f) ?: return@mapNotNull null
            val tiles = DemTiles.tilesFor(b)
            TerrainRegion(m.path, m.name, tiles, tiles.count { it in have })
        }
    }

    /** Asks the server for the size of the tiles [region] still needs, then waits for you to agree. */
    fun prepare(region: TerrainRegion) {
        if (running?.isActive == true || region.tooBig) return
        val missing = region.tiles.filterNot { it in _files.value || it in absent }
        if (missing.isEmpty()) return
        _job.value = TerrainJob.Sizing(region.mapPath)
        running = scope.launch(Dispatchers.IO) {
            try {
                val sem = Semaphore(4)
                val sizes = coroutineScope {
                    missing.map { t -> async { sem.withPermit { t to Http.head(DemTiles.url(t)) } } }.awaitAll()
                }
                // A tile the server doesn't have (S3 answers 403 or 404) is beyond the data: skip it.
                if (sizes.any { (_, h) -> h.first !in 200..299 && h.first != 403 && h.first != 404 }) throw IOException("Server error")
                val present = sizes.filter { it.second.first in 200..299 }.map { it.first to it.second.second }
                markAbsent(sizes.filter { it.second.first == 403 || it.second.first == 404 }.map { it.first })
                _job.value = TerrainJob.Confirm(region.mapPath, present.map { it.first }, present.sumOf { it.second.coerceAtLeast(0L) })
            } catch (e: CancellationException) {
                _job.value = null
            } catch (e: Exception) {
                _job.value = TerrainJob.Failed(region.mapPath, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** You agreed: download the tiles from the confirmation. */
    fun download() {
        val c = _job.value as? TerrainJob.Confirm ?: return
        cancelled = false
        _job.value = TerrainJob.Running(c.mapPath, 0, c.tiles.size, 0)
        running = scope.launch(Dispatchers.IO) {
            dir.mkdirs()
            var bytes = 0L
            try {
                c.tiles.forEachIndexed { i, t ->
                    if (cancelled) throw CancellationException()
                    val dest = File(dir, "$t.zip")
                    if (!dest.exists()) {
                        val gz = File(context.cacheDir, "$t.hgt.gz")
                        try {
                            Http.download(DemTiles.url(t), gz, { cancelled }) { done, _ ->
                                _job.value = TerrainJob.Running(c.mapPath, i, c.tiles.size, bytes + done)
                            }
                            bytes += gz.length()
                            val part = File(dir, "$t.zip.part")
                            gz.inputStream().use { DemTiles.gzToZip(t, it, part) }
                            if (!part.renameTo(dest)) throw IOException("Couldn't save $t")
                        } finally {
                            gz.delete()
                            File(gz.path + ".part").delete()
                        }
                    }
                    _job.value = TerrainJob.Running(c.mapPath, i + 1, c.tiles.size, bytes)
                    refresh()
                }
                _job.value = null
            } catch (e: CancellationException) {
                _job.value = null
            } catch (e: Exception) {
                _job.value = TerrainJob.Failed(c.mapPath, e.message ?: e.javaClass.simpleName)
            } finally {
                dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
                refresh()
            }
        }
    }

    fun cancel() {
        cancelled = true
        if (_job.value !is TerrainJob.Running) {
            running?.cancel()
            _job.value = null
        }
    }

    fun dismiss() {
        if (_job.value is TerrainJob.Failed || _job.value is TerrainJob.Confirm) _job.value = null
    }

    /** Deletes the tiles of [region] that no other region with complete terrain data still uses. */
    suspend fun delete(region: TerrainRegion, others: List<TerrainRegion>) = withContext(Dispatchers.IO) {
        val keep = others.filter { it.mapPath != region.mapPath && it.complete }.flatMap { it.tiles }.toSet()
        region.tiles.filterNot { it in keep }.forEach { File(dir, "$it.zip").delete() }
        refresh()
    }

    /** Deletes every tile. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        refresh()
    }
}
