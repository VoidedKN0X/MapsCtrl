package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.data.Http
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeTest {
    @Test
    fun strongEtagFirstThenLastModified() {
        assertEquals("\"abc-123\"", Http.Resume.validator("\"abc-123\"", "Tue, 01 Oct 2026 10:00:00 GMT"))
        // A weak ETag can't be used with If-Range.
        assertEquals("Tue, 01 Oct 2026 10:00:00 GMT", Http.Resume.validator("W/\"abc\"", "Tue, 01 Oct 2026 10:00:00 GMT"))
        assertEquals("Tue, 01 Oct 2026 10:00:00 GMT", Http.Resume.validator(null, " Tue, 01 Oct 2026 10:00:00 GMT "))
        assertNull(Http.Resume.validator(null, null))
        assertNull(Http.Resume.validator("W/\"x\"", ""))
        // Nothing that could break a header.
        assertNull(Http.Resume.validator("\"a\nb\"", null))
    }

    @Test
    fun contentRangeMustStartWhereThePartialFileEnds() {
        assertTrue(Http.Resume.rangeStartsAt("bytes 1000-1999/2000", 1000))
        assertTrue(Http.Resume.rangeStartsAt("bytes 1000-1999/*", 1000))
        assertFalse(Http.Resume.rangeStartsAt("bytes 0-1999/2000", 1000))
        assertFalse(Http.Resume.rangeStartsAt(null, 1000))
        assertFalse(Http.Resume.rangeStartsAt("garbage", 1000))
    }
}
