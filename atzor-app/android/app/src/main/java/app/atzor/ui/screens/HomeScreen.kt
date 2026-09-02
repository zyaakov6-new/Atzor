package app.atzor.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.atzor.R
import app.atzor.data.Schedule
import app.atzor.data.Store
import app.atzor.ui.HoldToUnlock
import app.atzor.ui.UiBus
import app.atzor.ui.VaultSealScene
import app.atzor.ui.theme.A2_100
import app.atzor.ui.theme.A2_200
import app.atzor.ui.theme.A2_400
import app.atzor.ui.theme.A2_800
import app.atzor.ui.theme.Accent100
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamFaint
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.MiriamLibre
import app.atzor.ui.vaultTimeLabel
import kotlinx.coroutines.delay

private data class DurationChoice(@androidx.annotation.StringRes val labelRes: Int, val ms: Long?)

private val durations = listOf(
    DurationChoice(R.string.home_duration_30m, 30L * 60 * 1000),
    DurationChoice(R.string.home_duration_hour, 60L * 60 * 1000),
    DurationChoice(R.string.home_duration_key, null),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onPickApps: () -> Unit,
    onKeys: () -> Unit,
    onSchedule: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by Store.state.collectAsState()
    var chosen by remember { mutableStateOf(durations[0]) }
    val context = LocalContext.current
    var showKeyOnlyConfirm by remember { mutableStateOf(false) }
    var presetEdit by remember { mutableStateOf<Pair<String, Schedule>?>(null) }

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

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sessionEndAt) {
        var ticks = 0
        while (true) {
            now = System.currentTimeMillis()
            if (ticks++ % 30 == 0) Store.touchLockClock()
            delay(1000)
        }
    }

    val active = state.lockedNow(now)
    val night = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active) {
        night.animateTo(if (active) 1f else 0f, tween(700))
    }
    val nightAlpha = night.value
    val darkText = nightAlpha <= 0.45f
    val titleColor = if (darkText) Color(0xFF201E1D) else Color(0xFFF5EAD8)
    val subColor = if (darkText) Color(0xFF6B6353) else Color(0xFFB9C1A4)

    val blockedPkgs = if (state.allowlistMode) emptySet() else state.blockedApps

    fun tryLock() {
        val st = Store.state.value
        when {
            st.blockSetEmpty -> UiBus.say(app.atzor.R.string.msg_pick_apps_first)
            chosen.ms == null && !st.hasKey ->
                UiBus.say(app.atzor.R.string.msg_need_key_for_untimed)
            chosen.ms == null -> showKeyOnlyConfirm = true
            else -> Store.startSession(chosen.ms, "manual_timed")
        }
    }

    if (showKeyOnlyConfirm) {
        AlertDialog(
            onDismissRequest = { showKeyOnlyConfirm = false },
            containerColor = CardBg,
            title = { Text(stringResource(R.string.home_key_only_title), color = Cream, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.home_key_only_body),
                    color = CreamSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showKeyOnlyConfirm = false
                    Store.startSession(null, "key_only")
                }) { Text(stringResource(R.string.home_key_only_confirm), color = Coral, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showKeyOnlyConfirm = false }) {
                    Text(stringResource(R.string.home_cancel), color = CreamSoft)
                }
            },
        )
    }

    presetEdit?.let { (title, schedule) ->
        PresetEditDialog(
            title = title,
            initial = schedule,
            onDismiss = { presetEdit = null },
            onSave = { edited ->
                Store.addSchedule(edited)
                presetEdit = null
                UiBus.say(app.atzor.R.string.sched_saved)
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ─── Setup reminders ───
        val missing = buildList {
            if (!notifAccess) add(context.getString(R.string.home_perm_notifications))
            if (!battExempt) add(context.getString(R.string.home_perm_background))
        }
        if (missing.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Coral.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .clickable {
                        if (state.lockedNow()) {
                            UiBus.say(app.atzor.R.string.msg_locked_open_first)
                        } else if (!notifAccess) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.home_perm_missing, missing.size, missing.joinToString(" · ")),
                    color = CoralDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.home_perm_fix), color = CoralDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ─── Vault hero (open ↔ sealed) - no sun ───
        Box(
            Modifier
                .fillMaxWidth()
                .height(560.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF7EDD9), Color(0xFFF2E2BF), Color(0xFFECD5A4), Color(0xFFE6CC96)),
                    ),
                ),
        ) {
            // Night overlay when locked.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = nightAlpha * 0.94f }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1D2417), Color(0xFF29321F), Color(0xFF313C25)),
                        ),
                    ),
            )

            // Header pills - always on top.
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .background(Color(0xE0FFFBF2), RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(Coral, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.app_name), fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF201E1D))
                }
                Text(
                    when {
                        state.allowlistMode -> stringResource(R.string.home_allowed_count, state.allowedApps.size)
                        state.blockedApps.isEmpty() -> stringResource(R.string.home_pick_blocked)
                        else -> stringResource(R.string.home_blocked_count, state.blockedApps.size)
                    },
                    color = Color(0xFF576440),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xE0FFFBF2), RoundedCornerShape(999.dp))
                        .clickable(onClick = onPickApps)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }

            // Vault + spiraling logos - always the centerpiece.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                VaultSealScene(
                    active = active,
                    blockedPackages = blockedPkgs,
                    vaultSize = 176.dp,
                    reduceMotion = state.reduceMotion,
                )
            }

            // Bottom copy + actions.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (active) R.string.home_state_locked else R.string.home_state_open),
                    fontFamily = MiriamLibre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (active) {
                        stringResource(R.string.home_locked_body) + "\n" + vaultTimeLabel(context, state.lockEndAt(now), now)
                    } else {
                        stringResource(R.string.home_open_body)
                    },
                    color = subColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )

                if (!active) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        durations.forEach { d ->
                            val selected = chosen == d
                            Text(
                                stringResource(d.labelRes),
                                color = if (selected) Color(0xFF201E1D) else titleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .background(
                                        if (selected) Color(0xEBFFFBF2) else Color(0x47FFFBF2),
                                        RoundedCornerShape(999.dp),
                                    )
                                    .clickable { chosen = d }
                                    .padding(horizontal = 15.dp, vertical = 9.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Coral, RoundedCornerShape(999.dp))
                            .clickable { tryLock() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.home_lock_now),
                            color = Color(0xFFFFFBF2),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    UnlockPanel(
                        state = state,
                        now = now,
                        titleColor = titleColor,
                        subColor = subColor,
                        onKeys = onKeys,
                    )
                }
            }
        }

        // What's new (once per version) + tips under the vault.
        if (shouldShowWhatsNew(state.lastSeenVersionCode)) {
            Spacer(Modifier.height(14.dp))
            WhatsNewCard(Modifier.fillMaxWidth())
        } else if (!state.tipsSeen) {
            Spacer(Modifier.height(14.dp))
            TipsCarousel(Modifier.fillMaxWidth())
        }


        Spacer(Modifier.height(16.dp))

        // Focus presets: tap apply, long-press edit times/days.
        Text(
            stringResource(R.string.home_presets_title),
            style = MaterialTheme.typography.titleMedium,
            color = Cream,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.home_presets_hint),
            color = CreamFaint,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fun applyOrWarn(id: String, okMsgRes: Int) {
                if (state.lockedNow()) UiBus.say(app.atzor.R.string.sched_locked_no_change)
                else {
                    Store.applyFocusPreset(id)
                    UiBus.say(okMsgRes)
                }
            }
            fun editPreset(id: String, title: String) {
                if (state.lockedNow()) {
                    UiBus.say(app.atzor.R.string.sched_locked_no_change)
                    return
                }
                if (id == "shabbat") {
                    UiBus.say(app.atzor.R.string.sched_shabbat_via_toggle)
                    return
                }
                Store.focusPresetDefaults(id)?.let { presetEdit = title to it }
            }
            FocusChip(stringResource(R.string.home_preset_work), stringResource(R.string.home_preset_work_time),
                onClick = { applyOrWarn("work", app.atzor.R.string.home_preset_work_added) },
                onLongClick = { editPreset("work", context.getString(R.string.home_preset_work)) },
            )
            FocusChip(stringResource(R.string.home_preset_sleep), stringResource(R.string.home_preset_sleep_time),
                onClick = { applyOrWarn("sleep", app.atzor.R.string.home_preset_sleep_added) },
                onLongClick = { editPreset("sleep", context.getString(R.string.home_preset_sleep)) },
            )
            FocusChip(stringResource(R.string.home_preset_shabbat), stringResource(R.string.home_preset_shabbat_time),
                onClick = { applyOrWarn("shabbat", app.atzor.R.string.home_preset_shabbat_added) },
                onLongClick = { editPreset("shabbat", context.getString(R.string.home_preset_shabbat)) },
            )
            FocusChip(stringResource(R.string.home_preset_family), stringResource(R.string.home_preset_family_time),
                onClick = { applyOrWarn("family", app.atzor.R.string.home_preset_family_added) },
                onLongClick = { editPreset("family", context.getString(R.string.home_preset_family)) },
            )
        }

        Spacer(Modifier.height(20.dp))

        // Settings list.
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(26.dp), spotColor = Color(0x332E2B25))
                .background(CardBg, RoundedCornerShape(26.dp)),
        ) {
            SettingsRow(
                iconBg = Accent100,
                icon = { BanIcon(CoralDeep) },
                title = stringResource(R.string.home_row_apps),
                subtitle = when {
                    state.allowlistMode -> stringResource(R.string.home_row_apps_allowlist, state.allowedApps.size)
                    state.blockedApps.isEmpty() -> stringResource(R.string.home_row_apps_empty)
                    else -> stringResource(R.string.home_row_apps_count, state.blockedApps.size)
                },
                onClick = onPickApps,
            )
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Line))
            SettingsRow(
                iconBg = A2_100,
                icon = { CalendarIcon(A2_800) },
                title = stringResource(R.string.home_row_schedule),
                subtitle = run {
                    val enabled = state.schedules.count { it.enabled }
                    when {
                        state.shabbatMode && enabled > 0 -> stringResource(R.string.home_row_schedule_shabbat_and, enabled)
                        state.shabbatMode -> stringResource(R.string.home_row_schedule_shabbat)
                        enabled > 0 -> stringResource(R.string.home_row_schedule_ranges, enabled)
                        else -> stringResource(R.string.home_row_schedule_empty)
                    }
                },
                onClick = onSchedule,
            )
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Line))
            SettingsRow(
                iconBg = A2_100,
                icon = { NfcWaves(A2_800) },
                title = stringResource(R.string.home_row_keys),
                subtitle = buildString {
                    val parts = mutableListOf<String>()
                    if (state.nfcTagIds.isNotEmpty()) parts.add(context.getString(R.string.home_row_keys_tags, state.nfcTagIds.size))
                    if (state.qrSecret != null) parts.add(context.getString(R.string.home_row_keys_qr))
                    when {
                        state.btLockEnabled && state.btLockAddresses.isNotEmpty() ->
                            parts.add("${state.btLockAddresses.size} Bluetooth")
                        state.btLockEnabled -> parts.add("Bluetooth")
                    }
                    if (parts.isEmpty()) append(context.getString(R.string.home_row_keys_empty))
                    else append(parts.joinToString(" · "))
                },
                onClick = onKeys,
            )
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Line))
            SettingsRow(
                iconBg = A2_100,
                icon = { GearIcon(A2_800) },
                title = stringResource(R.string.home_row_settings),
                subtitle = stringResource(R.string.home_row_settings_sub),
                onClick = onSettings,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Badges
        if (state.badges.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(22.dp))
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.home_badges_title), style = MaterialTheme.typography.titleMedium, color = Cream)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val badgeNames = listOf(
                        Store.Badge.STREAK_3 to stringResource(R.string.badge_streak_3),
                        Store.Badge.STREAK_7 to stringResource(R.string.badge_streak_7),
                        Store.Badge.STREAK_30 to stringResource(R.string.badge_streak_30),
                        Store.Badge.FIRST_LOCK to stringResource(R.string.badge_first_lock),
                        Store.Badge.FIRST_KEY to stringResource(R.string.badge_first_key),
                        Store.Badge.FIRST_BT to stringResource(R.string.badge_first_bt),
                    )
                    badgeNames.forEach { (id, label) ->
                        if (id in state.badges) BadgeChip(label)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Streak + weekly report.
        run {
            val today = java.time.LocalDate.now().toEpochDay()
            val openMs = if (state.lockMarkAt > 0L) (minOf(now, state.lockMarkUntil) - state.lockMarkAt).coerceAtLeast(0L) else 0L
            val todayMs = (if (state.lockedDayEpoch == today) state.lockedTodayMs else 0L) + openMs
            val totalMs = state.lockedTotalMs + openMs
            val todayCount = if (state.attemptsDayEpoch == today) state.attemptsToday else 0L
            val weekDays = state.daysLockedThisWeek(today)
            val weekMin = state.weekLockedMinutes(today)
            val reportText = buildString {
                appendLine(context.getString(R.string.stats_share_header))
                appendLine(context.getString(R.string.stats_share_streak, state.streakDays))
                appendLine(context.getString(R.string.stats_share_days, weekDays))
                appendLine(context.getString(R.string.stats_share_week, weekMin))
                appendLine(context.getString(R.string.stats_share_today, fmtDuration(context, todayMs)))
                append(context.getString(R.string.home_signoff))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(A2_800, RoundedCornerShape(26.dp))
                    .padding(22.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.streakDays}",
                            fontFamily = MiriamLibre,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color(0xFFF9F4ED),
                        )
                        Text(stringResource(R.string.stats_streak_days), color = A2_200, fontSize = 14.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$weekDays/7",
                            fontFamily = MiriamLibre,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color(0xFFF9F4ED),
                        )
                        Text(stringResource(R.string.stats_days_locked), color = A2_200, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(fmtDuration(context, todayMs), fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFF9F4ED))
                        Text(stringResource(R.string.stats_today), color = A2_200, fontSize = 13.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.stats_minutes, weekMin), fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFF9F4ED))
                        Text(stringResource(R.string.stats_week), color = A2_200, fontSize = 13.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(fmtDuration(context, totalMs), fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFF9F4ED))
                        Text(stringResource(R.string.stats_total), color = A2_200, fontSize = 13.sp)
                    }
                }
                if (todayCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.stats_attempts, todayCount), color = A2_400, fontSize = 12.sp)
                }
                val yesterdayCount = state.attemptsYesterday
                if (yesterdayCount > 0 && todayCount <= yesterdayCount) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (todayCount < yesterdayCount) stringResource(R.string.stats_attempts_better, yesterdayCount)
                        else stringResource(R.string.stats_attempts_same),
                        color = Color(0xFFDD9A5C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0x33F9F4ED), RoundedCornerShape(999.dp))
                        .clickable {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reportText)
                            }
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.stats_share_chooser)))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.stats_share), color = Color(0xFFF9F4ED), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.home_signoff), color = CreamFaint, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * Clear unlock instructions - the founder should never wonder how to open.
 * Three cases: timed session (tap open), key-only (NFC/QR), schedule (key or emergency).
 */
