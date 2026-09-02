package app.atzor.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the "always safe" mismatch: AppsScreen shows
 * navigation/messaging as apps that stay open, so enforcement must actually
 * exempt them by default in allowlist mode instead of silently blocking them.
 */
class SafetyTierTest {

    private val hardExempt = SafetyPackages.hardBlockExempt
    private val defaultAllowed = SafetyPackages.defaultAllowed

    @Test
    fun allowlistMode_navigationAppStaysOpenByDefault() {
        val state = AtzorState(allowlistMode = true, allowedApps = emptySet())
        assertFalse(
            state.isEffectivelyBlocked(
                "com.google.android.apps.maps",
                launchable = true,
                hardExempt = hardExempt,
                defaultAllowed = defaultAllowed,
            ),
        )
    }

    @Test
    fun allowlistMode_messagingAppStaysOpenByDefault() {
        val state = AtzorState(allowlistMode = true, allowedApps = emptySet())
        assertFalse(
            state.isEffectivelyBlocked(
                "com.google.android.apps.messaging",
                launchable = true,
                hardExempt = hardExempt,
                defaultAllowed = defaultAllowed,
            ),
        )
    }

    @Test
    fun allowlistMode_defaultAllowedAppCanBeExplicitlyOverridden() {
        val state = AtzorState(allowlistMode = true, safetyOverridesBlocked = setOf("com.waze"))
        assertTrue(
            state.isEffectivelyBlocked(
                "com.waze",
                launchable = true,
                hardExempt = hardExempt,
                defaultAllowed = defaultAllowed,
            ),
        )
    }

    @Test
    fun hardTierPackage_cannotBeOverridden() {
        // Overrides only apply to DEFAULT_ALLOWED packages; a HARD package is
        // never in defaultAllowed, so listing it here has no effect.
        val state = AtzorState(allowlistMode = true, safetyOverridesBlocked = setOf("com.android.dialer"))
        assertFalse(
            state.isEffectivelyBlocked(
                "com.android.dialer",
                launchable = true,
                hardExempt = hardExempt,
                defaultAllowed = defaultAllowed,
            ),
        )
    }

    @Test
    fun blocklistMode_unrelatedAppFollowsBlockedApps() {
        val state = AtzorState(allowlistMode = false, blockedApps = setOf("com.instagram.android"))
        assertTrue(
            state.isEffectivelyBlocked(
                "com.instagram.android",
                launchable = true,
                hardExempt = hardExempt,
                defaultAllowed = defaultAllowed,
            ),
        )
    }

    @Test
    fun dynamicallyResolvedDialer_isExemptEvenWithoutStaticEntry() {
        // Simulates an OEM dialer not in the static SafetyPackages list, as
        // BlockerService would pass it in after a TelecomManager lookup.
        val state = AtzorState(allowlistMode = true)
        assertFalse(
            state.isEffectivelyBlocked(
                "com.oem.unknown.dialer",
                launchable = true,
                hardExempt = hardExempt + "com.oem.unknown.dialer",
                defaultAllowed = defaultAllowed,
            ),
        )
    }
}
