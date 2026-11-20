package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.saved.GrowingList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GrowingListTest {
    @Test
    fun snapshotsHoldWhatWasAddedInOrder() {
        val g = GrowingList<Int>()
        assertNull(g.lastOrNull())
        val a = g.add(1)
        val b = g.add(2)
        val c = g.add(3)
        assertEquals(listOf(1), a)
        assertEquals(listOf(1, 2), b)
        assertEquals(listOf(1, 2, 3), c)
        assertEquals(3, g.size)
        assertEquals(3, g.lastOrNull())
    }

    @Test
    fun anEarlierSnapshotNeverChanges() {
        val g = GrowingList<Int>()
        val early = g.add(1)
        repeat(5_000) { g.add(it) }
        assertEquals(1, early.size)
        assertEquals(listOf(1), early.toList())
        assertEquals(1, early.last())
        assertThrows(IndexOutOfBoundsException::class.java) { early[1] }
        assertThrows(IndexOutOfBoundsException::class.java) { early[-1] }
    }

    @Test
    fun equalityMatchesOrdinaryLists() {
        val g = GrowingList<Int>()
        val a = g.add(7)
        val b = g.add(8)
        assertNotEquals(a, b)
        assertEquals(b, listOf(7, 8))
        assertEquals(listOf(7, 8), b)
        assertEquals(listOf(7, 8).hashCode(), b.hashCode())
        assertNotEquals(b, listOf(7, 9))
        // The same contents from another buffer are equal too.
        val other = GrowingList<Int>().also { it.add(7) }.add(8)
        assertEquals(b, other)
        assertNotEquals(emptyList<Int>(), a)
    }

    @Test
    fun worksAsAListForTheMapAndForSaving() {
        val g = GrowingList<Int>()
        var l: List<Int> = emptyList()
        for (i in 0 until 100) l = g.add(i)
        assertEquals((40 until 100).toList(), l.subList(40, l.size).map { it })
        assertEquals((0 until 100).toList(), l.toList())
        assertEquals(99, l.zipWithNext().size)
        assertEquals(99, l.lastOrNull())
        assertEquals(l[39], l.subList(0, 40).last())
    }
}
