package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.search.OpeningHours
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class OpeningHoursTest {
    // 2026-11-23 is a Monday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 23 + day, h, m)
    private fun open(spec: String, day: Int, h: Int, m: Int = 0) = OpeningHours.isOpen(spec, at(day, h, m))

    @Test
    fun simpleWeekdays() {
        val s = "Mo-Fr 08:00-12:00,13:00-17:30; Sa 09:00-12:00"
        assertEquals(true, open(s, 0, 9))
        assertEquals(false, open(s, 0, 12, 30))
        assertEquals(true, open(s, 4, 17, 29))
        assertEquals(false, open(s, 4, 17, 30))
        assertEquals(true, open(s, 5, 11))
        assertEquals(false, open(s, 6, 11))
    }

    @Test
    fun laterRuleReplacesEarlier() {
        val s = "Mo-Sa 10:00-20:00; We off; Sa 10:00-14:00"
        assertEquals(true, open(s, 1, 15))
        assertEquals(false, open(s, 2, 15))
        assertEquals(false, open(s, 5, 15))
        assertEquals(true, open(s, 5, 13))
    }

    @Test
    fun pastMidnight() {
        val s = "Fr-Sa 18:00-02:00"
        assertEquals(true, open(s, 4, 23))
        assertEquals(true, open(s, 5, 1, 30)) // Saturday night, from Friday
        assertEquals(true, open(s, 6, 1, 30)) // Sunday night, from Saturday
        assertEquals(false, open(s, 0, 1, 30))
        assertEquals(true, open("Mo-Su 22:00-26:00", 1, 1))
    }

    @Test
    fun wrapAroundDaysAndAdditionalRules() {
        assertEquals(true, open("Fr-Mo 10:00-12:00", 0, 11))
        assertEquals(false, open("Fr-Mo 10:00-12:00", 2, 11))
        val s = "Mo-Fr 09:00-12:00, We 14:00-18:00"
        assertEquals(true, open(s, 2, 15))
        assertEquals(true, open(s, 2, 10))
        assertEquals(false, open(s, 1, 15))
        assertEquals(true, open("Mo,We 08:00-10:00", 2, 9))
        assertEquals(false, open("Mo,We 08:00-10:00", 1, 9))
        assertEquals(true, open("Mo-Fr 08:00-12:00, 13:00-17:00", 0, 14))
    }

    @Test
    fun holidaysAreSkipped() {
        assertEquals(true, open("Mo-Sa 09:00-18:00; PH off", 0, 10))
        assertEquals(false, open("Mo-Sa 09:00-18:00; Su,PH off", 6, 10))
    }

    @Test
    fun specialForms() {
        assertEquals(true, open("24/7", 3, 3))
        assertEquals(true, open("08:00-20:00", 6, 10))
        assertEquals(true, open("Sa", 5, 3))
        assertEquals(false, open("Sa", 4, 3))
    }

    @Test
    fun unreadableGivesNull() {
        assertEquals(null, open("Jan-Mar Mo-Fr 08:00-12:00", 0, 9))
        assertEquals(null, open("Mo-Fr 08:00+", 0, 9))
        assertEquals(null, open("sunrise-sunset", 0, 9))
        assertEquals(null, open("Mo-Fr 08:00-12:00 || \"by appointment\"", 0, 9))
        assertEquals(null, open("", 0, 9))
        assertEquals(null, OpeningHours.isOpen(null, at(0, 9)))
    }
}
