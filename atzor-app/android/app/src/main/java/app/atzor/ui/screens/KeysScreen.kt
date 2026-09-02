package app.atzor.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.AtzorToggle
import app.atzor.ui.UiBus
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Night
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.LeafDeep
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.CardBg
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/**
 * Ordering a ready-made tag opens a WhatsApp chat with the message already
 * written, so a first order is a reply rather than a checkout to build.
 * Physical goods leaving the app for an external channel is what Play policy
 * expects; Play Billing only governs digital goods.
 *
 * International format, digits only, no plus sign and no spaces.
 * Empty on purpose falls back to email, so this is never a dead button.
 */
private const val ORDER_WHATSAPP_NUMBER = "972535556146"
private const val ORDER_EMAIL = "zyaakov6@gmail.com"

/** WhatsApp if a number is configured, otherwise a pre-filled email. */
private fun orderKeyUri(message: String, subject: String): android.net.Uri =
    if (ORDER_WHATSAPP_NUMBER.isNotBlank()) {
        android.net.Uri.parse(
            "https://wa.me/$ORDER_WHATSAPP_NUMBER?text=" + android.net.Uri.encode(message),
        )
    } else {
        android.net.Uri.parse(
            "mailto:$ORDER_EMAIL" +
                "?subject=" + android.net.Uri.encode(subject) +
                "&body=" + android.net.Uri.encode(message),
        )
    }

