package app.atzor.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.LeafDeep
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.Night
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.CreamFaint
import app.atzor.ui.theme.Accent100 as AccentWash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val pkg: String, val label: String, val icon: androidx.compose.ui.graphics.ImageBitmap?)

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Night else CreamSoft,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(if (selected) Coral else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(999.dp))
            .border(1.dp, if (selected) Coral else Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun AppsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by Store.state.collectAsState()

    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                .asSequence()
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { info ->
                    InstalledApp(
                        pkg = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = runCatching {
                            pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                        }.getOrNull(),
                    )
                }
                .sortedBy { it.label }
                .toList()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineMedium, color = Cream)
            Text(
                stringResource(R.string.apps_done),
                style = MaterialTheme.typography.labelLarge,
                color = CoralDeep,
                modifier = Modifier
                    .clickable {
                        val mode = if (state.allowlistMode) "allowlist" else "blocklist"
                        val count = if (state.allowlistMode) state.allowedApps.size else state.blockedApps.size
                        app.atzor.Analytics.logBlocklistConfigured(mode, count)
                        onBack()
                    }
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // Mode chips: blacklist vs allowlist.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(stringResource(R.string.apps_mode_blocklist), selected = !state.allowlistMode) {
                if (state.lockedNow()) app.atzor.ui.UiBus.say(app.atzor.R.string.apps_locked_no_change)
                else Store.setAllowlistMode(false)
            }
            ModeChip(stringResource(R.string.apps_mode_allowlist), selected = state.allowlistMode) {
                if (state.lockedNow()) app.atzor.ui.UiBus.say(app.atzor.R.string.apps_locked_no_change)
                else Store.setAllowlistMode(true)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                if (state.allowlistMode) R.string.apps_mode_allowlist_hint
                else R.string.apps_mode_blocklist_hint,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = CreamSoft,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.apps_gentle_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = CreamFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        // Category quick-block (blacklist mode only).
        if (!state.allowlistMode && apps != null) {
            Text(stringResource(R.string.apps_quick_pick), color = Cream, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val installed = apps!!.map { it.pkg }.toSet()
                app.atzor.data.AppCategories.all.forEach { cat ->
                    val hit = cat.packages.intersect(installed)
                    if (hit.isEmpty()) return@forEach
                    // Tapping a category that is already fully selected takes it
                    // back off, so the chip is a toggle rather than a one-way add.
                    val allPicked = hit.all { it in state.blockedApps }
                    Text(
                        "${stringResource(cat.titleRes)} (${hit.size})",
                        color = if (allPicked) Night else Cream,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (allPicked) Coral else CardBg,
                                RoundedCornerShape(999.dp),
                            )
                            .border(1.dp, if (allPicked) Coral else Line, RoundedCornerShape(999.dp))
                            .clickable {
                                when {
                                    state.lockedNow() ->
                                        app.atzor.ui.UiBus.say(app.atzor.R.string.apps_locked_no_list_change)
                                    allPicked -> {
                                        Store.removeBlockedApps(hit)
                                        app.atzor.ui.UiBus.say(
                                            app.atzor.R.string.apps_quick_removed,
                                            hit.size,
                                            context.getString(cat.titleRes),
                                        )
                                    }
                                    else -> {
                                        Store.addBlockedApps(hit)
                                        app.atzor.ui.UiBus.say(
                                            app.atzor.R.string.apps_quick_added,
                                            hit.size,
                                            context.getString(cat.titleRes),
                                        )
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Always-allowed safety list (trust).
        var showSafety by remember { mutableStateOf(false) }
        Text(
            stringResource(if (showSafety) R.string.apps_safety_hide else R.string.apps_safety_show),
            color = CoralDeep,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.clickable { showSafety = !showSafety }.padding(vertical = 4.dp),
        )
        if (showSafety) {
            val installedPkgs = apps?.map { it.pkg }?.toSet().orEmpty()
            val hardTier = app.atzor.data.SafetyPackages.entries
                .filter { it.tier == app.atzor.data.SafetyPackages.Tier.HARD }
                .filter { it.pkg in installedPkgs || it.pkg in app.atzor.data.SafetyPackages.hardBlockExempt }
            val defaultAllowedTier = app.atzor.data.SafetyPackages.entries
                .filter { it.tier == app.atzor.data.SafetyPackages.Tier.DEFAULT_ALLOWED }
                .filter { it.pkg in installedPkgs || it.pkg in state.safetyOverridesBlocked }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(R.string.apps_safety_hard_title),
                    color = Cream,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.apps_safety_hard_body),
                    color = CreamSoft,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                hardTier.forEach { e -> Text("• " + stringResource(e.labelRes), color = Cream, fontSize = 13.sp) }

                if (defaultAllowedTier.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.apps_safety_default_title),
                        color = Cream,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.apps_safety_default_body),
                        color = CreamSoft,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    defaultAllowedTier.forEach { e ->
                        val overridden = e.pkg in state.safetyOverridesBlocked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (state.lockedNow()) {
                                        app.atzor.ui.UiBus.say(app.atzor.R.string.apps_locked_no_change)
                                    } else {
                                        Store.setSafetyOverride(e.pkg, !overridden)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(e.labelRes) + " (" + stringResource(e.kindRes) + ")",
                                color = Cream,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(if (overridden) R.string.apps_safety_blocked else R.string.apps_safety_open),
                                color = if (overridden) CoralDeep else LeafDeep,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Clear the whole selection. Mode aware, and only worth showing when
        // there is something to clear.
        val selectedCount = if (state.allowlistMode) state.allowedApps.size else state.blockedApps.size
        if (selectedCount > 0) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.apps_selected_count, selectedCount),
                    color = CreamFaint,
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(R.string.apps_clear_all),
                    color = CoralDeep,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            if (state.lockedNow()) {
                                app.atzor.ui.UiBus.say(app.atzor.R.string.apps_locked_no_list_change)
                            } else {
                                Store.clearSelection()
                                app.atzor.ui.UiBus.say(app.atzor.R.string.apps_cleared)
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Search.
        var query by remember { mutableStateOf("") }
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(999.dp))
                .border(1.dp, Line, RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cream),
                cursorBrush = SolidColor(Coral),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.apps_search),
                                style = MaterialTheme.typography.bodyLarge,
                                color = CreamFaint,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Text(
                    "✕",
                    color = CreamFaint,
                    modifier = Modifier.clickable { query = "" }.padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (val list = apps) {
            null -> Text(stringResource(R.string.apps_loading), color = CreamSoft)
            else -> {
                val filtered = remember(list, query) {
                    val q = query.trim()
                    if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) }
                }
                if (!state.allowlistMode && state.blockedApps.isEmpty() && query.isEmpty()) {
                    app.atzor.ui.EmptyStateCard(
                        kind = app.atzor.ui.EmptyKind.Apps,
                        title = stringResource(R.string.apps_empty_title),
                        body = stringResource(R.string.apps_empty_body),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.apps_none_match), color = CreamSoft)
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    val blocked = if (state.allowlistMode) app.pkg in state.allowedApps else app.pkg in state.blockedApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { Store.toggleApp(app.pkg) }
                            .background(
                                if (blocked) AccentWash else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let {
                            Image(it, contentDescription = null, modifier = Modifier.size(38.dp))
                        } ?: Box(Modifier.size(38.dp).background(Line, CircleShape))
                        Spacer(Modifier.size(12.dp))
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Cream,
                            modifier = Modifier.weight(1f),
                        )
                        if (blocked) {
                            val override = state.appGentleOverrides[app.pkg]
                            val (label, color) = when (override) {
                                true -> stringResource(R.string.apps_gentle_soft) to LeafDeep
                                false -> stringResource(R.string.apps_gentle_hard) to CoralDeep
                                null -> stringResource(R.string.apps_gentle_default) to CreamFaint
                            }
                            Text(
                                label,
                                color = color,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                    .clickable {
                                        // Cycle: follow global → always gentle → always hard → follow global.
                                        val next: Boolean? = when (override) {
                                            null -> true
                                            true -> false
                                            false -> null
                                        }
                                        Store.setAppGentleOverride(app.pkg, next)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(
                                    if (blocked) LeafDeep else androidx.compose.ui.graphics.Color.Transparent,
                                    CircleShape,
                                )
                                .then(
                                    if (!blocked) Modifier.background(Line, CircleShape) else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (blocked) Text("✓", color = Night)
                        }
                    }
                }
                }
            }
        }
    }
}
