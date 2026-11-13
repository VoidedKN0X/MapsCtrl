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
     */
    fun download(url: String, dest: File, cancelled: () -> Boolean, progress: (Long, Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var have = if (part.exists()) part.length() else 0L
        val c = open(url)
        if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = c.responseCode
            if (code == 416) {
                // The partial file is already complete (or the file changed): start over.
                part.delete()
                c.disconnect()
                return download(url, dest, cancelled, progress)
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            if (code == 200) have = 0L // the server ignored the range
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
        } finally {
            c.disconnect()
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
