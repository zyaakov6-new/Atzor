package app.atzor.data

/**
 * Decides whether a Settings screen is one of the two doors that can defang
 * עצור mid-lock, so strict mode can guard those alone instead of sealing the
 * whole Settings app. Blocking all of Settings is what once left a user unable
 * to pair Bluetooth headphones with no way out.
 *
 * Only [android.view.accessibility.AccessibilityEvent.getClassName] is used.
 * canRetrieveWindowContent is false and stays false, so the node tree is not
 * available and we cannot read the fragment argument that says which app an
 * app-info screen belongs to. Two consequences, both accepted:
 *
 *  - Every app-info screen is guarded, not just ours. Checking another app's
 *    storage mid-lock is rare and low-stakes next to losing Bluetooth.
 *  - Modern Settings hosts most sub-screens inside one generic activity
 *    (SubSettings), which we deliberately do NOT match, because that same
 *    activity also hosts Bluetooth, Wi-Fi and sound.
 *
 * The direction of failure is always [Screen.ALLOW]. A missed guard is a far
 * smaller failure than locking someone out of their own device settings, and
 * it is why the strict-mode copy promises "hard to back out of", not
 * "impossible".
 */
object SettingsGuard {

    enum class Screen { ACCESSIBILITY, APP_INFO, ALLOW }

    /**
     * Activity and alias names, grouped by OEM family. Matching is
     * case-insensitive and substring-based on the trailing class name, so a
     * new OEM variant is a one-line addition here rather than a code change.
     */
    private val accessibilityClasses = listOf(
        // Stock Android / Pixel, and the Samsung and Xiaomi aliases of the same.
        "Settings\$AccessibilitySettingsActivity",
        "Settings\$AccessibilityDetailsSettingsActivity",
        "AccessibilitySettingsActivity",
        "AccessibilitySettingsForSetupWizardActivity",
        "com.android.settings.accessibility.AccessibilitySettings",
        "ToggleAccessibilityServicePreferenceFragment",
        // Samsung ships accessibility in its own package as well; that one is
        // guarded at package level, this catches the in-Settings entry point.
        "com.samsung.accessibility.setting.AccessibilitySettings",
        // Xiaomi / MIUI.
        "com.miui.settings.accessibility.AccessibilitySettingsActivity",
    )

    private val appInfoClasses = listOf(
        // Stock Android / Pixel.
        "Settings\$AppInfoDashboardActivity",
        "InstalledAppDetailsTop",
        "com.android.settings.applications.InstalledAppDetails",
        "AppInfoDashboardFragment",
        // Samsung One UI.
        "Settings\$AppInfoActivity",
        "com.samsung.android.settings.applications.InstalledAppDetails",
        // Xiaomi / MIUI.
        "com.miui.appmanager.ApplicationsDetailsActivity",
    )

    /**
     * Generic hosts that must never match: they render ordinary screens such as
     * Bluetooth and Wi-Fi just as often as anything sensitive. Listed so the
     * intent is explicit rather than an accident of the patterns above.
     */
    private val neverMatch = listOf(
        "com.android.settings.SubSettings",
        "com.android.settings.Settings",
        "com.android.settings.homepage.SettingsHomepageActivity",
    )

    /**
     * [className] is the value from the window-state-change event. Returns
     * [Screen.ALLOW] for anything not confidently identified, including null.
     */
    fun classify(className: String?): Screen {
        val name = className?.trim().orEmpty()
        if (name.isEmpty()) return Screen.ALLOW
        if (neverMatch.any { it.equals(name, ignoreCase = true) }) return Screen.ALLOW
        if (accessibilityClasses.any { name.contains(it, ignoreCase = true) }) return Screen.ACCESSIBILITY
        if (appInfoClasses.any { name.contains(it, ignoreCase = true) }) return Screen.APP_INFO
        return Screen.ALLOW
    }

    /** Whether this screen should be bounced while a strict-mode lock is active. */
    fun shouldBounce(className: String?): Boolean = classify(className) != Screen.ALLOW
}
