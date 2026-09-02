package app.atzor.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.atzor.MainActivity
import app.atzor.data.Store

/**
 * Quick Settings tile: one tap in the shade locks with the last-used duration.
 * Tapping while locked opens the app (locks end with the key/emergency flow,
 * not with a casual shade tap - except plain timed sessions, which unlock).
 */
class AtzorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val state = Store.state.value
        when {
            !state.lockedNow() -> {
                if (state.blockSetEmpty || (state.keyOnly && !state.hasKey)) {
                    openApp()
                } else {
                    Store.startSession(state.lastDurationMs, "tile")
                }
            }
            // Timed manual session: a shade tap may end it, same as in-app.
            state.manualActive && !state.keyOnly -> Store.unlockNow()
            // Key-only or scheduled locks need the key/emergency: go to the app.
            else -> openApp()
        }
        refresh()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val locked = Store.state.value.lockedNow()
        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(app.atzor.R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(if (locked) app.atzor.R.string.tile_locked else app.atzor.R.string.tile_open)
        }
        tile.updateTile()
    }
}
