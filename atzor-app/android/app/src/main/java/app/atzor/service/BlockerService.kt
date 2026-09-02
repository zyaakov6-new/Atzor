package app.atzor.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import app.atzor.R
import app.atzor.data.AtzorState
import app.atzor.data.SafetyPackages
import app.atzor.data.SettingsGuard
import app.atzor.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches which app is in the foreground. When a blocked app surfaces while a
 * lock (manual session or schedule) is active, we bounce the user home and show
 * the lock screen. canRetrieveWindowContent is off: only package names are seen.
 *
 * Two extra duties:
 *  - It remembers the last foreground app, so the moment a lock STARTS (tile,
 *    QR, NFC, schedule) the app already on screen is enforced immediately,
 *    not only on the next app switch.
 *  - In strict mode it guards the package installer, plus the two Settings
 *    screens that can defang עצור (accessibility and app info). The rest of
 *    Settings stays usable mid-lock: sealing all of it once left a user unable
 *    to pair Bluetooth headphones with no way out. See SettingsGuard.
 */
class BlockerService : AccessibilityService() {

    private var lastBlockedAt = 0L
    private var lastBlockedPkg: String? = null
    private var lastForegroundPkg: String? = null

    private var launchableCache: Set<String> = emptySet()
    private var launchableCachedAt = 0L
    private var launcherCache: Set<String> = emptySet()

    private var scope: CoroutineScope? = null
    private val overlay by lazy { LockOverlay(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            Store.touchLockClock()
            handler.postDelayed(this, 60_000L)
        }
    }

