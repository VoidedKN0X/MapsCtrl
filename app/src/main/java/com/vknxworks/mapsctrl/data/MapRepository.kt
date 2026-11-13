package com.vknxworks.mapsctrl.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.CancellationException

/** A map on the phone: a Mapsforge .map file, and its search data (.poi) when downloaded. */
@Serializable
data class InstalledMap(
    /** Server path without extension, e.g. "europe/monaco"; "imported/<name>" for files you imported. */
    val path: String,
    val name: String,
    val mapBytes: Long,
    val poiBytes: Long = 0,
    /** The server's date for the map file, to spot updates. */
    val serverDate: String? = null,
    val installedAt: Long = System.currentTimeMillis(),
    val imported: Boolean = false,
)

/** A region or folder on the download server. */
data class CatalogItem(
    val path: String,
    val name: String,
    val isFolder: Boolean,
    val mapBytes: Long?,
    val poiBytes: Long?,
    val modified: String?,
)

sealed interface DownloadState {
    data class Running(val done: Long, val total: Long, val searchData: Boolean) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Maps from download.mapsforge.org: browsing the server's folders, downloads with progress, updates, deleting,
 * and importing a .map file you already have. Files live in the app's private storage.
 */
class MapRepository(private val context: Context, private val scope: CoroutineScope) {
    companion object {
        const val MAPS_BASE = "https://download.mapsforge.org/maps/v5/"
        const val POIS_BASE = "https://download.mapsforge.org/pois/v5/"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mapsDir = File(context.filesDir, "maps")
    private val poiDir = File(context.filesDir, "poi")
    private val indexFile = File(mapsDir, "installed.json")
    private val lock = Mutex()

    private val _installed = MutableStateFlow<List<InstalledMap>>(emptyList())
    val installed: StateFlow<List<InstalledMap>> = _installed

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads

    /** Paths with a newer map on the server (after [checkUpdates]). */
    private val _updates = MutableStateFlow<Set<String>>(emptySet())
    val updates: StateFlow<Set<String>> = _updates

    private val jobs = HashMap<String, Job>()
    private val cancelled = HashSet<String>()

    init {
        scope.launch(Dispatchers.IO) {
            mapsDir.mkdirs()
            poiDir.mkdirs()
            val list = runCatching { json.decodeFromString<List<InstalledMap>>(indexFile.readText()) }.getOrDefault(emptyList())
            // Drop entries whose file is gone, and clean up half-finished downloads.
            _installed.value = list.filter { mapFile(it.path).exists() }
            mapsDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            poiDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        }
    }

    private fun fileKey(path: String) = path.replace('/', '~')
    fun mapFile(path: String) = File(mapsDir, fileKey(path) + ".map")
    fun poiFile(path: String) = File(poiDir, fileKey(path) + ".poi")

    fun mapFiles(): List<File> = _installed.value.map { mapFile(it.path) }.filter { it.exists() }
    fun poiFiles(): List<File> = _installed.value.map { poiFile(it.path) }.filter { it.exists() }

    fun storageBytes(): Long = (mapsDir.listFiles().orEmpty() + poiDir.listFiles().orEmpty()).sumOf { it.length() }

    private suspend fun save(transform: (List<InstalledMap>) -> List<InstalledMap>) = lock.withLock {
        val next = transform(_installed.value).sortedBy { it.name.lowercase() }
        _installed.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(indexFile.path + ".tmp")
            tmp.writeText(json.encodeToString(next))
            tmp.renameTo(indexFile)
        }
    }

    /** The regions and folders in [folder] ("" for the top, "europe/", "europe/germany/"). */
    suspend fun browse(folder: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val maps = async { DirectoryIndex.parse(Http.getText(MAPS_BASE + folder)) }
            val pois = async { runCatching { DirectoryIndex.parse(Http.getText(POIS_BASE + folder)) }.getOrDefault(emptyList()) }
            val poiSizes = pois.await().filter { it.name.endsWith(".poi") }.associate { it.name.removeSuffix(".poi") to it.bytes }
            val entries = maps.await()
            val serverDates = entries.filter { it.name.endsWith(".map") }.associate { folder + it.name.removeSuffix(".map") to it.modified }
            // While we're here, note updates for installed maps in this folder.
            val newer = _installed.value.filter { m -> serverDates[m.path]?.let { it != m.serverDate } == true }.map { it.path }
            if (newer.isNotEmpty()) _updates.update { it + newer }
            entries.mapNotNull { e ->
                when {
                    e.isDirectory -> CatalogItem(folder + e.name, DirectoryIndex.displayName(e.name), true, null, null, e.modified)
                    e.name.endsWith(".map") -> {
                        val base = e.name.removeSuffix(".map")
                        CatalogItem(folder + base, DirectoryIndex.displayName(base), false, e.bytes, poiSizes[base], e.modified)
                    }
                    else -> null
                }
            }.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
        }
    }

