package com.vknxworks.mapsctrl.saved

import android.content.Context
import android.net.Uri
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.search.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val point: LatLon,
    val note: String = "",
    val tags: Map<String, String> = emptyMap(),
    val category: String? = null,
    val created: Long = System.currentTimeMillis(),
) {
    fun toPlace() = Place(name, point, tags, category, savedId = id)
}

@Serializable
data class SavedTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val segments: List<List<LatLon>>,
    /** Epoch millis per point, when recorded on this phone (same shape as [segments]). */
    val times: List<List<Long?>> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val visible: Boolean = true,
) {
    val lengthMeters: Double get() = segments.sumOf { s -> s.zipWithNext().sumOf { (a, b) -> Geo.distance(a, b) } }
}

@Serializable
private data class SavedFile(val places: List<SavedPlace> = emptyList(), val tracks: List<SavedTrack> = emptyList())

/** Saved places and tracks, in one JSON file in private storage; GPX import and export. */
class SavedRepository(private val context: Context, scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "saved.json")
    private val lock = Mutex()

    private val _places = MutableStateFlow<List<SavedPlace>>(emptyList())
    val places: StateFlow<List<SavedPlace>> = _places
    private val _tracks = MutableStateFlow<List<SavedTrack>>(emptyList())
    val tracks: StateFlow<List<SavedTrack>> = _tracks

    init {
        scope.launch(Dispatchers.IO) {
            val data = runCatching { json.decodeFromString<SavedFile>(file.readText()) }.getOrDefault(SavedFile())
            _places.value = data.places
            _tracks.value = data.tracks
        }
    }

    private suspend fun write() = withContext(Dispatchers.IO) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(SavedFile(_places.value, _tracks.value)))
        tmp.renameTo(file)
    }

    suspend fun save(place: Place): SavedPlace = lock.withLock {
        val existing = _places.value.firstOrNull { it.id == place.savedId }
        val sp = existing?.copy(name = place.name) ?: SavedPlace(name = place.name, point = place.point, tags = place.tags, category = place.category)
        _places.value = _places.value.filterNot { it.id == sp.id } + sp
        write()
        sp
    }

    suspend fun updatePlace(p: SavedPlace) = lock.withLock {
        _places.value = _places.value.map { if (it.id == p.id) p else it }
        write()
    }

    suspend fun deletePlace(id: String) = lock.withLock {
        _places.value = _places.value.filterNot { it.id == id }
        write()
    }

    suspend fun addTrack(t: SavedTrack) = lock.withLock {
        _tracks.value = _tracks.value + t
        write()
    }

    suspend fun updateTrack(t: SavedTrack) = lock.withLock {
        _tracks.value = _tracks.value.map { if (it.id == t.id) t else it }
        write()
    }

    suspend fun deleteTrack(id: String) = lock.withLock {
        _tracks.value = _tracks.value.filterNot { it.id == id }
        write()
    }

    /** Imports a GPX file: waypoints become places, tracks and routes become tracks. Returns (places, tracks). */
    suspend fun importGpx(uri: Uri): Pair<Int, Int> {
        val data = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { Gpx.read(it) }
        } ?: return 0 to 0
        lock.withLock {
            _places.value = _places.value + data.waypoints.map { w ->
                SavedPlace(name = w.name, point = w.point, note = w.description.orEmpty(), created = w.time?.toEpochMilli() ?: System.currentTimeMillis())
            }
            _tracks.value = _tracks.value + data.tracks.map { t ->
                SavedTrack(name = t.name, segments = t.segments, times = t.times.map { s -> s.map { it?.toEpochMilli() } })
            }
            write()
        }
        return data.waypoints.size to data.tracks.size
    }

    /** Writes places and tracks (all, or just the ones given) as GPX. */
    suspend fun exportGpx(uri: Uri, places: List<SavedPlace> = _places.value, tracks: List<SavedTrack> = _tracks.value) = withContext(Dispatchers.IO) {
        val data = GpxData(
            waypoints = places.map { GpxWaypoint(it.point, it.name, it.note.ifBlank { null }, Instant.ofEpochMilli(it.created)) },
            tracks = tracks.map { t -> GpxTrack(t.name, t.segments, t.times.map { s -> s.map { ms -> ms?.let { Instant.ofEpochMilli(it) } } }) },
        )
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { Gpx.write(it, data, "MapsCtrl") }
    }
}
