package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.routing.TravelMode
import com.vknxworks.mapsctrl.routing.Units
import com.vknxworks.mapsctrl.settings.AppSettings
import com.vknxworks.mapsctrl.settings.BackupFormatException
import com.vknxworks.mapsctrl.settings.LockTimeout
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
        assertEquals("https://ntfy.example.org", restored.ntfyServer)
        // A file never turns ETA sharing on; and here the server changed too.
        assertFalse(restored.ntfyEnabled)
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

    @Test
    fun differentServerTurnsSharingOffAndIsListed() {
        val here = mine
        val b = SettingsBackups.parse("""{"app":"MapsCtrl","format":1,"settings":{"ntfyServer":"https://evil.example","ntfyEnabled":true}}""")
        val next = SettingsBackups.apply(b, here)
        assertEquals("https://evil.example", next.ntfyServer)
        assertFalse(next.ntfyEnabled)
        assertEquals("secret-topic", next.ntfyTopic)
        assertEquals(listOf("ntfy_server"), SettingsBackups.changes(b, here))
    }

    @Test
    fun sameServerKeepsSharingOnButAFileCanNeverTurnItOn() {
        val same = SettingsBackups.parse("""{"app":"MapsCtrl","format":1,"settings":{"ntfyServer":"https://ntfy.example.org","ntfyEnabled":true}}""")
        assertTrue(SettingsBackups.apply(same, mine).ntfyEnabled)
        assertFalse(SettingsBackups.apply(same, mine.copy(ntfyEnabled = false)).ntfyEnabled)
        val off = SettingsBackups.parse("""{"app":"MapsCtrl","format":1,"settings":{"ntfyEnabled":false}}""")
        assertFalse(SettingsBackups.apply(off, mine).ntfyEnabled)
        assertEquals(listOf("ntfy"), SettingsBackups.changes(off, mine))
    }

    @Test
    fun lockAndPrivacyOnlyGetStricter() {
        val loose = SettingsBackups.parse(
            """{"app":"MapsCtrl","format":1,"settings":{"hideRecents":false,"hideScreenshots":false,"lockTimeout":"RESTART","keepRecentSearches":true}}""",
        )
        val strictHere = AppSettings(hideRecents = true, hideScreenshots = true, lockTimeout = LockTimeout.MINUTE_1, keepRecentSearches = false)
        assertEquals(strictHere, SettingsBackups.apply(loose, strictHere))

        val strict = SettingsBackups.parse(
            """{"app":"MapsCtrl","format":1,"settings":{"hideRecents":true,"lockTimeout":"IMMEDIATELY","keepRecentSearches":false}}""",
        )
        val next = SettingsBackups.apply(strict, AppSettings(lockTimeout = LockTimeout.HOUR_1))
        assertTrue(next.hideRecents)
        assertEquals(LockTimeout.IMMEDIATELY, next.lockTimeout)
        assertFalse(next.keepRecentSearches)
        assertEquals(listOf("hide_recents", "lock_timeout", "recent"), SettingsBackups.changes(strict, AppSettings(lockTimeout = LockTimeout.HOUR_1)))
    }

    @Test
    fun exportHasLockTimeoutButNoPin() {
        val text = SettingsBackups.export(AppSettings(lockTimeout = LockTimeout.MINUTES_5, hideRecents = true), "1.0", "x")
        assertTrue(text.contains("MINUTES_5"))
        assertFalse(text.contains("pin", ignoreCase = true))
    }
}
