package app.atzor.ui

import androidx.annotation.StringRes
import app.atzor.R
import app.atzor.data.Store
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A user-facing message as a string resource plus its format args, so copy
 * stays in strings.xml even though this object has no Context. The collector
 * (AtzorRoot) resolves it against the real resources.
 */
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

/** Tiny bridge between MainActivity (NFC intents, scanner results) and the Compose UI. */
object UiBus {
    /** When true, the next NFC tap registers the tag instead of toggling the lock. */
    val nfcRegisterMode = MutableStateFlow(false)

    /** One-shot user-facing message; screens collect and clear it. */
    val message = MutableStateFlow<UiMessage?>(null)

    /** Post a one-shot message. Args must never include a package name or key id. */
    fun say(@StringRes resId: Int, vararg args: Any) {
        message.value = UiMessage(resId, args.toList())
    }

    var launchQrScan: () -> Unit = {}

    fun onNfcTag(tagIdHex: String, ndefWritten: Boolean = false) {
        if (nfcRegisterMode.value) {
            Store.registerNfcTag(tagIdHex)
            nfcRegisterMode.value = false
            say(if (ndefWritten) R.string.key_tag_saved_written else R.string.key_tag_saved)
            return
        }
        val state = Store.state.value
        when {
            state.nfcTagIds.isEmpty() -> say(R.string.key_no_tag_yet)
            Store.keyPresented(tagIdHex) ->
                say(if (Store.state.value.lockedNow()) R.string.key_locked_now else R.string.key_opened_now)
            else -> say(R.string.key_wrong_tag)
        }
    }

    fun onKeyScanned(contents: String) {
        if (Store.keyPresented(contents)) {
            say(if (Store.state.value.lockedNow()) R.string.key_locked_now else R.string.key_opened_now)
        } else {
            say(R.string.key_unknown_code)
        }
    }
}