@Composable
private fun UnlockPanel(
    state: app.atzor.data.AtzorState,
    now: Long,
    titleColor: Color,
    subColor: Color,
    onKeys: () -> Unit,
) {
    val canTapUnlock = state.manualActive && !state.keyOnly
    val needsKey = state.keyOnly || (!state.manualActive && state.lockedNow(now))

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            canTapUnlock -> {
                Text(
                    stringResource(R.string.unlock_ready),
                    color = subColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color(0xE0FFFBF2), RoundedCornerShape(999.dp))
                        .clickable { Store.unlockNow() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.unlock_button), color = Color(0xFF201E1D), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            needsKey -> {
                Text(
                    if (state.hasKey)
                        stringResource(R.string.unlock_hint_has_key)
                    else
                        stringResource(R.string.unlock_hint_no_key),
                    color = subColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.qrSecret != null) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .background(Color(0xE0FFFBF2), RoundedCornerShape(999.dp))
                                .clickable { UiBus.launchQrScan() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.unlock_scan_qr), color = Color(0xFF201E1D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Color(0x47FFFBF2), RoundedCornerShape(999.dp))
                            .clickable(onClick = onKeys),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(if (state.hasKey) R.string.unlock_keys_existing else R.string.unlock_keys_add),
                            color = titleColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
                if (state.nfcTagIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.unlock_nfc_hint),
                        color = subColor.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Always available while locked, and deliberately a gesture rather
        // than a tap: a key-only lock has no other exit if the tag is lost.
        HoldToUnlock(
            accent = Coral,
            trackColor = Coral.copy(alpha = 0.12f),
            textColor = subColor,
            onUnlock = { Store.emergencyUnlock() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusChip(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Column(
        Modifier
            .background(CardBg, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(subtitle, color = CreamSoft, fontSize = 12.sp)
    }
}

@Composable
private fun BadgeChip(label: String) {
    Text(
        label,
        color = Color(0xFF3D472B),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Color(0xFFE1EECC), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    iconBg: Color,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Cream, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = CreamSoft, style = MaterialTheme.typography.bodyMedium)
        }
        // Chevron that works in both directions: use a simple › (mirrors via RTL layout).
        Text("›", color = CreamFaint, fontSize = 22.sp)
    }
}

@Composable
fun NfcWaves(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx())
        for (i in 0..2) {
            val r = size.minDimension * (0.22f + 0.20f * i)
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(size.width / 2f - r, size.height / 2f - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = stroke,
            )
        }
    }
}

@Composable
private fun BanIcon(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx())
        val r = size.minDimension / 2f - stroke.width / 2f
        drawCircle(color = color, radius = r, style = stroke)
        val d = r * 0.707f
        drawLine(
            color = color,
            start = Offset(size.width / 2f - d, size.height / 2f - d),
            end = Offset(size.width / 2f + d, size.height / 2f + d),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
private fun GearIcon(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx())
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.28f
        drawCircle(color = color, radius = r, center = c, style = stroke)
        drawCircle(color = color, radius = r * 0.35f, center = c)
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 - 90.0)
            val x1 = c.x + (kotlin.math.cos(a) * r * 1.15).toFloat()
            val y1 = c.y + (kotlin.math.sin(a) * r * 1.15).toFloat()
            val x2 = c.x + (kotlin.math.cos(a) * r * 1.55).toFloat()
            val y2 = c.y + (kotlin.math.sin(a) * r * 1.55).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke.width)
        }
    }
}

@Composable
private fun CalendarIcon(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.78f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.16f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.08f, size.height * 0.42f),
            end = Offset(size.width * 0.92f, size.height * 0.42f),
            strokeWidth = stroke.width,
        )
    }
}

private fun fmtDuration(context: android.content.Context, ms: Long): String {
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> context.getString(R.string.dur_hours_minutes, h, m)
        else -> context.getString(R.string.dur_minutes, m)
    }
}
