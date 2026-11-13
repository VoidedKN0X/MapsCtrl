package com.vknxworks.mapsctrl.data

import android.content.Context
import com.vknxworks.mapsctrl.BuildConfig
import com.vknxworks.mapsctrl.routing.Segments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

data class SegmentFile(val tile: String, val bytes: Long, val modified: Long)

/**
 * BRouter's routing data: 5x5 degree .rd5 tiles from brouter.de, downloaded only when you agree, plus the routing
 * profiles bundled with the app (copied out of the APK so BRouter can read them as files).
 */
class SegmentRepository(private val context: Context, private val scope: CoroutineScope) {
    val segmentDir = File(context.filesDir, "brouter/segments4")
    val profileDir = File(context.filesDir, "brouter/profiles2")

    private val _files = MutableStateFlow<List<SegmentFile>>(emptyList())
    val files: StateFlow<List<SegmentFile>> = _files

    /** Tile -> (bytes so far, total), while downloading. */
    private val _progress = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val progress: StateFlow<Map<String, Pair<Long, Long>>> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var serverSizes: Map<String, Long>? = null
    private var job: Job? = null
    @Volatile private var cancelled = false

    init {
        scope.launch(Dispatchers.IO) {
            segmentDir.mkdirs()
            installProfiles()
            segmentDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            refresh()
        }
    }

    /** Copies the bundled profiles out of the APK once per app version. */
    private fun installProfiles() {
        profileDir.mkdirs()
        val stamp = File(profileDir, ".version")
        if (stamp.exists() && stamp.readText() == BuildConfig.VERSION_CODE.toString()) return
        context.assets.list("brouter")?.forEach { name ->
            context.assets.open("brouter/$name").use { input -> File(profileDir, name).outputStream().use { input.copyTo(it) } }
        }
        stamp.writeText(BuildConfig.VERSION_CODE.toString())
    }

    fun ensureProfiles() = installProfiles()

    private fun refresh() {
        _files.value = segmentDir.listFiles().orEmpty().filter { it.name.endsWith(".rd5") }
            .map { SegmentFile(it.name.removeSuffix(".rd5"), it.length(), it.lastModified()) }
            .sortedBy { it.tile }
    }

    fun has(tile: String) = File(segmentDir, "$tile.rd5").exists()

    fun bytesOnPhone(): Long = _files.value.sumOf { it.bytes }

    /** Download sizes of [tiles] from the server's listing (fetched once). Tiles the server doesn't have are left out. */
    suspend fun sizes(tiles: Collection<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        val all = serverSizes ?: DirectoryIndex.parse(Http.getText(Segments.BASE_URL))
            .filter { it.name.endsWith(".rd5") }
            .associate { it.name.removeSuffix(".rd5") to (it.bytes ?: 0L) }
            .also { serverSizes = it }
        tiles.mapNotNull { t -> all[t]?.let { t to it } }.toMap()
    }

    fun download(tiles: Collection<String>, onDone: (Boolean) -> Unit = {}) {
        if (job?.isActive == true) return
        cancelled = false
        _error.value = null
        job = scope.launch(Dispatchers.IO) {
            var ok = true
            for (t in tiles) {
                if (has(t)) continue
                try {
                    _progress.update { it + (t to (0L to -1L)) }
                    Http.download(Segments.BASE_URL + "$t.rd5", File(segmentDir, "$t.rd5"), { cancelled }) { done, total ->
                        _progress.update { it + (t to (done to total)) }
                    }
                } catch (e: CancellationException) {
                    ok = false
                    File(segmentDir, "$t.rd5.part").delete()
                    break
                } catch (e: Exception) {
                    ok = false
                    _error.value = e.message ?: e.javaClass.simpleName
                    break
                } finally {
                    _progress.update { it - t }
                }
            }
            refresh()
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun cancel() {
        cancelled = true
    }

    suspend fun delete(tile: String) = withContext(Dispatchers.IO) {
        File(segmentDir, "$tile.rd5").delete()
        refresh()
    }
}
