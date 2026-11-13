package com.vknxworks.mapsctrl.saved

import com.vknxworks.mapsctrl.geo.LatLon
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.io.Writer
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

data class GpxWaypoint(val point: LatLon, val name: String, val description: String? = null, val time: Instant? = null)

data class GpxTrack(val name: String, val segments: List<List<LatLon>>, val times: List<List<Instant?>> = emptyList())

data class GpxData(val waypoints: List<GpxWaypoint>, val tracks: List<GpxTrack>)

/** GPX 1.1 (and 1.0) files: waypoints become saved places, tracks and routes become saved tracks. */
object Gpx {

    fun read(input: InputStream): GpxData {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        f.isExpandEntityReferences = false
        // A GPX file is untrusted: no DTDs, no external entities.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { f.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        val doc = f.newDocumentBuilder().parse(input)
        val root = doc.documentElement

        val waypoints = children(root, "wpt").mapNotNull { e ->
            val p = point(e) ?: return@mapNotNull null
            GpxWaypoint(p, text(e, "name") ?: p.format(), text(e, "desc") ?: text(e, "cmt"), time(e))
        }
        val tracks = mutableListOf<GpxTrack>()
        children(root, "trk").forEachIndexed { i, trk ->
            val segs = children(trk, "trkseg").map { seg -> children(seg, "trkpt").mapNotNull { point(it) to time(it) } }
            val points = segs.map { s -> s.mapNotNull { it.first } }.filter { it.isNotEmpty() }
            val times = segs.map { s -> s.filter { it.first != null }.map { it.second } }.filter { it.isNotEmpty() }
            if (points.isNotEmpty()) tracks += GpxTrack(text(trk, "name") ?: "Track ${i + 1}", points, times)
        }
        children(root, "rte").forEachIndexed { i, rte ->
            val pts = children(rte, "rtept").mapNotNull { point(it) }
            if (pts.isNotEmpty()) tracks += GpxTrack(text(rte, "name") ?: "Route ${i + 1}", listOf(pts))
        }
        return GpxData(waypoints, tracks)
    }

    fun write(out: Writer, data: GpxData, creator: String) {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.write("<gpx version=\"1.1\" creator=\"${esc(creator)}\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        for (w in data.waypoints) {
            out.write("  <wpt lat=\"${coord(w.point.lat)}\" lon=\"${coord(w.point.lon)}\">\n")
            w.time?.let { out.write("    <time>$it</time>\n") }
            out.write("    <name>${esc(w.name)}</name>\n")
            w.description?.takeIf { it.isNotBlank() }?.let { out.write("    <desc>${esc(it)}</desc>\n") }
            out.write("  </wpt>\n")
        }
        for (t in data.tracks) {
            out.write("  <trk>\n    <name>${esc(t.name)}</name>\n")
            t.segments.forEachIndexed { si, seg ->
                out.write("    <trkseg>\n")
                seg.forEachIndexed { pi, p ->
                    val time = t.times.getOrNull(si)?.getOrNull(pi)
                    if (time != null) {
                        out.write("      <trkpt lat=\"${coord(p.lat)}\" lon=\"${coord(p.lon)}\"><time>$time</time></trkpt>\n")
                    } else {
                        out.write("      <trkpt lat=\"${coord(p.lat)}\" lon=\"${coord(p.lon)}\"/>\n")
                    }
                }
                out.write("    </trkseg>\n")
            }
            out.write("  </trk>\n")
        }
        out.write("</gpx>\n")
        out.flush()
    }

    private fun coord(v: Double) = String.format(java.util.Locale.ROOT, "%.7f", v).trimEnd('0').trimEnd('.')

    private fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code < 0x20 && c != '\n' && c != '\t') append(' ') else append(c)
        }
    }

    /** Element children by local name, ignoring any namespace prefix. */
    private fun children(e: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = e.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && n.nodeName.substringAfter(':') == name) out += n as Element
        }
        return out
    }

    private fun text(e: Element, name: String): String? = children(e, name).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun point(e: Element): LatLon? {
        val lat = e.getAttribute("lat").trim().toDoubleOrNull() ?: return null
        val lon = e.getAttribute("lon").trim().toDoubleOrNull() ?: return null
        return LatLon(lat, lon).takeIf { it.isValid() }
    }

    private fun time(e: Element): Instant? = text(e, "time")?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
