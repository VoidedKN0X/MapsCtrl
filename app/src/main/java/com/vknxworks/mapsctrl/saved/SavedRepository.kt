package com.vknxworks.mapsctrl.saved

import android.content.Context
import android.net.Uri
import com.vknxworks.mapsctrl.data.crypto.DataKeyStore
import com.vknxworks.mapsctrl.data.crypto.SealedFile
import com.vknxworks.mapsctrl.geo.Geo
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.search.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    /** [ROLE_HOME] or [ROLE_WORK] for the Home and Work shortcuts; at most one place has each. */
    val role: String? = null,
) {
    fun toPlace() = Place(name, point, tags, category, savedId = id)

    companion object {
        const val ROLE_HOME = "home"
        const val ROLE_WORK = "work"
    }
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
    /** Height per point in metres, when the GPX file had it (same shape as [segments]); empty otherwise. */
    val elevations: List<List<Float?>> = emptyList(),
) {
    val lengthMeters: Double get() = segments.sumOf { s -> s.zipWithNext().sumOf { (a, b) -> Geo.distance(a, b) } }

    /** The heights as one list along [segments] flattened, or empty when not every segment has them. */
    fun flatElevations(): List<Float?> =
        if (elevations.size == segments.size && elevations.indices.all { elevations[it].size == segments[it].size }) elevations.flatten() else emptyList()
}

@Serializable
private data class SavedFile(
    val places: List<SavedPlace> = emptyList(),
    val tracks: List<SavedTrack> = emptyList(),
    /** Places you picked in search, newest first (kept in the same encrypted file). */
    val recent: List<Place> = emptyList(),
)

/**
 * Saved places and tracks, in one JSON file in private storage; GPX import and export.
 *
 * The file is encrypted at rest with the app's data key ([DataKeyStore], [SealedFile]). A file from before
 * encryption is still read, and is then replaced by its encrypted copy (see [SealedFile.sealInPlace]). When
 * there is no data key to be had, the ordinary file is read and written, as before. GPX files stay ordinary GPX.
 */
