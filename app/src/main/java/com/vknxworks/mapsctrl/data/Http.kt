package com.vknxworks.mapsctrl.data

import com.vknxworks.mapsctrl.BuildConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/**
 * The only network code in MapsCtrl: plain HTTPS GETs for downloads you start, transit routes you ask for
 * (Transitous), and the optional ntfy POST. No cookies, no tracking headers; the User-Agent names only the app
 * and its version (plus the project's URL for Transitous, which asks API users for a contact).
 */
object Http {
    private val userAgent = "MapsCtrl/${BuildConfig.VERSION_NAME}"

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "HTTPS only" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.useCaches = false
        c.setRequestProperty("User-Agent", userAgent)
        return c
    }

    /**
     * A GET for an API answer (Transitous): JSON only, [userAgent] as the services ask for, short timeouts, the
     * whole request bounded by [deadlineMs], and at most [maxBytes] read; anything larger is refused.
     */
    fun getJsonCapped(url: String, userAgent: String, maxBytes: Int, deadlineMs: Long = 30_000): String {
        val start = System.nanoTime()
        val c = open(url)
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            val declared = c.contentLengthLong
            if (declared > maxBytes) throw IOException("Response too large")
            val out = java.io.ByteArrayOutputStream(if (declared in 1..maxBytes) declared.toInt() else 64 * 1024)
            c.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    if ((System.nanoTime() - start) / 1_000_000 > deadlineMs) throw IOException("Timed out")
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw IOException("Response too large")
                    out.write(buf, 0, n)
                }
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            c.disconnect()
        }
    }

    /** An HTTP error status from an API. */
    class HttpStatusException(val code: Int) : IOException("HTTP $code")

    fun getText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** A HEAD request: the status and the file's size (-1 when the server doesn't say). */
    fun head(url: String): Pair<Int, Long> {
        val c = open(url)
        c.requestMethod = "HEAD"
        try {
            return c.responseCode to c.contentLengthLong
        } finally {
            c.disconnect()
        }
    }

    /** Size of a file on the server, from a HEAD request. */
    fun contentLength(url: String): Long? {
        val c = open(url)
        c.requestMethod = "HEAD"
        try {
            if (c.responseCode !in 200..299) return null
            return c.contentLengthLong.takeIf { it >= 0 }
        } finally {
            c.disconnect()
        }
    }

    /**
     * Downloads [url] to [dest]: into "<dest>.part" first (continuing a partial file when the server allows it),
     * renamed to [dest] only when complete. [progress] gets (bytes so far, total or -1).
     *
     * A partial file is only continued with `If-Range` and the version it started from (its ETag or
     * Last-Modified, kept in "<dest>.part.v"): when the server has a newer file by then, it answers with the whole
     * new file and the download starts over, instead of sending the new file's tail after the old file's start.
     * A partial file without a saved version is started over.
     */
    fun download(url: String, dest: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit) =
        download(url, dest, cancelled, progress, retried = false)

    private fun download(url: String, dest: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit, retried: Boolean) {
        dest.parentFile?.mkdirs()
        val part = partFile(dest)
        val meta = versionFile(dest)
        var have = if (part.exists()) part.length() else 0L
        val validator = if (have > 0) runCatching { meta.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } else null
        if (have > 0 && validator == null) {
            // Unknown version: never joined to anything.
            part.delete()
            have = 0L
        }
        val c = open(url)
        if (have > 0 && validator != null) {
            c.setRequestProperty("Range", "bytes=$have-")
            c.setRequestProperty("If-Range", validator)
        }
        try {
            val code = c.responseCode
            if (code == 416 || (code == 206 && !Resume.rangeStartsAt(c.getHeaderField("Content-Range"), have))) {
                // The partial file is already complete, the file changed, or the server sent another range: start over.
                discardPartial(dest)
                c.disconnect()
                if (retried) throw IOException("HTTP $code")
                return download(url, dest, cancelled, progress, retried = true)
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            if (code != 206) {
                // The whole file (new, changed, or the server ignored the range): remember which version it is.
                have = 0L
                val v = Resume.validator(c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"))
                if (v != null) meta.writeText(v) else meta.delete()
            }
            val total = c.contentLengthLong.let { if (it >= 0) it + have else -1L }
            RandomAccessFile(part, "rw").use { raf ->
                raf.setLength(have)
                raf.seek(have)
                c.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var done = have
                    var lastReport = 0L
                    while (true) {
                        if (cancelled()) throw CancellationException()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastReport > 150_000_000L) {
                            progress(done, total)
                            lastReport = now
                        }
                    }
                    if (total > 0 && done != total) throw IOException("Incomplete download")
                    progress(done, total)
                }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw IOException("Couldn't save ${dest.name}")
            meta.delete()
        } finally {
            c.disconnect()
        }
    }

    /** The partial file of a download to [dest]. */
    fun partFile(dest: File) = File(dest.path + ".part")

    /** Which version of the file the partial file is (see [download]). */
    private fun versionFile(dest: File) = File(dest.path + ".part.v")

    /** Deletes a download's partial file and its saved version. */
    fun discardPartial(dest: File) {
        partFile(dest).delete()
        versionFile(dest).delete()
    }

    /** A leftover of an unfinished download ("<file>.part" or "<file>.part.v"). */
    fun isPartial(f: File) = f.name.endsWith(".part") || f.name.endsWith(".part.v")

    /** Pure helpers for resuming downloads; unit-tested. */
    object Resume {
        /**
         * What to send as `If-Range` later: a strong ETag, else the Last-Modified date. A weak ETag (`W/"…"`)
         * can't be used for ranges, and neither can nothing at all (then a partial file is never continued).
         */
        fun validator(etag: String?, lastModified: String?): String? {
            val e = etag?.trim()
            if (!e.isNullOrEmpty() && !e.startsWith("W/") && e.length <= 200 && e.all { it.code in 0x21..0x7e }) return e
            val m = lastModified?.trim()
            if (!m.isNullOrEmpty() && m.length <= 100 && m.all { it.code in 0x20..0x7e }) return m
            return null
        }

        /** A 206 answer's `Content-Range` ("bytes 1000-1999/2000") starts where the partial file ends. */
        fun rangeStartsAt(contentRange: String?, have: Long): Boolean {
            val m = Regex("""^\s*bytes\s+(\d+)-\d+/(\d+|\*)\s*$""").find(contentRange ?: return false) ?: return false
            return m.groupValues[1].toLongOrNull() == have
        }
    }

    /** POSTs a short text message (ntfy). Returns the HTTP status. */
    fun postText(url: String, body: String, headers: Map<String, String>): Int {
        val c = open(url)
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return c.responseCode
        } finally {
            c.disconnect()
        }
    }
}
