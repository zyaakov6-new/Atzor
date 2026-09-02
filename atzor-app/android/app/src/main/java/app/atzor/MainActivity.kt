package app.atzor

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.atzor.data.Store
import app.atzor.ui.UiBus
import app.atzor.ui.screens.AtzorRoot
import app.atzor.ui.theme.AtzorTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { UiBus.onKeyScanned(it) }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status/nav bars; every screen already pads its own content.
        enableEdgeToEdge()

        // If the previous run crashed, show the reason on screen instead of
        // risking the same crash again. Screenshot it to diagnose without adb.
        val crashPrefs = getSharedPreferences("atzor_crash", MODE_PRIVATE)
        val savedCrash = crashPrefs.getString("trace", null)
        if (savedCrash != null) {
            setContent { app.atzor.ui.CrashReportScreen(savedCrash) { crashPrefs.edit().clear().commit(); recreate() } }
            return
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        UiBus.launchQrScan = {
            qrLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("")
                    .setBeepEnabled(false)
                    .setOrientationLocked(true)
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        handleNfcIntent(intent)
        handleShortcut(intent)

        setContent {
            AtzorTheme {
                AtzorRoot()
            }
        }
    }

    /** Launcher long-press shortcuts: instant lock without touching the UI. */
    private fun handleShortcut(intent: Intent?) {
        val state = app.atzor.data.Store.state.value
        when (intent?.action) {
            "app.atzor.action.LOCK_HOUR" -> when {
                state.lockedNow() -> UiBus.say(R.string.msg_already_locked)
                state.blockSetEmpty -> UiBus.say(R.string.msg_pick_apps_first)
                else -> {
                    app.atzor.data.Store.startSession(60L * 60L * 1000L, "shortcut")
                    UiBus.say(R.string.msg_locked_for_hour)
                }
            }
            "app.atzor.action.LOCK_KEY" -> when {
                state.lockedNow() -> UiBus.say(R.string.msg_already_locked)
                state.blockSetEmpty -> UiBus.say(R.string.msg_pick_apps_first)
                !state.hasKey -> UiBus.say(R.string.msg_need_key_first)
                else -> {
                    app.atzor.data.Store.startSession(null, "shortcut")
                    UiBus.say(R.string.msg_locked_until_key)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val launchIntent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        adapter.enableForegroundDispatch(this, pending, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
        handleShortcut(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val isNfc = intent.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED
        if (!isNfc) return

        @Suppress("DEPRECATION")
        val tag: Tag? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val id = tag?.id ?: return
        val hex = id.joinToString("") { "%02x".format(it) }
        // While registering, also write an atzor:// NDEF record (best effort):
        // a written tag launches עצור from any screen, no app-switching needed.
        val ndefWritten = if (UiBus.nfcRegisterMode.value) writeAtzorNdef(tag) else false
        UiBus.onNfcTag(hex, ndefWritten)
    }

    private fun writeAtzorNdef(tag: Tag): Boolean = runCatching {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createUri("atzor://key"),
                NdefRecord.createApplicationRecord(packageName),
            ),
        )
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            val ok = ndef.isWritable && ndef.maxSize >= message.toByteArray().size
            if (ok) ndef.writeNdefMessage(message)
            ndef.close()
            ok
        } else {
            val formatable = NdefFormatable.get(tag) ?: return false
            formatable.connect()
            formatable.format(message)
            formatable.close()
            true
        }
    }.getOrDefault(false)
}
