package com.vknxworks.mapsctrl.map

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.location.Fix
import com.vknxworks.mapsctrl.routing.Route
import com.vknxworks.mapsctrl.saved.SavedPlace
import com.vknxworks.mapsctrl.saved.SavedTrack
import com.vknxworks.mapsctrl.transit.TransitLeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading
import org.mapsforge.map.layer.hills.DemFolderFS
import org.mapsforge.map.layer.hills.HillsRenderConfig
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import java.io.File

/** Where the map is looking. */
data class Camera(val center: LatLon, val zoom: Double, val rotation: Float, val metersPerPixel: Double)

/** Mapsforge's MapView, told when you drag the map yourself (that ends "follow my position"). */
@SuppressLint("ViewConstructor")
class CtrlMapView(context: Context, private val onUserPan: () -> Unit) : MapView(context) {
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
            MotionEvent.ACTION_MOVE -> if (Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > slop) onUserPan()
            MotionEvent.ACTION_POINTER_DOWN -> onUserPan()
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Everything on the map: the vector map from your downloaded .map files in the Ctrl style, the route, saved
 * places and tracks, the selected place and your position. One per screen.
 */
class MapController(private val context: Context, start: LatLon, startZoom: Float) {

    private val _camera = MutableStateFlow(Camera(start, startZoom.toDouble(), 0f, 0.0))
    val camera: StateFlow<Camera> = _camera

    var onTap: (LatLon, Float, Float) -> Unit = { _, _, _ -> }
    var onLongPress: (LatLon) -> Unit = {}
    var onUserPan: () -> Unit = {}

    val view: CtrlMapView = CtrlMapView(context) { onUserPan() }
    private val tileCache: TileCache
    private var renderer: TileRendererLayer? = null
    private var dataStore: MultiMapDataStore? = null
    private var mapFiles: List<File> = emptyList()
    private var night = false
    private var hills: HillsRenderConfig? = null
    private var hillsKey: String? = null

    private val graphics = AndroidGraphicFactory.INSTANCE
    private val trackLayers = mutableListOf<Polyline>()
    private val routeCasing = line(0xFFFFFFFF.toInt(), 9f)
    private val routeLine = line(0xFF8F5CFF.toInt(), 6f)
    private val recording = line(0xFFB48CFF.toInt(), 4f)
    private val overlay = OverlayLayer(context)
    private val tap = TapLayer()

    init {
        view.isClickable = true
        view.mapScaleBar.isVisible = false
        view.setBuiltInZoomControls(false)
        view.model.displayModel.backgroundColor = 0xFFE2E2DD.toInt()
        view.model.mapViewPosition.setZoomLevelMin(2.toByte())
        view.model.mapViewPosition.setZoomLevelMax(20.toByte())
        view.model.mapViewPosition.mapPosition = MapPosition(LatLong(start.lat, start.lon), startZoom.toInt().toByte())
        tileCache = AndroidUtil.createTileCache(
            // Room for the square frame buffer a turned map needs (the screen's diagonal in both directions).
            context, "ctrl-tiles", view.model.displayModel.tileSize, 2.5f,
            view.model.frameBufferModel.overdrawFactor,
        )
        view.model.mapViewPosition.addObserver(object : Observer {
            override fun onChange() = publishCamera()
        })
        rebuildRenderer()
        val layers = view.layerManager.layers
        trackLayers.clear()
        layers.add(routeCasing)
        layers.add(routeLine)
        layers.add(recording)
        layers.add(overlay)
        layers.add(tap)
        publishCamera()
    }

    private fun line(color: Int, widthDp: Float): Polyline {
        val p = graphics.createPaint()
        p.color = color
        p.strokeWidth = widthDp * context.resources.displayMetrics.density
        p.setStyle(Style.STROKE)
        p.setStrokeCap(Cap.ROUND)
        p.setStrokeJoin(Join.ROUND)
        return Polyline(p, graphics)
    }

    private fun publishCamera() {
        val pos = view.model.mapViewPosition
        val c = pos.center ?: return
        val zoom = pos.zoom
        val tile = view.model.displayModel.tileSize
        val mapSize = tile * Math.pow(2.0, zoom)
        val mpp = Math.cos(Math.toRadians(c.latitude)) * 2 * Math.PI * 6378137.0 / mapSize
        _camera.value = Camera(LatLon(c.latitude, c.longitude), zoom, pos.rotation?.degrees ?: 0f, mpp)
    }

    // ---------- Map data and style ----------

    fun setMaps(files: List<File>) {
        if (files == mapFiles) return
        mapFiles = files
        rebuildRenderer()
    }

    fun setNight(isNight: Boolean) {
        if (isNight == night && renderer != null) return
        night = isNight
        view.model.displayModel.backgroundColor = if (night) 0xFF0B0B0D.toInt() else 0xFFE2E2DD.toInt()
        tileCache.purge()
        renderer?.setXmlRenderTheme(theme())
        view.layerManager.redrawLayers()
    }

