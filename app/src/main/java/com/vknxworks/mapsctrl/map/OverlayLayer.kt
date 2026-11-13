package com.vknxworks.mapsctrl.map

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import com.vknxworks.mapsctrl.geo.LatLon
import com.vknxworks.mapsctrl.location.Fix
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer

/** What the overlay draws. */
data class OverlayState(
    val saved: List<LatLon> = emptyList(),
    val results: List<LatLon> = emptyList(),
    val selected: LatLon? = null,
    val routeStart: LatLon? = null,
    val routeEnd: LatLon? = null,
    val fix: Fix? = null,
    /** Direction you're facing (compass or travel), when known. */
    val heading: Float? = null,
    val night: Boolean = false,
)

/**
 * MapsCtrl's markers, drawn straight onto the map canvas: saved places (small violet-edged dots), search
 * results (grey dots), the route's start and end, the selected place (violet pin) and your position (violet dot
 * with a white ring, its accuracy circle and a heading wedge).
 */
class OverlayLayer(context: Context) : Layer() {
    @Volatile private var state = OverlayState()
    private val density = context.resources.displayMetrics.density

    private val violet = 0xFF8F5CFF.toInt()
    private val violetLight = 0xFFB48CFF.toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    fun update(transform: (OverlayState) -> OverlayState) {
        state = transform(state)
        requestRedraw()
    }

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val c = AndroidGraphicFactory.getCanvas(canvas)
        val s = state
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        fun px(p: LatLon): Pair<Float, Float> = Pair(
            (MercatorProjection.longitudeToPixelX(p.lon, mapSize) - topLeftPoint.x).toFloat(),
            (MercatorProjection.latitudeToPixelY(p.lat, mapSize) - topLeftPoint.y).toFloat(),
        )
        val ring = if (s.night) 0xFF141417.toInt() else 0xFFFFFFFF.toInt()

        // Search results: grey dots.
        for (p in s.results) {
            val (x, y) = px(p)
            fill.color = ring; c.drawCircle(x, y, 6.5f * density, fill)
            fill.color = if (s.night) 0xFF9A9AA4.toInt() else 0xFF5C5C64.toInt(); c.drawCircle(x, y, 4.5f * density, fill)
        }
        // Saved places: violet-edged dots.
        for (p in s.saved) {
            val (x, y) = px(p)
            fill.color = ring; c.drawCircle(x, y, 7f * density, fill)
            stroke.color = violet; stroke.strokeWidth = 2.5f * density; c.drawCircle(x, y, 5f * density, stroke)
        }
        // Route start: a hollow dot.
        s.routeStart?.let { p ->
            val (x, y) = px(p)
            fill.color = ring; c.drawCircle(x, y, 8f * density, fill)
            stroke.color = violet; stroke.strokeWidth = 3f * density; c.drawCircle(x, y, 5f * density, stroke)
        }
        // Route end and the selected place: a pin.
        (s.selected ?: s.routeEnd)?.let { p -> drawPin(c, px(p), ring) }
        if (s.selected != null && s.routeEnd != null && s.routeEnd != s.selected) drawPin(c, px(s.routeEnd), ring)

        // Your position.
        s.fix?.let { f ->
            val (x, y) = px(f.point)
            val acc = MercatorProjection.metersToPixels(f.accuracy, f.point.lat, mapSize).toFloat()
            if (acc > 12 * density) {
                fill.color = 0x228F5CFF; c.drawCircle(x, y, acc, fill)
                stroke.color = 0x558F5CFF; stroke.strokeWidth = 1f * density; c.drawCircle(x, y, acc, stroke)
            }
            s.heading?.let { h ->
                // The wedge points where you're heading; the canvas is already turned with the map.
                c.save()
                c.rotate(h, x, y)
                path.reset()
                path.moveTo(x, y - 22 * density)
                path.lineTo(x - 9 * density, y - 4 * density)
                path.lineTo(x + 9 * density, y - 4 * density)
                path.close()
                fill.color = violetLight
                c.drawPath(path, fill)
                c.restore()
            }
            fill.color = ring; c.drawCircle(x, y, 10f * density, fill)
            fill.color = violet; c.drawCircle(x, y, 7f * density, fill)
        }
    }

    private fun drawPin(c: android.graphics.Canvas, at: Pair<Float, Float>, ring: Int) {
        val (x, y) = at
        val r = 11f * density
        path.reset()
        path.moveTo(x, y)
        path.cubicTo(x - r * 0.5f, y - r * 1.1f, x - r, y - r * 1.4f, x - r, y - r * 2.1f)
        path.arcTo(x - r, y - r * 3.1f, x + r, y - r * 1.1f, 180f, 180f, false)
        path.cubicTo(x + r, y - r * 1.4f, x + r * 0.5f, y - r * 1.1f, x, y)
        path.close()
        stroke.color = ring; stroke.strokeWidth = 3f * density
        c.drawPath(path, stroke)
        fill.color = violet
        c.drawPath(path, fill)
        fill.color = ring
        c.drawCircle(x, y - r * 2.1f, r * 0.38f, fill)
    }
}
