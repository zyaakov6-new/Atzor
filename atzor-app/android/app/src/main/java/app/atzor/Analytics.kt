package app.atzor

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin wrapper around Firebase Analytics, mirroring CrashReporter's defensive
 * pattern: every call no-ops safely if Firebase was never configured (no
 * google-services.json - see app/build.gradle.kts).
 *
 * Hard privacy rules for every event logged through here:
 *  - Never log a package name, app label, tag id, QR secret, or anything
 *    that identifies which apps a user blocks.
 *  - Never log a raw duration or count. Always bucket via [durationBucket]
 *    or [countBucket].
 *  - No user id beyond Firebase's own anonymous instance id.
 */
object Analytics {
    private lateinit var appContext: Context

    /** Called once from Application.onCreate, mirroring Store.init. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
    }

    private val analytics: FirebaseAnalytics?
        get() = runCatching {
            if (!::appContext.isInitialized) return null
            FirebaseAnalytics.getInstance(appContext)
        }.getOrNull()

    private fun log(name: String, params: Map<String, String> = emptyMap()) {
        runCatching {
            val fa = analytics ?: return
            val bundle = Bundle()
            params.forEach { (k, v) -> bundle.putString(k, v) }
            fa.logEvent(name, bundle)
        }.onFailure { Log.w("Atzor", "Analytics not configured; skipping $name") }
    }

    /** ms == null or Long.MAX_VALUE (key-only sessions) buckets as "untimed". */
    fun durationBucket(ms: Long?): String = when {
        ms == null || ms == Long.MAX_VALUE -> "untimed"
        ms < 15L * 60_000L -> "under_15m"
        ms < 60L * 60_000L -> "15_60m"
        ms < 4L * 60L * 60_000L -> "1_4h"
        else -> "4h_plus"
    }

    fun countBucket(n: Int): String = when {
        n <= 0 -> "0"
        n <= 3 -> "1_3"
        n <= 10 -> "4_10"
        else -> "10_plus"
    }

    fun logLockStarted(method: String, durationMs: Long?) =
        log("lock_started", mapOf("method" to method, "duration_bucket" to durationBucket(durationMs)))

    fun logLockEnded(reason: String, actualDurationMs: Long) =
        log("lock_ended", mapOf("reason" to reason, "actual_duration_bucket" to durationBucket(actualDurationMs)))

    fun logEmergencyRequested() = log("emergency_requested")

    fun logEmergencyCompleted() = log("emergency_completed")

    fun logGentlePassUsed() = log("gentle_pass_used")

    fun logKeyRegistered(type: String) = log("key_registered", mapOf("type" to type))

    fun logBlocklistConfigured(mode: String, appCount: Int) =
        log("blocklist_configured", mapOf("mode" to mode, "app_count_bucket" to countBucket(appCount)))

    fun logOnboardingCompleted(stepReached: Int) =
        log("onboarding_completed", mapOf("step_reached" to stepReached.toString()))
}