@Composable
fun KeysScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val state by Store.state.collectAsState()
    val registerMode by UiBus.nfcRegisterMode.collectAsState()
    var showQr by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var btPermissionOk by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> btPermissionOk = granted }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.keys_title), style = MaterialTheme.typography.headlineMedium, color = Cream)
            Text(
                stringResource(R.string.keys_back),
                style = MaterialTheme.typography.labelLarge,
                color = CoralDeep,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.keys_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = CreamSoft,
        )

        Spacer(Modifier.height(22.dp))

        if (state.nfcTagIds.isEmpty() && state.qrSecret == null) {
            app.atzor.ui.EmptyStateCard(
                kind = app.atzor.ui.EmptyKind.Keys,
                title = stringResource(R.string.keys_empty_title),
                body = stringResource(R.string.keys_empty_body),
            )
            Spacer(Modifier.height(16.dp))
        }

        // NFC key card
        KeyCard(
            title = stringResource(R.string.keys_nfc_title),
            active = state.nfcTagIds.isNotEmpty(),
            body = when {
                registerMode -> stringResource(R.string.keys_nfc_register)
                state.nfcTagIds.size == 1 -> stringResource(R.string.keys_nfc_one)
                state.nfcTagIds.size > 1 -> stringResource(R.string.keys_nfc_many, state.nfcTagIds.size)
                else -> stringResource(R.string.keys_nfc_none)
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { UiBus.nfcRegisterMode.value = !registerMode },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (registerMode) CardBg else Coral,
                        contentColor = if (registerMode) Cream else app.atzor.ui.theme.OnAccent,
                    ),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        when {
                            registerMode -> stringResource(R.string.keys_nfc_cancel)
                            state.nfcTagIds.isEmpty() -> stringResource(R.string.keys_nfc_add_first)
                            else -> stringResource(R.string.keys_nfc_add_more)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (state.nfcTagIds.isNotEmpty() && !registerMode) {
                    TextButton(onClick = { Store.clearNfcTags() }) {
                        Text(stringResource(R.string.keys_nfc_clear), color = CoralDeep)
                    }
                }
            }
            // Nothing to hold to the phone yet, so offer a ready-made tag.
            // Physical goods, so this leaves the app for a web checkout, which
            // is what Play policy expects (Play Billing covers digital goods).
            if (state.nfcTagIds.isEmpty() && !registerMode) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Night, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        stringResource(R.string.keys_buy_title),
                        color = Cream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.keys_buy_body),
                        color = CreamSoft,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    val orderMessage = stringResource(R.string.keys_buy_message)
                    val orderSubject = stringResource(R.string.keys_buy_subject)
                    Button(
                        onClick = {
                            val sent = runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        orderKeyUri(orderMessage, orderSubject),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.isSuccess
                            // No WhatsApp and no mail app: say so rather than
                            // letting the tap do nothing at all.
                            if (!sent) app.atzor.ui.UiBus.say(R.string.keys_buy_no_app, ORDER_EMAIL)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            stringResource(R.string.keys_buy_button),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            if (state.nfcTagIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                state.nfcTagIds.forEach { tagId ->
                    val label = state.nfcTagLabels[tagId] ?: stringResource(R.string.key_tag_fallback_label)
                    val last = state.keyLastUsed["nfc:$tagId"]
                    var editing by remember(tagId) { mutableStateOf(false) }
                    var draft by remember(tagId, label) { mutableStateOf(label) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(Night, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    ) {
                        if (editing) {
                            BasicTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cream),
                                cursorBrush = SolidColor(Coral),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    Store.setNfcTagLabel(tagId, draft)
                                    editing = false
                                }) { Text(stringResource(R.string.keys_save), color = Coral) }
                                TextButton(onClick = { editing = false; draft = label }) {
                                    Text(stringResource(R.string.keys_cancel), color = CreamSoft)
                                }
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = Cream, fontWeight = FontWeight.Bold)
                                    Text(
                                        last?.let { stringResource(R.string.keys_last_used, fmtWhen(it)) }
                                            ?: stringResource(R.string.keys_never_used),
                                        color = CreamSoft,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    stringResource(R.string.keys_rename),
                                    color = CoralDeep,
                                    modifier = Modifier.clickable { editing = true }.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // QR key card
        val qrLast = state.keyLastUsed["qr"]
        KeyCard(
            title = stringResource(R.string.keys_qr_title),
            active = state.qrSecret != null,
            body = if (state.qrSecret != null)
                stringResource(R.string.keys_qr_have) +
                    (qrLast?.let { stringResource(R.string.keys_qr_last_used, fmtWhen(it)) } ?: "")
            else
                stringResource(R.string.keys_qr_none),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.qrSecret == null) {
                    Button(
                        onClick = {
                            Store.ensureQrSecret()
                            showQr = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.keys_qr_create), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = { UiBus.launchQrScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.keys_qr_scan), style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(onClick = { showQr = !showQr }, shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(if (showQr) R.string.keys_qr_hide else R.string.keys_qr_show), color = Cream)
                    }
                }
            }
        }

        if (showQr) {
            state.qrSecret?.let { secret ->
                Spacer(Modifier.height(18.dp))
                val qrBitmap = remember(secret) {
                    BarcodeEncoder().encodeBitmap(secret, BarcodeFormat.QR_CODE, 720, 720)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(26.dp))
                        .border(1.dp, Line, RoundedCornerShape(26.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(qrBitmap.asImageBitmap(), contentDescription = stringResource(R.string.keys_qr_content_desc))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.keys_qr_print_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5C6A70),
                        fontSize = 13.sp,
                    )
                    TextButton(onClick = { Store.clearQrSecret(); showQr = false }) {
                        Text(stringResource(R.string.keys_qr_delete), color = CoralDeep, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Specific Bluetooth device → auto-lock on connect (never "any Bluetooth").
        KeyCard(
            title = stringResource(R.string.keys_bt_title),
            active = state.btLockEnabled && state.btLockAddresses.isNotEmpty(),
            body = stringResource(R.string.keys_bt_body),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.keys_bt_lock_on_connect), color = Cream, style = MaterialTheme.typography.titleMedium)
                AtzorToggle(checked = state.btLockEnabled) { on ->
                    if (on && !btPermissionOk && Build.VERSION.SDK_INT >= 31) {
                        btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    Store.setBtLockEnabled(on)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(stringResource(R.string.keys_bt_unlock_on_disconnect), color = Cream, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.keys_bt_unlock_hint), color = CreamSoft, fontSize = 12.sp)
                }
                AtzorToggle(
                    checked = state.btUnlockOnDisconnect,
                    onCheckedChange = { Store.setBtUnlockOnDisconnect(it) },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.keys_bt_duration), color = CreamSoft, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.keys_bt_hour) to 60L * 60 * 1000,
                    stringResource(R.string.keys_bt_two_hours) to 2L * 60 * 60 * 1000,
                    stringResource(R.string.keys_bt_until_key) to 0L,
                ).forEach { (label, ms) ->
                    val selected = state.btLockDurationMs == ms
                    Text(
                        label,
                        color = if (selected) Color(0xFFFFFBF2) else Cream,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(
                                if (selected) Coral else Color.Transparent,
                                RoundedCornerShape(999.dp),
                            )
                            .border(1.dp, if (selected) Coral else Line, RoundedCornerShape(999.dp))
                            .clickable { Store.setBtLockDurationMs(ms) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!btPermissionOk && Build.VERSION.SDK_INT >= 31) {
                Text(
                    stringResource(R.string.keys_bt_permission),
                    color = CoralDeep,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                        .padding(bottom = 8.dp),
                )
            }
            val bonded = remember(btPermissionOk) {
                if (!btPermissionOk) emptyList()
                else runCatching {
                    val bm = context.getSystemService(BluetoothManager::class.java)
                    bm?.adapter?.bondedDevices?.toList().orEmpty()
                }.getOrElse { emptyList() }
            }
            if (bonded.isEmpty()) {
                Text(
                    stringResource(R.string.keys_bt_none),
                    color = CreamSoft,
                    fontSize = 13.sp,
                )
            } else {
                bonded.forEach { device ->
                    val addr = device.address ?: return@forEach
                    val name = runCatching { device.name }.getOrNull() ?: addr
                    val on = addr in state.btLockAddresses
                    val last = state.keyLastUsed["bt:$addr"]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { Store.setBtLockDevice(addr, name, !on) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Cream, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                last?.let { stringResource(R.string.keys_last_used, fmtWhen(it)) } ?: addr,
                                color = CreamSoft,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            stringResource(if (on) R.string.keys_bt_marked else R.string.keys_bt_mark),
                            color = if (on) LeafDeep else CoralDeep,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun fmtWhen(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun KeyCard(
    title: String,
    active: Boolean,
    body: String,
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(26.dp))
            .border(1.dp, if (active) LeafDeep.copy(alpha = 0.45f) else Line, RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
            if (active) {
                Box(
                    Modifier
                        .background(LeafDeep.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(stringResource(R.string.keys_bt_active), color = LeafDeep, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = CreamSoft)
        Spacer(Modifier.height(14.dp))
        actions()
    }
}