    private fun theme(): ExternalRenderTheme {
        val dir = File(context.filesDir, if (night) "theme/night" else "theme/day")
        return ExternalRenderTheme(CtrlRenderTheme.write(dir, if (night) CtrlRenderTheme.Night else CtrlRenderTheme.Day))
    }

    private fun rebuildRenderer() {
        val layers = view.layerManager.layers
        renderer?.let { layers.remove(it); it.onDestroy() }
        dataStore?.close()
        val store = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        for (f in mapFiles) runCatching { store.addMapDataStore(MapFile(f), false, false) }
        dataStore = store
        tileCache.purge()
        // Labels are drawn into the tiles: Mapsforge 0.25's separate label layer turns some street names upside down.
        val r = TileRendererLayer(tileCache, store, view.model.mapViewPosition, false, true, false, graphics, hills)
        r.setXmlRenderTheme(theme())
        renderer = r
        layers.add(0, r)
        view.layerManager.redrawLayers()
    }

    /**
     * Hillshading from the elevation tiles in [demDir], or off with null. [version] changes when tiles were added or
     * removed, so the tile index is read again.
     */
    fun setHillshading(demDir: File?, version: Int) {
        val key = demDir?.let { "${it.path}#$version" }
        if (key == hillsKey) return
        hillsKey = key
        hills?.interruptAndDestroy()
        hills = demDir?.let { d ->
            runCatching {
                // The strength comes from the theme (stronger at night, on the dark map).
                HillsRenderConfig(MemoryCachingHgtReaderTileSource(DemFolderFS(d), AdaptiveClasyHillShading(), graphics))
                    .indexOnThread()
            }.getOrNull()
        }
        rebuildRenderer()
    }

    /** The area covered by the downloaded maps, or null with none. */
    fun dataBounds(): BoundingBox? = runCatching { dataStore?.boundingBox() }.getOrNull()

    // ---------- Overlays ----------

    fun setRoute(route: Route?) {
        val pts = route?.points?.map { LatLong(it.lat, it.lon) }.orEmpty()
        routeCasing.latLongs.clear(); routeCasing.latLongs.addAll(pts)
        routeLine.latLongs.clear(); routeLine.latLongs.addAll(pts)
        routeCasing.paintStroke.color = if (night) 0xFF0E0E11.toInt() else 0xFFFFFFFF.toInt()
        routeLine.paintStroke.color = if (night) 0xFFB48CFF.toInt() else 0xFF7A45F0.toInt()
        routeCasing.requestRedraw()
        routeLine.requestRedraw()
    }

    private val transitLayers = mutableListOf<Polyline>()

    /**
     * A public transport journey: each ride in its line's colour (violet when the timetable has none) on a casing,
     * walks as grey dashes. Null clears it.
     */
    fun setTransit(legs: List<TransitLeg>?) {
        val layers = view.layerManager.layers
        transitLayers.forEach { layers.remove(it) }
        transitLayers.clear()
        val density = context.resources.displayMetrics.density
        var index = layers.indexOf(overlay).coerceAtLeast(1)
        for (leg in legs.orEmpty()) {
            val pts = leg.geometry.map { LatLong(it.lat, it.lon) }
            if (pts.size < 2) continue
            val parts = if (leg.kind.isTransit) {
                val casing = line(if (night) 0xFF0E0E11.toInt() else 0xFFFFFFFF.toInt(), 9f)
                val color = leg.color ?: if (night) 0xFFB48CFF.toInt() else 0xFF7A45F0.toInt()
                listOf(casing, line(color, 6f))
            } else {
                val walk = line(if (night) 0xFFB9B9C2.toInt() else 0xFF5C5C66.toInt(), 4f)
                walk.paintStroke.setDashPathEffect(floatArrayOf(2f * density, 7f * density))
                listOf(walk)
            }
            for (p in parts) {
                p.latLongs.addAll(pts)
                transitLayers += p
                layers.add(index++, p)
            }
        }
        view.layerManager.redrawLayers()
    }

    fun setTracks(tracks: List<SavedTrack>) {
        val layers = view.layerManager.layers
        trackLayers.forEach { layers.remove(it) }
        trackLayers.clear()
        val index = layers.indexOf(routeCasing).coerceAtLeast(1)
        for (t in tracks.filter { it.visible }) for (seg in t.segments) {
            val l = line(if (night) 0xCC9A7BD6.toInt() else 0xCC6A3BDB.toInt(), 3.5f)
            l.latLongs.addAll(seg.map { LatLong(it.lat, it.lon) })
            trackLayers += l
            layers.add(index, l)
        }
        view.layerManager.redrawLayers()
    }

    fun setRecording(points: List<LatLon>) {
        recording.latLongs.clear()
        recording.latLongs.addAll(points.map { LatLong(it.lat, it.lon) })
        recording.requestRedraw()
    }