class SavedRepository(private val context: Context, private val scope: CoroutineScope, private val keys: DataKeyStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, FILE)
    private val tmp = File(context.filesDir, TMP)
    private val lock = Mutex()

    private val _places = MutableStateFlow<List<SavedPlace>>(emptyList())
    val places: StateFlow<List<SavedPlace>> = _places
    private val _tracks = MutableStateFlow<List<SavedTrack>>(emptyList())
    val tracks: StateFlow<List<SavedTrack>> = _tracks
    private val _recent = MutableStateFlow<List<Place>>(emptyList())
    /** Places you picked in search, newest first. */
    val recent: StateFlow<List<Place>> = _recent

    private val _ready = MutableStateFlow(false)
    /** The saved file has been read (or there is none). Until then [places] and [tracks] are still empty. */
    val ready: StateFlow<Boolean> = _ready

    private val _unreadable = MutableStateFlow(false)
    /**
     * The saved file is encrypted and can't be opened (the key in the phone's secure storage is gone or
     * changed, or the file is damaged). The file is left exactly as it is, nothing is written while this is
     * true, and the app says what happened instead of showing an empty list.
     */
    val unreadable: StateFlow<Boolean> = _unreadable

    init {
        scope.launch(Dispatchers.IO) { open() }
    }

    private suspend fun open() = lock.withLock {
        val loaded = load()
        if (loaded != null) {
            _places.value = loaded.places
            _tracks.value = loaded.tracks
            _recent.value = loaded.recent
        }
        _unreadable.value = loaded == null
        _ready.value = true
    }

    private fun decode(bytes: ByteArray): SavedFile = json.decodeFromString(SavedFile.serializer(), String(bytes, Charsets.UTF_8))

    /** The contents of a sealed or ordinary file, or null when it's sealed and can't be opened. */
    private fun plainOf(bytes: ByteArray): ByteArray? {
        if (!SealedFile.isSealed(bytes)) return bytes
        val key = keys.get(create = false) ?: return null
        return try {
            SealedFile.open(key, bytes)
        } catch (e: Exception) {
            null
        }
    }

    /** Moves a file that doesn't read as saved places out of the way without deleting it (or an earlier one set aside). */
    private fun setAside(f: File) {
        var aside = File(f.parentFile, ASIDE)
        if (aside.exists()) aside = File(f.parentFile, "$ASIDE.${System.currentTimeMillis()}")
        f.renameTo(aside)
    }

    /**
     * When `saved.json` is missing but a complete `saved.json.tmp` was left next to it (the app died after
     * writing the newest save and before it took the file's place), that copy is put in place. Only a copy
     * that opens and reads as saved places is used; anything else is left where it is.
     */
    private fun recoverMissing() {
        try {
            if (!tmp.exists()) return
            val plain = plainOf(tmp.readBytes()) ?: return
            decode(plain)
            tmp.renameTo(file)
        } catch (e: Exception) {
            // Not a usable copy.
        }
    }

    /**
     * Reads the file. Null means it is encrypted and can't be opened (or can't be read at all); it is left untouched. A file that
     * doesn't read as saved places is kept aside (not overwritten) and the lists start empty. An ordinary
     * file that reads fine is then replaced by its encrypted copy; if that fails for any reason the ordinary
     * file simply stays in use and it is tried again at the next start.
     */
    private fun load(): SavedFile? {
        if (!file.exists()) recoverMissing()
        if (!file.exists()) return SavedFile()
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            // It's there but can't be read right now: treated like a file that can't be opened, so it's never written over.
            return null
        }
        val sealed = SealedFile.isSealed(bytes)
        val plain = plainOf(bytes) ?: return null
        val data = try {
            decode(plain)
        } catch (e: Exception) {
            runCatching { setAside(file) }
            return SavedFile()
        }
        if (!sealed) {
            try {
                val key = keys.get(create = true)
                if (key != null) SealedFile.sealInPlace(key, file, tmp)
            } catch (e: Throwable) {
                // The ordinary file stays in use.
            }
        }
        return data
    }

    /** Waits for the first read of the file, so a change can never be written over places that weren't loaded yet. */
    private suspend fun awaitReady() {
        _ready.first { it }
    }

    private suspend fun write() = withContext(Dispatchers.IO) {
        // Never over a file that couldn't be opened: it stays on disk until it opens again or is given up.
        if (_unreadable.value) return@withContext
        val plain = json.encodeToString(SavedFile.serializer(), SavedFile(_places.value, _tracks.value, _recent.value)).toByteArray(Charsets.UTF_8)
        // Encrypted when there's a data key; the ordinary file otherwise, as before.
        val sealed = keys.get(create = true)?.let { SealedFile.sealChecked(it, plain) }
        SealedFile.writeAtomic(file, tmp, sealed ?: plain)
    }

    /** Tries to open the saved file again, after [unreadable]. */
    fun retry() {
        scope.launch(Dispatchers.IO) {
            keys.reset()
            open()
        }
    }

    /**
     * Gives up on a saved file that can't be opened and starts empty. The file is set aside as
     * `saved.json.unreadable` (and a key that no longer works as `data.key.unreadable`), not deleted. Does
     * nothing unless the file is [unreadable].
     */
    suspend fun startEmpty() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!_unreadable.value) return@withLock
            runCatching {
                if (file.exists()) setAside(file)
            }
            // Only when the file really is out of the way: nothing new may ever be written over it.
            if (file.exists()) return@withLock
            // A key that still works stays; one that's gone makes way for a new one.
            if (keys.get(create = false) == null) keys.discard(keep = true)
            _places.value = emptyList()
            _tracks.value = emptyList()
            _recent.value = emptyList()
            _unreadable.value = false
        }
    }

    suspend fun save(place: Place): SavedPlace {
        awaitReady()
        return lock.withLock { saveLocked(place) }
    }

    private suspend fun saveLocked(place: Place): SavedPlace {
        val existing = _places.value.firstOrNull { it.id == place.savedId }
        val sp = existing?.copy(name = place.name) ?: SavedPlace(name = place.name, point = place.point, tags = place.tags, category = place.category)
        if (_unreadable.value) return sp
        _places.value = _places.value.filterNot { it.id == sp.id } + sp
        write()
        return sp
    }

    /** One change to the lists, after the first read and never while the file is [unreadable]. */
    private suspend fun change(block: () -> Unit) {
        awaitReady()
        lock.withLock {
            if (_unreadable.value) return@withLock
            block()
            // This write also takes a waiting recent-search change along.
            recentWrite?.let { if (it != currentCoroutineContext()[Job]) it.cancel() }
            recentWrite = null
            write()
        }
    }

    suspend fun updatePlace(p: SavedPlace) = change {
        _places.value = _places.value.map { if (it.id == p.id) p else it }
    }

    suspend fun deletePlace(id: String) = change {
        _places.value = _places.value.filterNot { it.id == id }
    }

    /**
     * Makes [place] your Home or Work ([role]): it's saved first when it isn't yet, and the place that had the
     * role before loses it (it stays saved).
     */
    suspend fun setRole(place: Place, role: String) {
        awaitReady()
        lock.withLock {
            if (_unreadable.value) return
            val existing = _places.value.firstOrNull { it.id == place.savedId }
                ?: _places.value.firstOrNull { it.point == place.point && it.name == place.name }
            val target = existing ?: SavedPlace(name = place.name, point = place.point, tags = place.tags, category = place.category)
            _places.value = _places.value.filterNot { it.id == target.id }.map { if (it.role == role) it.copy(role = null) else it } + target.copy(role = role)
            write()
        }
    }

    /** Takes the Home or Work role off a place (the place stays saved). */
    suspend fun clearRole(role: String) = change {
        _places.value = _places.value.map { if (it.role == role) it.copy(role = null) else it }
    }

    /** A recent-search write waiting to happen (see [addRecent]). */
    private var recentWrite: Job? = null

    /**
     * Puts a place you picked in search at the top of the recent list (at most [MAX_RECENT]). The list shows the
     * change straight away; the file (all places and tracks, encrypted) is written [RECENT_WRITE_DELAY_MS] later,
     * once for several picks in a row, or sooner with any other change, [flushRecent] or [clearRecent].
     */
    suspend fun addRecent(place: Place) {
        awaitReady()
        lock.withLock {
            if (_unreadable.value) return
            val p = place.copy(savedId = null)
            _recent.value = (listOf(p) + _recent.value.filterNot { it.name == p.name && it.point == p.point }).take(MAX_RECENT)
            recentWrite?.cancel()
            recentWrite = scope.launch {
                delay(RECENT_WRITE_DELAY_MS)
                flushRecent()
            }
        }
    }

    /** Writes a waiting recent-search change now (when the app goes to the background). */
    suspend fun flushRecent() {
        if (recentWrite == null) return
        change { }
    }

    /** Forgets every recent search. */
    suspend fun clearRecent() = change {
        _recent.value = emptyList()
    }

    /**
     * "Forgot PIN?" on the app lock: every saved place, track and recent search is deleted (the file is written
     * empty, encrypted as before). A file that can't be opened is left as it is: nobody can read it anyway, and
     * files that can't be opened are never written over.
     */
    suspend fun deleteAll() = change {
        _places.value = emptyList()
        _tracks.value = emptyList()
        _recent.value = emptyList()
    }

    suspend fun addTrack(t: SavedTrack) = change {
        _tracks.value = _tracks.value + t
    }

    suspend fun updateTrack(t: SavedTrack) = change {
        _tracks.value = _tracks.value.map { if (it.id == t.id) t else it }
    }

    suspend fun deleteTrack(id: String) = change {
        _tracks.value = _tracks.value.filterNot { it.id == id }
    }

    /** Imports a GPX file: waypoints become places, tracks and routes become tracks. Returns (places, tracks). */
    suspend fun importGpx(uri: Uri): Pair<Int, Int> {
        val data = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                Gpx.read(
                    input,
                    trackName = { n -> context.getString(com.vknxworks.mapsctrl.R.string.gpx_track_n, n) },
                    routeName = { n -> context.getString(com.vknxworks.mapsctrl.R.string.gpx_route_n, n) },
                )
            }
        } ?: return 0 to 0
        awaitReady()
        lock.withLock {
            if (_unreadable.value) return 0 to 0
            _places.value = _places.value + data.waypoints.map { w ->
                SavedPlace(name = w.name, point = w.point, note = w.description.orEmpty(), created = w.time?.toEpochMilli() ?: System.currentTimeMillis())
            }
            _tracks.value = _tracks.value + data.tracks.map { t ->
                SavedTrack(name = t.name, segments = t.segments, times = t.times.map { s -> s.map { it?.toEpochMilli() } }, elevations = t.elevations)
            }
            write()
        }
        return data.waypoints.size to data.tracks.size
    }

    /** Writes places and tracks (all, or just the ones given) as GPX. */
    suspend fun exportGpx(uri: Uri, places: List<SavedPlace> = _places.value, tracks: List<SavedTrack> = _tracks.value) = withContext(Dispatchers.IO) {
        val data = GpxData(
            waypoints = places.map { GpxWaypoint(it.point, it.name, it.note.ifBlank { null }, Instant.ofEpochMilli(it.created)) },
            tracks = tracks.map { t -> GpxTrack(t.name, t.segments, t.times.map { s -> s.map { ms -> ms?.let { Instant.ofEpochMilli(it) } } }, t.elevations) },
        )
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { Gpx.write(it, data, "MapsCtrl") }
    }

    private companion object {
        const val FILE = "saved.json"
        const val TMP = "saved.json.tmp"
        const val ASIDE = "saved.json.unreadable"
        const val MAX_RECENT = 15
        const val RECENT_WRITE_DELAY_MS = 5_000L
    }
}
