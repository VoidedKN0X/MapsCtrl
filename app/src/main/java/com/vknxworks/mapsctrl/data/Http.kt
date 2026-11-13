package com.vknxworks.mapsctrl.data

import com.vknxworks.mapsctrl.BuildConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/**
 * The only network code in MapsCtrl: plain HTTPS GETs for downloads you start, and the optional ntfy POST.
 * No cookies, no tracking headers; the User-Agent names only the app and its version.
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

    fun getText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
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