    fun setSaved(places: List<SavedPlace>) = overlay.update { it.copy(saved = places.map { p -> p.point }) }
    fun setSelected(p: LatLon?) = overlay.update { it.copy(selected = p) }
    fun setResults(points: List<LatLon>) = overlay.update { it.copy(results = points) }
    fun setFix(fix: Fix?, heading: Float?) = overlay.update { it.copy(fix = fix, heading = heading) }
    fun setRouteEnds(start: LatLon?, end: LatLon?) = overlay.update { it.copy(routeStart = start, routeEnd = end) }
    fun setNightOverlay(isNight: Boolean) = overlay.update { it.copy(night = isNight) }

    /** Saved place markers close to a screen point, for taps. */
    fun savedNear(x: Float, y: Float, places: List<SavedPlace>): SavedPlace? {
        val proj = view.mapViewProjection
        val radius = 24 * context.resources.displayMetrics.density
        return places.minByOrNull { p ->
            val pt = proj.toPixels(LatLong(p.point.lat, p.point.lon)) ?: return@minByOrNull Double.MAX_VALUE
            Math.hypot(pt.x - x, pt.y - y)
        }?.takeIf { p ->
            val pt = proj.toPixels(LatLong(p.point.lat, p.point.lon)) ?: return@takeIf false
            Math.hypot(pt.x - x, pt.y - y) < radius
        }
    }

    // ---------- Camera ----------

    /**
     * Centres the map on [p]. With [lift] (a fraction of the view's height) the point sits that much above the
     * middle, so a sheet at the bottom doesn't cover it.
     */
    fun moveTo(target: LatLon, zoom: Int? = null, animate: Boolean = true, lift: Double = 0.0) {
        val pos = view.model.mapViewPosition
        val p = if (lift == 0.0) target else {
            val z = zoom ?: pos.zoomLevel.toInt()
            val mpp = Math.cos(Math.toRadians(target.lat)) * 2 * Math.PI * 6378137.0 / (view.model.displayModel.tileSize * Math.pow(2.0, z.toDouble()))
            val h = view.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
            LatLon(target.lat - lift * h * mpp / 111_320.0, target.lon)
        }
        if (zoom != null && zoom.toByte() != pos.zoomLevel) {
            pos.mapPosition = MapPosition(LatLong(p.lat, p.lon), zoom.toByte())
        } else if (animate) {
            pos.animateTo(LatLong(p.lat, p.lon))
        } else {
            pos.center = LatLong(p.lat, p.lon)
        }
    }

    fun zoomIn() = view.model.mapViewPosition.zoomIn()
    fun zoomOut() = view.model.mapViewPosition.zoomOut()

    /** Shows the whole box (a route, search results), leaving room for panels at the top and bottom. */
    fun fit(points: List<LatLon>, maxZoom: Int = 17, top: Double = 0.25, bottom: Double = 0.25) {
        if (points.isEmpty()) return
        if (points.size == 1) { moveTo(points[0], maxZoom); return }
        val box = BoundingBox(points.minOf { it.lat }, points.minOf { it.lon }, points.maxOf { it.lat }, points.maxOf { it.lon })
        val w = view.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val h = view.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        // Use the part of the height between the panels at the top ([top]) and bottom ([bottom]).
        val free = (1.0 - top - bottom).coerceIn(0.15, 1.0)
        val dim = org.mapsforge.core.model.Dimension((w * 0.8).toInt(), (h * free * 0.9).toInt())
        val z = LatLongUtils.zoomForBounds(dim, box, view.model.displayModel.tileSize).coerceIn(2.toByte(), maxZoom.toByte())
        val c = box.centerPoint
        moveTo(LatLon(c.latitude, c.longitude), z.toInt(), animate = false, lift = (bottom - top) / 2)
    }

    /** Turns the map so [heading] (degrees) points up; 0 is north up. */
    fun rotate(heading: Float) {
        val w = view.width / 2f
        val h = view.height / 2f
        if (w <= 0f) return
        view.rotate(Rotation(-heading, w, h))
        view.layerManager.redrawLayers()
        publishCamera()
    }

    fun destroy() {
        runCatching { hills?.interruptAndDestroy() }
        runCatching { view.destroyAll() }
        dataStore?.close()
        AndroidGraphicFactory.clearResourceMemoryCache()
    }

    /** Catches taps and long presses that no other layer took. */
    private inner class TapLayer : Layer() {
        override fun draw(boundingBox: BoundingBox?, zoomLevel: Byte, canvas: org.mapsforge.core.graphics.Canvas?, topLeftPoint: org.mapsforge.core.model.Point?, rotation: Rotation?) {}

        override fun onTap(tapLatLong: LatLong, layerXY: org.mapsforge.core.model.Point?, tapXY: org.mapsforge.core.model.Point): Boolean {
            onTap(LatLon(tapLatLong.latitude, tapLatLong.longitude), tapXY.x.toFloat(), tapXY.y.toFloat())
            return true
        }

        override fun onLongPress(tapLatLong: LatLong, layerXY: org.mapsforge.core.model.Point?, tapXY: org.mapsforge.core.model.Point?): Boolean {
            onLongPress(LatLon(tapLatLong.latitude, tapLatLong.longitude))
            return true
        }
    }
}
