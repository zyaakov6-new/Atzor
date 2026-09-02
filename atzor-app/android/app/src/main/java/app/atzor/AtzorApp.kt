package app.atzor

import android.app.Application
import android.util.Log
import app.atzor.data.Store
import java.io.File

class AtzorApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Capture any crash to a file readable from the phone's file manager,
        // so problems can be diagnosed without a USB/adb connection.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = Log.getStackTraceString(throwable)
                Log.e("Atzor", "Uncaught crash", throwable)
                // Persist synchronously so the next launch can display it on screen
                // (no USB / dev-options needed to diagnose).
                getSharedPreferences("atzor_crash", MODE_PRIVATE)
                    .edit().putString("trace", trace).commit()
                getExternalFilesDir(null)?.let { dir ->
                    File(dir, "atzor-crash.txt").writeText(trace)
                }
                // Also report to Crashlytics once Firebase is configured (no-op until then).
                CrashReporter.recordException(throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }

        runCatching { Store.init(this) }
            .onFailure { Log.e("Atzor", "Store.init failed", it) }
        runCatching { Analytics.init(this) }
            .onFailure { Log.e("Atzor", "Analytics.init failed", it) }
    }
}
