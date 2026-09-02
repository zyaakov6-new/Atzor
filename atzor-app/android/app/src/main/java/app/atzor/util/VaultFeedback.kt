package app.atzor.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.atzor.data.Store

/** Distinct mechanical feedback for vault close vs open. */
object VaultFeedback {
    private var lastAt = 0L

    fun playSeal(context: Context) = play(context, Kind.SEAL)

    fun playOpen(context: Context) = play(context, Kind.OPEN)

    private enum class Kind { SEAL, OPEN }

    private fun play(context: Context, kind: Kind) {
        val now = System.currentTimeMillis()
        if (now - lastAt < 600L) return
        lastAt = now
        val state = Store.state.value
        if (state.hapticsEnabled) {
            runCatching { vibrate(context, kind) }
        }
        if (state.soundEnabled) {
            runCatching { tone(kind) }
        }
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun vibrate(context: Context, kind: Kind) {
        val v = vibrator(context)
        if (Build.VERSION.SDK_INT >= 26) {
            when (kind) {
                // Seal: two firm clanks (heavy close).
                Kind.SEAL -> v.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 32, 45, 70), -1),
                )
                // Open: rising soft ticks (release).
                Kind.OPEN -> v.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 18, 35, 22, 40, 30), -1),
                )
            }
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(if (kind == Kind.SEAL) 90 else 55)
        }
    }

    private fun tone(kind: Kind) {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
        when (kind) {
            Kind.SEAL -> tg.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            Kind.OPEN -> tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 80)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { tg.release() }
        }, 220)
    }
}
