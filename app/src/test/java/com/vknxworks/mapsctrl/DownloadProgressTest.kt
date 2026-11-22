package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.data.DownloadProgress
import com.vknxworks.mapsctrl.data.DownloadState
import com.vknxworks.mapsctrl.data.TerrainJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun bytesOfEveryDownload() {
        val maps = listOf(DownloadState.Running(50, 100, false), DownloadState.Failed("x"))
        assertEquals(50, DownloadProgress.percent(maps, emptyList(), null))
        assertEquals(25, DownloadProgress.percent(maps, listOf(0L to 100L), null))
        // Sizes not known yet: no percentage.
        assertNull(DownloadProgress.percent(listOf(DownloadState.Running(10, -1, false)), listOf(5L to -1L), null))
    }

    @Test
    fun terrainCountsTilesWhenAlone() {
        assertEquals(30, DownloadProgress.percent(emptyList(), emptyList(), TerrainJob.Running("p", 3, 10, 12345)))
        assertEquals(100, DownloadProgress.percent(emptyList(), listOf(200L to 100L), null))
        assertNull(DownloadProgress.percent(emptyList(), emptyList(), TerrainJob.Running("p", 0, 0, 0)))
    }
}