    /** Asks the server for newer versions of every downloaded map. */
    suspend fun checkUpdates() {
        val folders = _installed.value.filterNot { it.imported }.map { it.path.substringBeforeLast('/', "").let { f -> if (f.isEmpty()) "" else "$f/" } }.distinct()
        for (f in folders) runCatching { browse(f) }
    }

    fun download(item: CatalogItem, withSearchData: Boolean) {
        if (item.isFolder || jobs[item.path]?.isActive == true) return
        cancelled.remove(item.path)
        _downloads.update { it + (item.path to DownloadState.Running(0, item.mapBytes ?: -1, false)) }
        jobs[item.path] = scope.launch(Dispatchers.IO) {
            try {
                val mapUrl = MAPS_BASE + item.path + ".map"
                val dest = mapFile(item.path)
                Http.download(mapUrl, dest, { item.path in cancelled }) { done, total ->
                    _downloads.update { it + (item.path to DownloadState.Running(done, total, false)) }
                }
                var poiBytes = 0L
                if (withSearchData) {
                    val poi = poiFile(item.path)
                    runCatching {
                        Http.download(POIS_BASE + item.path + ".poi", poi, { item.path in cancelled }) { done, total ->
                            _downloads.update { it + (item.path to DownloadState.Running(done, total, true)) }
                        }
                        poiBytes = poi.length()
                    }.onFailure { if (it is CancellationException) throw it }
                }
                save { list ->
                    list.filterNot { it.path == item.path } + InstalledMap(
                        path = item.path,
                        name = item.name,
                        mapBytes = dest.length(),
                        poiBytes = poiBytes,
                        serverDate = item.modified,
                    )
                }
                _updates.update { it - item.path }
                _downloads.update { it - item.path }
            } catch (e: CancellationException) {
                File(mapFile(item.path).path + ".part").delete()
                File(poiFile(item.path).path + ".part").delete()
                _downloads.update { it - item.path }
            } catch (e: Exception) {
                _downloads.update { it + (item.path to DownloadState.Failed(e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    /** Updates a downloaded map to the server's current version. */
    fun update(map: InstalledMap, withSearchData: Boolean) {
        val folder = map.path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        scope.launch {
            val item = runCatching { browse(folder) }.getOrNull()?.firstOrNull { it.path == map.path }
            if (item == null) {
                _downloads.update { it + (map.path to DownloadState.Failed("not on the server")) }
            } else {
                download(item, withSearchData || map.poiBytes > 0)
            }
        }
    }

    fun cancel(path: String) {
        cancelled += path
        jobs[path]?.cancel()
        _downloads.update { it - path }
        File(mapFile(path).path + ".part").delete()
        File(poiFile(path).path + ".part").delete()
    }

    fun dismissError(path: String) = _downloads.update { it - path }

    suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            mapFile(path).delete()
            poiFile(path).delete()
        }
        save { list -> list.filterNot { it.path == path } }
    }

    /** Copies a .map file picked in the system file picker. Returns its name, or null when it isn't a Mapsforge map. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val display = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "map.map"
        val base = display.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "map" }
        val path = "imported/$base"
        val dest = mapFile(path)
        val tmp = File(dest.path + ".part")
        resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it, 256 * 1024) } } ?: return@withContext null
        // A Mapsforge map starts with this magic text.
        val magic = tmp.inputStream().use { s -> ByteArray(20).also { s.read(it) } }
        if (String(magic, Charsets.US_ASCII) != "mapsforge binary OSM") {
            tmp.delete()
            return@withContext null
        }
        dest.delete()
        tmp.renameTo(dest)
        save { list -> list.filterNot { it.path == path } + InstalledMap(path, DirectoryIndex.displayName(base), dest.length(), imported = true) }
        DirectoryIndex.displayName(base)
    }
}
