package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.data.DemTiles
import com.vknxworks.mapsctrl.data.GeoBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

class DemTilesTest {

    @Test
    fun names() {
        assertEquals("N43E007", DemTiles.name(43, 7))
        assertEquals("S34W071", DemTiles.name(-34, -71))
        assertEquals("N00W001", DemTiles.name(0, -1))
        assertTrue(DemTiles.isName("N43E007"))
        assertFalse(DemTiles.isName("../N43E007"))
        assertFalse(DemTiles.isName("N43E07"))
        assertEquals("https://elevation-tiles-prod.s3.amazonaws.com/skadi/N43/N43E007.hgt.gz", DemTiles.url("N43E007"))
        assertEquals("https://elevation-tiles-prod.s3.amazonaws.com/skadi/S34/S34W071.hgt.gz", DemTiles.url("S34W071"))
    }

    @Test
    fun tilesForAnArea() {
        // Monaco's map: one tile.
        assertEquals(listOf("N43E007"), DemTiles.tilesFor(GeoBox(43.72, 7.40, 43.76, 7.45)))
        // Across the equator and the prime meridian.
        assertEquals(listOf("S01W001", "S01E000", "N00W001", "N00E000"), DemTiles.tilesFor(GeoBox(-0.5, -0.5, 0.5, 0.5)))
        // A whole continent stops at the cap (+1, so the caller can tell it's too big).
        assertEquals(DemTiles.MAX_TILES_PER_REGION + 1, DemTiles.tilesFor(GeoBox(35.0, -10.0, 70.0, 40.0)).size)
    }

    private fun gz(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(bytes) } }.toByteArray()

    @Test
    fun convertsToTheZipMapsforgeReads() {
        val dest = File.createTempFile("dem", ".zip")
        try {
            val size = 1201 * 1201 * 2
            assertEquals(size.toLong(), DemTiles.gzToZip("N43E007", ByteArrayInputStream(gz(ByteArray(size))), dest))
            ZipFile(dest).use { z ->
                val e = z.getEntry("N43E007.hgt")
                assertEquals(size.toLong(), e.size)
            }
        } finally {
            dest.delete()
        }
    }

    @Test
    fun refusesWhatIsNotATile() {
        val dest = File.createTempFile("dem", ".zip")
        for (bytes in listOf(gz(ByteArray(1000)), "not gzip".toByteArray(), gz(ByteArray(3601 * 3601 * 2 + 2)))) {
            try {
                DemTiles.gzToZip("N43E007", ByteArrayInputStream(bytes), dest)
                fail()
            } catch (_: IOException) {
            }
            assertFalse(dest.exists())
        }
    }
}
