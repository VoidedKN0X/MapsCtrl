package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.share.Ntfy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyTest {
    @Test
    fun titleGoesInTheQueryAsUtf8() {
        assertEquals(
            "https://ntfy.example.org/eta?title=Li%C3%A8ge%20%E2%80%93%20Caf%C3%A9",
            Ntfy.messageUrl("https://ntfy.example.org/", "eta", "Liège – Café"),
        )
        // Only ASCII is left in the URL, so the HTTP client can't refuse it.
        val url = Ntfy.messageUrl("https://ntfy.example.org", "eta", "Dübendorf 🚗 &x=1\nnext")!!
        assert(url.all { it.code < 128 })
        assertEquals("https://ntfy.example.org/eta?title=D%C3%BCbendorf%20%F0%9F%9A%97%20%26x%3D1%20next", url)
    }

    @Test
    fun emptyTitleOrUnusableServer() {
        assertEquals("https://ntfy.example.org/eta", Ntfy.messageUrl("https://ntfy.example.org", "eta", "  "))
        assertNull(Ntfy.messageUrl("http://ntfy.example.org", "eta", "x"))
        assertNull(Ntfy.messageUrl("https://ntfy.example.org", "bad topic", "x"))
    }
}