    /**
     * Packages that are dangerous in their entirety, so no sub-screen detection
     * is needed. com.android.settings is deliberately NOT here: it is handled
     * screen by screen through SettingsGuard.
     */
    private val guardedPackages = setOf(
        "com.samsung.accessibility",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.sec.android.app.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    private val settingsPackage = "com.android.settings"

    /** Debounce for the Settings bounce, and a loop-breaker: if BACK keeps
     *  landing on another guarded screen we go home instead of ping-ponging. */
    private var lastBounceAt = 0L
    private var consecutiveBounces = 0

    private var dialerCache: String? = null
    private var dialerCachedAt = 0L
    private var smsCache: String? = null
    private var smsCachedAt = 0L

    /** The device's actual default dialer app, whatever OEM it comes from.
     *  Unioned with SafetyPackages.hardBlockExempt so it is never blocked. */
    private fun dynamicDialerPackage(): String? {
        val now = System.currentTimeMillis()
        if (dialerCache == null || now - dialerCachedAt > 12L * 60L * 60L * 1000L) {
            dialerCache = runCatching {
                (getSystemService(TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
            }.getOrNull()
            dialerCachedAt = now
        }
        return dialerCache
    }

    /** The device's actual default SMS/RCS app, whatever OEM it comes from.
     *  Unioned with SafetyPackages.defaultAllowed (still overridable). */
    private fun dynamicSmsPackage(): String? {
        val now = System.currentTimeMillis()
        if (smsCache == null || now - smsCachedAt > 12L * 60L * 60L * 1000L) {
            smsCache = runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
            smsCachedAt = now
        }
        return smsCache
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            var wasLocked = Store.state.value.lockedNow()
            Store.state.collect { state ->
                val locked = state.lockedNow()
                // Lock just started: enforce against whatever is on screen right now.
                if (locked && !wasLocked) lastForegroundPkg?.let { enforce(it, state) }
                // Lock just ended: drop the overlay if it is up.
                if (!locked) overlay.hide()
                wasLocked = locked
                // Keep the ongoing countdown notification in sync with the lock.
                LockNotifier.sync(this@BlockerService, state)
            }
        }
        handler.postDelayed(clockTick, 60_000L)
    }

    override fun onDestroy() {
        overlay.hide()
        scope?.cancel()
        scope = null
        handler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!isStateChange && !isContentChange) return
        val pkg = event.packageName?.toString() ?: return

        // Content-changed fires constantly while an app is used; it is the fast
        // path that catches a blocked app even when the switch event was late.
        // Only state changes update the foreground tracker (content events can
        // arrive from background windows like keyboards).
        if (isStateChange && pkg != "com.android.systemui") lastForegroundPkg = pkg
        if (pkg == packageName) return

        val state = Store.state.value
        if (!state.lockedNow()) return

        // Settings is guarded screen by screen, never as a whole package, and
        // only a window-state change tells us which screen is showing.
        if (pkg == settingsPackage) {
            if (isStateChange) guardSettingsScreen(event.className?.toString(), state)
            return
        }
        enforce(pkg, state)
    }

    /**
     * Bounce out of the two Settings screens that could disable or remove עצור.
     * Everything else in Settings is left alone. Bouncing with BACK is
     * deliberate: it is proportionate for a screen someone may have opened by
     * accident, and far less alarming than a full lock screen.
     */
    private fun guardSettingsScreen(className: String?, state: AtzorState) {
        if (!state.strictMode) return
        if (!SettingsGuard.shouldBounce(className)) return

        val now = System.currentTimeMillis()
        if (now - lastBounceAt < 700) return
        consecutiveBounces = if (now - lastBounceAt < 3000) consecutiveBounces + 1 else 1
        lastBounceAt = now

        if (consecutiveBounces >= 3) {
            // BACK keeps returning to a guarded screen; leave Settings entirely.
            consecutiveBounces = 0
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        overlay.showNotice(R.string.notice_settings_guarded)
    }

    private fun enforce(pkg: String, state: AtzorState) {
        if (pkg == packageName) return
        // An overlay is already covering the screen; don't rebuild it on every
        // content event from the app underneath (gentle mode keeps the app alive).
        if (overlay.isShowing) return

        val isGuarded = state.strictMode && pkg in guardedPackages
        val isBlockedApp = !isGuarded &&
            pkg !in launcherPackages() &&
            state.isEffectivelyBlocked(
                pkg,
                launchable = pkg in launchablePackages(),
                hardExempt = SafetyPackages.hardBlockExempt + listOfNotNull(dynamicDialerPackage()),
                defaultAllowed = SafetyPackages.defaultAllowed + listOfNotNull(dynamicSmsPackage()),
            )
        if (!isBlockedApp && !isGuarded) return

        val now = System.currentTimeMillis()

        // Gentle mode (global or a per-app override): an "enter anyway" pass
        // lets the app breathe for 5 minutes. Guarded settings never get a
        // pass; the protection stays hard.
        val gentle = isBlockedApp && state.isGentleFor(pkg)
        if (isBlockedApp && gentle &&
            state.gentlePassPkg == pkg && state.gentlePassUntil > now
        ) return

        // Debounce so we do not spam the lock screen while the same app resurfaces.
        if (pkg == lastBlockedPkg && now - lastBlockedAt < 1200) return
        lastBlockedAt = now
        lastBlockedPkg = pkg

        if (isBlockedApp) Store.recordAttempt()

        // Gentle pause floats OVER the app (no bounce): the user may choose to
        // continue right where they were. Hard blocks bounce home underneath.
        if (!gentle) performGlobalAction(GLOBAL_ACTION_HOME)
        overlay.show(
            pkg = if (isBlockedApp) pkg else null,
            protection = isGuarded,
            gentle = gentle,
        )
    }

    /** All apps with a launcher icon; the universe the allowlist applies to. Cached 12h. */
    private fun launchablePackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (launchableCache.isEmpty() || now - launchableCachedAt > 12L * 60L * 60L * 1000L) {
            launchableCache = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
                    .toSet()
            }.getOrDefault(launchableCache)
            launchableCachedAt = now
        }
        return launchableCache
    }

    /** The user's home screen app(s); blocking these would brick the phone. */
    private fun launcherPackages(): Set<String> {
        if (launcherCache.isEmpty()) {
            launcherCache = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
        }
        return launcherCache
    }

    override fun onInterrupt() = Unit
}
