package app.atzor

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin wrapper around Firebase Crashlytics. Every call is defensive: without a
 * real google-services.json (see app/build.gradle.kts), FirebaseApp never
 * initializes and FirebaseCrashlytics.getInstance() throws - we swallow that
 * and fall back to Logcat only, so the on-device crash screen remains the
 * source of truth until Firebase is actually configured.
 */
object CrashReporter {
    private val crashlytics: FirebaseCrashlytics?
        get() = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()

    fun recordException(throwable: Throwable) {
        runCatching { crashlytics?.recordException(throwable) }
            .onFailure { Log.w("Atzor", "Crashlytics not configured; skipping recordException") }
    }

    fun log(message: String) {
        runCatching { crashlytics?.log(message) }
    }
}
