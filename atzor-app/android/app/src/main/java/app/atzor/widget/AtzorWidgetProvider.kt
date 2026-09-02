package app.atzor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import app.atzor.MainActivity
import app.atzor.R
import app.atzor.data.Store
import app.atzor.util.VaultFeedback

/**
 * Home-screen widget v2: vault-themed circle (tap = lock last duration /
 * unlock timed session), live countdown, streak / today summary.
 */
class AtzorWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "app.atzor.widget.TOGGLE"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AtzorWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val state = Store.state.value
            val now = System.currentTimeMillis()
            val locked = state.lockedNow(now)
            val endAt = state.lockEndAt(now)

            val views = RemoteViews(context.packageName, R.layout.widget_atzor)

            views.setInt(
                R.id.widget_root, "setBackgroundResource",
                if (locked) R.drawable.widget_bg_locked else R.drawable.widget_bg,
            )
            views.setInt(
                R.id.widget_button, "setBackgroundResource",
                if (locked) R.drawable.widget_circle_locked else R.drawable.widget_circle_open,
            )
            views.setTextViewText(
                R.id.widget_btn_label,
                context.getString(if (locked) R.string.widget_locked else R.string.widget_lock_action),
            )
            views.setTextColor(
                R.id.widget_title,
                if (locked) 0xFFB9C1A4.toInt() else 0xFF56633F.toInt(),
            )
            views.setTextViewText(
                R.id.widget_title,
                context.getString(if (locked) R.string.widget_title_locked else R.string.widget_title_open),
            )
            views.setTextColor(
                R.id.widget_status,
                if (locked) 0xFFA8B193.toInt() else 0xFF82796A.toInt(),
            )
            views.setTextColor(
                R.id.widget_timer,
                if (locked) 0xFFE9DCC0.toInt() else 0xFF56633F.toInt(),
            )

            if (locked && endAt != Long.MAX_VALUE) {
                views.setViewVisibility(R.id.widget_timer, android.view.View.VISIBLE)
                views.setChronometerCountDown(R.id.widget_timer, true)
                views.setChronometer(
                    R.id.widget_timer,
                    SystemClock.elapsedRealtime() + (endAt - now).coerceAtLeast(0L),
                    null,
                    true,
                )
            } else {
                views.setViewVisibility(R.id.widget_timer, android.view.View.GONE)
            }

            val today = java.time.LocalDate.now().toEpochDay()
            val openMs = if (state.lockMarkAt > 0L) {
                (minOf(now, state.lockMarkUntil) - state.lockMarkAt).coerceAtLeast(0L)
            } else 0L
            val todayMin = ((if (state.lockedDayEpoch == today) state.lockedTodayMs else 0L) + openMs) / 60_000L
            views.setTextViewText(
                R.id.widget_status,
                when {
                    locked && endAt == Long.MAX_VALUE -> context.getString(R.string.widget_until_key)
                    locked && todayMin > 0 ->
                        context.getString(R.string.widget_today_streak, todayMin, state.streakDays)
                    state.streakDays > 0 ->
                        context.getString(R.string.widget_streak_today, state.streakDays, todayMin)
                    todayMin > 0 -> context.getString(R.string.widget_today_only, todayMin)
                    else -> context.getString(R.string.widget_tap_to_lock)
                },
            )

            views.setOnClickPendingIntent(
                R.id.widget_button,
                PendingIntent.getBroadcast(
                    context, 1,
                    Intent(context, AtzorWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 2,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        val state = Store.state.value
        when {
            !state.lockedNow() -> {
                if (state.blockSetEmpty || (state.lastDurationMs <= 0L)) {
                    openApp(context)
                } else {
                    Store.startSession(state.lastDurationMs, "widget")
                    VaultFeedback.playSeal(context.applicationContext)
                }
            }
            state.manualActive && !state.keyOnly -> {
                Store.unlockNow()
                VaultFeedback.playOpen(context.applicationContext)
            }
            else -> openApp(context)
        }
        updateAll(context)
    }

    private fun openApp(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
