package app.atzor.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.AtzorToggle
import app.atzor.ui.UiBus
import app.atzor.ui.theme.A2_700
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft

/**
 * App settings: defenses, system permissions, and feedback toggles.
 * Kept off the home screen so the vault stays the focus.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val state by Store.state.collectAsState()
    var showStrictConfirm by remember { mutableStateOf(false) }

    if (showStrictConfirm) {
        AlertDialog(
            onDismissRequest = { showStrictConfirm = false },
            containerColor = CardBg,
            title = {
                Text(
                    stringResource(R.string.strict_confirm_title),
                    color = Cream,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            },
            text = { Text(stringResource(R.string.strict_confirm_body), color = CreamSoft) },
            confirmButton = {
                TextButton(onClick = {
                    showStrictConfirm = false
                    Store.setStrictMode(true)
                }) {
                    Text(
                        stringResource(R.string.strict_confirm_enable),
                        color = Coral,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showStrictConfirm = false }) {
                    Text(stringResource(R.string.strict_confirm_cancel), color = CreamSoft)
                }
            },
        )
    }
    val context = LocalContext.current

    var notifAccess by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var battExempt by remember {
        mutableStateOf(
            (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName),
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                battExempt = (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = Cream)
            Text(
                stringResource(R.string.settings_back),
                style = MaterialTheme.typography.labelLarge,
                color = CoralDeep,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = CreamSoft,
        )

        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(26.dp), spotColor = Color(0x332E2B25))
                .background(CardBg, RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.settings_during_lock), style = MaterialTheme.typography.titleLarge, color = Cream, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))

            ShieldToggleRow(
                title = stringResource(R.string.settings_strict_title),
                subtitle = stringResource(R.string.settings_strict_sub),
                checked = state.strictMode,
            ) { on ->
                when {
                    state.lockedNow() && !on -> UiBus.say(app.atzor.R.string.settings_locked_strict)
                    // Turning it ON is the consequential direction, so it gets a
                    // plain-language confirmation first. Turning it off does not.
                    on -> showStrictConfirm = true
                    else -> Store.setStrictMode(false)
                }
            }
            Spacer(Modifier.height(12.dp))
            ShieldToggleRow(
                title = stringResource(R.string.settings_gentle_title),
                subtitle = stringResource(R.string.settings_gentle_sub),
                checked = state.gentleMode,
            ) { on ->
                if (state.lockedNow() && on) {
                    UiBus.say(app.atzor.R.string.settings_locked_change)
                } else {
                    Store.setGentleMode(on)
                }
            }
            Spacer(Modifier.height(12.dp))
            ShieldLinkRow(
                title = stringResource(R.string.settings_notif_title),
                subtitle = if (notifAccess)
                    stringResource(R.string.settings_notif_on)
                else
                    stringResource(R.string.settings_notif_off),
                granted = notifAccess,
            ) {
                if (state.lockedNow()) {
                    UiBus.say(app.atzor.R.string.settings_locked_generic)
                } else {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ShieldLinkRow(
                title = stringResource(R.string.settings_batt_title),
                subtitle = if (battExempt)
                    stringResource(R.string.settings_batt_on)
                else
                    stringResource(R.string.settings_batt_off),
                granted = battExempt,
            ) {
                if (state.lockedNow()) {
                    UiBus.say(app.atzor.R.string.settings_locked_generic)
                } else {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(26.dp), spotColor = Color(0x332E2B25))
                .background(CardBg, RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.settings_feedback), style = MaterialTheme.typography.titleLarge, color = Cream, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            ShieldToggleRow(
                title = stringResource(R.string.settings_haptics_title),
                subtitle = stringResource(R.string.settings_haptics_sub),
                checked = state.hapticsEnabled,
            ) { Store.setHapticsEnabled(it) }
            Spacer(Modifier.height(12.dp))
            ShieldToggleRow(
                title = stringResource(R.string.settings_sound_title),
                subtitle = stringResource(R.string.settings_sound_sub),
                checked = state.soundEnabled,
            ) { Store.setSoundEnabled(it) }
            Spacer(Modifier.height(12.dp))
            ShieldToggleRow(
                title = stringResource(R.string.settings_motion_title),
                subtitle = stringResource(R.string.settings_motion_sub),
                checked = state.reduceMotion,
            ) { Store.setReduceMotion(it) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShieldToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CreamSoft, fontSize = 13.sp)
        }
        AtzorToggle(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ShieldLinkRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CreamSoft, fontSize = 13.sp)
        }
        if (granted) {
            Text(stringResource(R.string.settings_enabled), color = A2_700, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp)
        } else {
            Text(
                stringResource(R.string.settings_enable),
                color = CoralDeep,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
            )
        }
    }
}
