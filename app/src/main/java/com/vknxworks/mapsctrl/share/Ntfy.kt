package com.vknxworks.mapsctrl.share

import com.vknxworks.mapsctrl.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * ETA sharing to an ntfy server you choose (your own, or ntfy.sh): a short text message to a topic, which anyone
 * subscribed to that topic sees. Off by default; nothing is sent unless you set a server and topic and turn it on.
 */
object Ntfy {

    /** "https://ntfy.example.org" + "my-topic" -> "https://ntfy.example.org/my-topic", or null when unusable. */
    fun topicUrl(server: String, topic: String): String? {
        val s = server.trim().trimEnd('/')
        val t = topic.trim().trim('/')
        if (!s.startsWith("https://") || s.length < 10 || t.isEmpty()) return null
        if (!t.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return null
        return "$s/" + URLEncoder.encode(t, "UTF-8")
    }

    /** Sends one message. Returns true when the server accepted it. */
    suspend fun send(server: String, topic: String, title: String, message: String): Boolean = withContext(Dispatchers.IO) {
        val url = topicUrl(server, topic) ?: return@withContext false
        runCatching {
            Http.postText(url, message, mapOf("Title" to title.replace(Regex("[\\r\\n]"), " "), "Tags" to "round_pushpin")) in 200..299
        }.getOrDefault(false)
    }
}
