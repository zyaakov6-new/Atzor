package app.atzor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the report that started this work: strict mode sealed
 * the whole Settings app, so a user could not pair Bluetooth headphones and
 * had no way out. Only the two screens that can actually disable or remove
 * עצור may be guarded, and anything unrecognised must be allowed through.
 */
class SettingsGuardTest {

    @Test
    fun accessibilityScreens_areGuarded() {
        listOf(
            "com.android.settings.Settings\$AccessibilitySettingsActivity",
            "com.android.settings.accessibility.AccessibilitySettings",
            "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment",
            "com.samsung.accessibility.setting.AccessibilitySettings",
            "com.miui.settings.accessibility.AccessibilitySettingsActivity",
        ).forEach {
            assertEquals(it, SettingsGuard.Screen.ACCESSIBILITY, SettingsGuard.classify(it))
            assertTrue(it, SettingsGuard.shouldBounce(it))
        }
    }

    @Test
    fun appInfoScreens_areGuarded() {
        listOf(
            "com.android.settings.Settings\$AppInfoDashboardActivity",
            "com.android.settings.applications.InstalledAppDetailsTop",
            "com.miui.appmanager.ApplicationsDetailsActivity",
        ).forEach {
            assertEquals(it, SettingsGuard.Screen.APP_INFO, SettingsGuard.classify(it))
            assertTrue(it, SettingsGuard.shouldBounce(it))
        }
    }

    @Test
    fun bluetoothAndOtherOrdinaryScreens_areAllowed() {
        // The exact scenario from the bug report, plus its neighbours.
        listOf(
            "com.android.settings.bluetooth.BluetoothSettings",
            "com.android.settings.Settings\$BluetoothSettingsActivity",
            "com.android.settings.wifi.WifiSettings",
            "com.android.settings.Settings\$SoundSettingsActivity",
            "com.android.settings.Settings\$DateTimeSettingsActivity",
            "com.android.settings.fuelgauge.PowerUsageSummary",
        ).forEach {
            assertEquals(it, SettingsGuard.Screen.ALLOW, SettingsGuard.classify(it))
            assertFalse(it, SettingsGuard.shouldBounce(it))
        }
    }

    @Test
    fun genericSubSettingsHost_isAllowed() {
        // SubSettings hosts Bluetooth just as often as anything sensitive, so
        // matching it would recreate the original bug. Fail open instead.
        assertEquals(SettingsGuard.Screen.ALLOW, SettingsGuard.classify("com.android.settings.SubSettings"))
        assertEquals(SettingsGuard.Screen.ALLOW, SettingsGuard.classify("com.android.settings.Settings"))
    }

    @Test
    fun unknownOrMissingClassName_isAllowed() {
        assertEquals(SettingsGuard.Screen.ALLOW, SettingsGuard.classify(null))
        assertEquals(SettingsGuard.Screen.ALLOW, SettingsGuard.classify(""))
        assertEquals(SettingsGuard.Screen.ALLOW, SettingsGuard.classify("   "))
        assertEquals(SettingsGuard.Screen.ALLOW, SettingsGuard.classify("com.example.SomethingWeHaveNeverSeen"))
    }

    @Test
    fun dateTimeIsNoLongerGuarded_becauseTheClockCannotShortenALock() {
        // ClockTamperTest covers why: the lock is measured monotonically.
        assertFalse(SettingsGuard.shouldBounce("com.android.settings.Settings\$DateTimeSettingsActivity"))
    }

    @Test
    fun settingsPackage_isOpenByDefaultSoAllowlistModeDoesNotBlockIt() {
        assertTrue("com.android.settings" in SafetyPackages.defaultAllowed)
        val state = AtzorState(allowlistMode = true, allowedApps = emptySet())
        assertFalse(
            state.isEffectivelyBlocked(
                "com.android.settings",
                launchable = true,
                hardExempt = SafetyPackages.hardBlockExempt,
                defaultAllowed = SafetyPackages.defaultAllowed,
            ),
        )
    }

    @Test
    fun strictModeOff_meansNothingIsGuarded() {
        // The service checks state.strictMode before consulting the matcher at
        // all; this pins the state side of that contract.
        assertFalse(AtzorState().strictMode)
    }
}
