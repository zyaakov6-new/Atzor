package app.atzor.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.atzor.data.AtzorState
import app.atzor.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * While a lock (manual or scheduled) is active, notifications from blocked apps
 * are snoozed so the phone stays genuinely quiet. Nothing is deleted: the
 * moment the lock ends, everything held is redelivered.
 *
 * Requires the user to grant Notification Access once
 * (Settings → Notifications → Notification access → עצור).
 */
class AtzorNotificationListener : NotificationListenerService() {

    private var scope: CoroutineScope? = null

    override fun onListenerConnected() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            var wasLocked = Store.state.value.lockedNow()
            Store.state.collect { state ->
                val locked = state.lockedNow()
                if (locked) {
                    // Lock started (or blocklist changed mid-lock): hold everything.
                    sweepActive(state)
                } else if (wasLocked) {
                    // Lock just ended (key, emergency, or manual): deliver the backlog now.
                    releaseSnoozed()
                }
                wasLocked = locked
            }
        }
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        maybeSnooze(sbn, Store.state.value)
    }

    /** Re-snooze for 1ms: the system redelivers every held notification immediately. */
    private fun releaseSnoozed() {
        runCatching { snoozedNotifications }.getOrNull()?.forEach { sbn ->
            runCatching { snoozeNotification(sbn.key, 1L) }
        }
    }

    private fun sweepActive(state: AtzorState) {
        runCatching { activeNotifications }.getOrNull()?.forEach { maybeSnooze(it, state) }
    }

    private fun maybeSnooze(sbn: StatusBarNotification, state: AtzorState) {
        val now = System.currentTimeMillis()
        if (!state.lockedNow(now)) return
        // Launchability is irrelevant for notifications; allowlist mode treats
        // any non-allowed notifying package as blocked.
        if (!state.isAppBlocked(sbn.packageName, launchable = true)) return
        if (sbn.packageName == packageName) return
        // Leave ongoing/foreground-service notifications alone (music, navigation, uploads).
        if (sbn.isOngoing) return

        val end = state.lockEndAt(now)
        val remaining = if (end == Long.MAX_VALUE) MAX_SNOOZE_CHUNK_MS
        else (end - now).coerceIn(1_000L, MAX_TIMED_SNOOZE_MS)
        runCatching { snoozeNotification(sbn.key, remaining) }
    }

    private companion object {
        /**
         * Key-only locks have no end time, so snooze in chunks; if the chunk
         * expires mid-lock the repost lands back in onNotificationPosted and
         * is snoozed again before it can make noise.
         */
        const val MAX_SNOOZE_CHUNK_MS = 30L * 60L * 1000L
        const val MAX_TIMED_SNOOZE_MS = 24L * 60L * 60L * 1000L
    }
}
