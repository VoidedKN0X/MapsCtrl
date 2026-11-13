package com.vknxworks.mapsctrl.data

/**
 * One line of a web server's directory listing: a file or a folder.
 * [bytes] is exact when the server lists bytes (nginx) and rounded when it lists "43M" (Apache).
 */
data class IndexEntry(
    val name: String,
    val isDirectory: Boolean,
    val bytes: Long?,
    val bytesExact: Boolean,
    /** "2026-08-21 15:48" or "12-Nov-2026 03:14", as the server shows it. */
    val modified: String?,
)

/**
 * Reads the directory listings of download.mapsforge.org (Apache "Index of" tables) and
 * brouter.de/brouter/segments4 (nginx "Index of" with a <pre> block). Only plain relative links count:
 * no parent folder, sort links or absolute URLs.
 */
object DirectoryIndex {

    private val apacheRow = Regex(
        """<tr>.*?<a href="([^"]+)">[^<]*</a>\s*</td>\s*<td[^>]*>([^<]*)</td>\s*<td[^>]*>([^<]*)</td>""",
        RegexOption.IGNORE_CASE,
    )
    private val nginxRow = Regex("""<a href="([^"]+)">[^<]*</a>\s+(\S+\s+\S+)\s+(\S+)""", RegexOption.IGNORE_CASE)

    fun parse(html: String): List<IndexEntry> {
        val rows = if (html.contains("<pre>", ignoreCase = true) && !html.contains("<table", ignoreCase = true)) {
            nginxRow.findAll(html).map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        } else {
            apacheRow.findAll(html).map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        }
        return rows.mapNotNull { (href, date, size) -> entry(href, date, size) }.toList()
    }

    private fun entry(rawHref: String, date: String, size: String): IndexEntry? {
        val href = rawHref.replace("&amp;", "&")
        if (href.startsWith("?") || href.startsWith("/") || href.startsWith("..") || href.contains("://")) return null
        if (href.contains("..")) return null
        val isDir = href.endsWith("/")
        val name = java.net.URLDecoder.decode(href.trimEnd('/'), "UTF-8")
        if (name.isEmpty() || name.contains('/')) return null
        val (bytes, exact) = parseSize(size.trim())
        return IndexEntry(name, isDir, if (isDir) null else bytes, exact, date.trim().ifEmpty { null })
    }

    /** "43M" -> 43 MiB (rounded), "776K", "1.9G", "12241667" -> exact bytes, "-" -> null. */
    fun parseSize(s: String): Pair<Long?, Boolean> {
        if (s.isEmpty() || s == "-") return null to false
        s.toLongOrNull()?.let { return it to true }
        val m = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*([KMGT])B?$""", RegexOption.IGNORE_CASE).find(s) ?: return null to false
        val n = m.groupValues[1].toDouble()
        val mult = when (m.groupValues[2].uppercase()) {
            "K" -> 1024.0
            "M" -> 1024.0 * 1024
            "G" -> 1024.0 * 1024 * 1024
            else -> 1024.0 * 1024 * 1024 * 1024
        }
        return (n * mult).toLong() to false
    }

    /** "czech-republic" -> "Czech Republic", "ireland-and-northern-ireland" -> "Ireland and Northern Ireland". */
    fun displayName(fileOrFolder: String): String {
        val base = fileOrFolder.substringBeforeLast('.').ifEmpty { fileOrFolder }
        val small = setOf("and", "of", "the", "de", "la", "del", "da", "do", "das", "dos", "di", "du", "y")
        return base.split('-', '_').filter { it.isNotEmpty() }.mapIndexed { i, w ->
            if (i > 0 && w in small) w else w.replaceFirstChar { it.uppercase() }
        }.joinToString(" ")
    }
}
