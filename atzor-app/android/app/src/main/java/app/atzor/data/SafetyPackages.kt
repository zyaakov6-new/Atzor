package app.atzor.data

import androidx.annotation.StringRes
import app.atzor.R

/**
 * Packages the blocker treats specially, split into two tiers:
 *  - HARD: never blockable, under any mode or setting, no user override
 *    (phone, emergency, system UI).
 *  - DEFAULT_ALLOWED: open by default even in allowlist mode (navigation,
 *    texting), but the user can flip a per-app override to block one anyway
 *    for a stricter lock. See AtzorState.safetyOverridesBlocked.
 *
 * These static entries cover the AOSP/Google/Samsung/Israeli-market packages
 * we can name directly. BlockerService additionally resolves the device's
 * actual default dialer and default SMS app at runtime (TelecomManager /
 * Telephony), so a phone from an OEM not listed here is still covered
 * correctly instead of silently falling through to a hard block.
 */
object SafetyPackages {
    enum class Tier { HARD, DEFAULT_ALLOWED }

    data class Entry(
        val pkg: String,
        @StringRes val labelRes: Int,
        @StringRes val kindRes: Int,
        val tier: Tier,
    )

    val entries: List<Entry> = listOf(
        Entry("com.android.dialer", R.string.safety_dialer_aosp, R.string.safety_kind_calls, Tier.HARD),
        Entry("com.google.android.dialer", R.string.safety_dialer_google, R.string.safety_kind_calls, Tier.HARD),
        Entry("com.samsung.android.dialer", R.string.safety_dialer_samsung, R.string.safety_kind_calls, Tier.HARD),
        Entry("com.samsung.android.app.telephonyui", R.string.safety_telephonyui_samsung, R.string.safety_kind_calls, Tier.HARD),
        Entry("com.android.emergency", R.string.safety_emergency, R.string.safety_kind_emergency, Tier.HARD),
        Entry("com.android.systemui", R.string.safety_systemui, R.string.safety_kind_system, Tier.HARD),
        // Settings must stay reachable in allowlist mode too; strict mode
        // guards the two dangerous screens inside it (see SettingsGuard).
        Entry("com.android.settings", R.string.safety_settings, R.string.safety_kind_system, Tier.DEFAULT_ALLOWED),
        Entry("com.google.android.apps.maps", R.string.safety_maps_google, R.string.safety_kind_navigation, Tier.DEFAULT_ALLOWED),
        Entry("com.waze", R.string.safety_waze, R.string.safety_kind_navigation, Tier.DEFAULT_ALLOWED),
        Entry("com.tranzmate", R.string.safety_moovit, R.string.safety_kind_navigation, Tier.DEFAULT_ALLOWED),
        Entry("com.android.mms", R.string.safety_mms, R.string.safety_kind_messaging, Tier.DEFAULT_ALLOWED),
        Entry("com.google.android.apps.messaging", R.string.safety_messages_google, R.string.safety_kind_messaging, Tier.DEFAULT_ALLOWED),
        Entry("com.samsung.android.messaging", R.string.safety_messages_samsung, R.string.safety_kind_messaging, Tier.DEFAULT_ALLOWED),
    )

    /** Never blockable, no override. BlockerService unions this with the
     *  device's actual default dialer, resolved at runtime. */
    val hardBlockExempt: Set<String> =
        entries.filter { it.tier == Tier.HARD }.map { it.pkg }.toSet()

    /** Exempt by default, overridable. BlockerService unions this with the
     *  device's actual default SMS app, resolved at runtime. */
    val defaultAllowed: Set<String> =
        entries.filter { it.tier == Tier.DEFAULT_ALLOWED }.map { it.pkg }.toSet()
}
