package app.atzor.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.atzor.data.Store
import app.atzor.service.BlockerService
import app.atzor.ui.UiBus
import app.atzor.ui.theme.Night

enum class Screen { Home, Apps, Keys, Schedule, Settings }

fun isBlockerServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
               it.resolveInfo.serviceInfo.name == BlockerService::class.java.name }
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun AtzorRoot() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var serviceEnabled by remember { mutableStateOf(isBlockerServiceEnabled(context)) }
    val snackbar = remember { SnackbarHostState() }
    val message by UiBus.message.collectAsState()
    val state by Store.state.collectAsState()

    // Re-check the accessibility service each time we return from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isBlockerServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Resolved here (not in the effect) because getString needs the Context.
    val messageText = message?.let { m -> context.getString(m.resId, *m.args.toTypedArray()) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            UiBus.message.value = null
        }
    }

    // Vault feedback: seal on lock start, open pattern on unlock.
    var wasLocked by remember { mutableStateOf(state.lockedNow()) }
    LaunchedEffect(state.sessionEndAt, state.schedules, state.shabbatMode, state.oneOffLocks, state.scheduleOverrideUntil) {
        val locked = state.lockedNow()
        if (locked && !wasLocked) {
            app.atzor.util.VaultFeedback.playSeal(context.applicationContext)
        } else if (!locked && wasLocked) {
            app.atzor.util.VaultFeedback.playOpen(context.applicationContext)
        }
        wasLocked = locked
    }

    // System back returns to Home from inner screens instead of exiting the app.
    BackHandler(enabled = screen != Screen.Home) {
        screen = Screen.Home
    }

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when {
            !state.onboarded || !serviceEnabled -> OnboardingScreen(
                modifier = mod,
                serviceEnabled = serviceEnabled,
                onOpenSettings = { openAccessibilitySettings(context) },
                onDone = { Store.setOnboarded() },
            )
            !state.quickStartSeen -> QuickStartScreen(modifier = mod, onDone = {})
            !state.tapInstructionSeen -> KeysScreen(
                modifier = mod,
                firstRun = true,
                onBack = {
                    UiBus.nfcRegisterMode.value = false
                    Store.setTapInstructionSeen()
                },
            )
            screen == Screen.Home -> HomeScreen(
                modifier = mod,
                onPickApps = { screen = Screen.Apps },
                onKeys = { screen = Screen.Keys },
                onSchedule = { screen = Screen.Schedule },
                onSettings = { screen = Screen.Settings },
            )
            screen == Screen.Apps -> AppsScreen(modifier = mod, onBack = { screen = Screen.Home })
            screen == Screen.Schedule -> ScheduleScreen(modifier = mod, onBack = { screen = Screen.Home })
            screen == Screen.Settings -> SettingsScreen(modifier = mod, onBack = { screen = Screen.Home })
            else -> KeysScreen(modifier = mod, onBack = { screen = Screen.Home })
        }
    }
}
