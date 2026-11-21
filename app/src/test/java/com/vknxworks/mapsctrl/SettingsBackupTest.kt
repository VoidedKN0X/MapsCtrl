package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.Units
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.settings.BackupFormatException
import com.vknxworks.mapsctrl.settings.SettingsBackups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    private val mine = AppSettings(
        units = Units.IMPERIAL, travelMode = TravelMode.MOPED, ntfyEnabled = true, ntfyServer = "https://ntfy.example.org",
        ntfyTopic = "secret-topic", lastLat = 50.9, lastLon = 3.1, avoidFerries = true, overSpeedAlert = true, overSpeedMargin = 3,
    )

    @Test
    fun roundTripWithoutTopicOrPosition() {
        val text = SettingsBackups.export(mine, "1.0", "2026-11-21T20:00:00Z")
        assertFalse(text.contains("secret-topic"))
        assertFalse(text.contains("50.9"))
        val restored = SettingsBackups.apply(SettingsBackups.parse(text), AppSettings(ntfyTopic = "other"))
        assertEquals(Units.IMPERIAL, restored.units)
        assertEquals(TravelMode.MOPED, restored.travelMode)
        assertEquals("other", restored.ntfyTopic)
        assertTrue(restored.ntfyEnabled)
        assertTrue(restored.avoidFerries)
        assertEquals(3, restored.overSpeedMargin)
        assertEquals(AppSettings().lastLat, restored.lastLat, 0.0)
    }

    @Test
    fun importNeverLowersPrivacyOrEnablesSharingWithoutTopic() {
        val text = SettingsBackups.export(mine.copy(hideScreenshots = false), "1.0", "x")
        val restored = SettingsBackups.apply(SettingsBackups.parse(text), AppSettings(hideScreenshots = true))
        assertTrue(restored.hideScreenshots)
        // No topic on this phone: ETA sharing stays off.
        assertFalse(restored.ntfyEnabled)
    }

    @Test
    fun changesListsOnlyWhatDiffers() {
        val b = SettingsBackups.parse(SettingsBackups.export(AppSettings(units = Units.IMPERIAL), "1.0", "x"))
        assertEquals(listOf("units"), SettingsBackups.changes(b, AppSettings()))
    }

    @Test
    fun rejectsOtherFiles() {
        for (bad in listOf("", "{}", "{}x", """{"app":"PrintCtrl","format":1}""")) {
            try { SettingsBackups.parse(bad); throw AssertionError("accepted $bad") } catch (e: BackupFormatException) { assertFalse(e.newer) }
        }
        try { SettingsBackups.parse("""{"app":"MapsCtrl","format":9}"""); throw AssertionError("accepted a newer file") } catch (e: BackupFormatException) { assertTrue(e.newer) }
    }

    @Test
    fun badValuesAreIgnored() {
        val b = SettingsBackups.parse("""{"app":"MapsCtrl","format":1,"settings":{"units":"PARSECS","overSpeedMargin":99,"ntfyServer":"http://plain.example"}}""")
        assertEquals(AppSettings(), SettingsBackups.apply(b, AppSettings()))
    }
}
