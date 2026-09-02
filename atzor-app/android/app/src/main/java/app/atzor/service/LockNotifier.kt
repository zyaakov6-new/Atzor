package app.atzor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.atzor.MainActivity
import app.atzor.R
import app.atzor.data.AtzorState

/**
 * The ongoing "עצור נועל" notification with a live countdown.
 * Uses the system chronometer, so the timer ticks without re-posting.
 */
object LockNotifier {

    private const val CHANNEL_ID = "atzor_lock"
    private const val NOTIFICATION_ID = 71

    /** (locked, endAt) last posted, to avoid churn on every state emission. */
    private var lastKey: Pair<Boolean, Long>? = null

    fun sync(context: Context, state: AtzorState) {
        val now = System.currentTimeMillis()
        val locked = state.lockedNow(now)
        val endAt = if (locked) state.lockEndAt(now) else 0L
        val key = locked to endAt
        if (key == lastKey) return
        lastKey = key

        // The widget mirrors the same lock state; refresh it on every change.
        runCatching { app.atzor.widget.AtzorWidgetProvider.updateAll(context) }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!locked) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_desc) },
        )

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.notif_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (endAt == Long.MAX_VALUE) {
            builder.setContentText(context.getString(R.string.notif_until_key))
        } else {
            // Live countdown to the lock end, rendered by the system.
            builder
                .setContentText(context.getString(R.string.notif_countdown))
                .setWhen(endAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        runCatching { nm.notify(NOTIFICATION_ID, builder.build()) }
    }
}
