package app.atzor.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.UiBus
import app.atzor.util.VaultFeedback

/**
 * Auto-locks only when a *watched* Bluetooth device connects (e.g. car stereo).
 * Optionally unlocks when that same device disconnects.
 */
class BluetoothLockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val state = Store.state.value
        if (!state.btLockEnabled || state.btLockAddresses.isEmpty()) return

        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return
        if (address !in state.btLockAddresses) return
        val label = state.btLockLabels[address] ?: device.name ?: address

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (state.lockedNow()) return
                if (state.blockSetEmpty) {
                    UiBus.say(R.string.bt_no_apps)
                    return
                }
                val duration = state.btLockDurationMs
                Store.startSessionFromBluetooth(if (duration <= 0L) null else duration)
                Store.recordKeyUsed("bt:$address")
                VaultFeedback.playSeal(context.applicationContext)
                UiBus.say(R.string.bt_locked_because, label)
                LockNotifier.sync(context.applicationContext, Store.state.value)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (!state.btUnlockOnDisconnect) return
                if (!state.lockedNow()) return
                // Only end a manual session started by BT/user - not pure schedule if not manual.
                if (state.manualActive) {
                    Store.unlockNow()
                    VaultFeedback.playOpen(context.applicationContext)
                    UiBus.say(R.string.bt_opened_disconnect, label)
                    LockNotifier.sync(context.applicationContext, Store.state.value)
                }
            }
        }
    }
}
