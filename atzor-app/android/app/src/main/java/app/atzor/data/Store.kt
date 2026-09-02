package app.atzor.data

import android.content.Context
import android.os.SystemClock
import app.atzor.Analytics
import app.atzor.R
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "atzor")

/**
 * A recurring lock window. Days use Calendar.DAY_OF_WEEK (1=Sunday .. 7=Saturday).
 * startMin/endMin are minutes since midnight; start > end means it crosses midnight.
 */
data class Schedule(
    val days: Set<Int>,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean = true,
)

fun encodeSchedules(list: List<Schedule>): String =
    list.joinToString(";") { s ->
        s.days.sorted().joinToString("") + "|" + s.startMin + "|" + s.endMin + "|" + (if (s.enabled) "1" else "0")
    }

fun decodeSchedules(raw: String): List<Schedule> =
    raw.split(";").filter { it.isNotBlank() }.mapNotNull { entry ->
        runCatching {
            val p = entry.split("|")
            Schedule(
                days = p[0].map { it.digitToInt() }.toSet(),
                startMin = p[1].toInt(),
                endMin = p[2].toInt(),
                enabled = p[3] == "1",
            )
        }.getOrNull()
    }

/**
 * A one-off lock tied to a specific calendar date (not recurring), for
 * occasions the user knows the exact date of: ערב פסח, ראש השנה, a family
 * event. dateEpochDay is java.time.LocalDate.toEpochDay() for the start day;
 * start > end crosses into the next day, same convention as Schedule.
 */
data class OneOffLock(
    val dateEpochDay: Long,
    val startMin: Int,
    val endMin: Int,
    val label: String,
    val enabled: Boolean = true,
)

fun encodeOneOffLocks(list: List<OneOffLock>): String =
    list.joinToString(";") { l ->
        // Label may not contain the separators used here.
        val safeLabel = l.label.replace("|", " ").replace(";", " ")
        "${l.dateEpochDay}|${l.startMin}|${l.endMin}|${if (l.enabled) "1" else "0"}|$safeLabel"
    }

fun decodeOneOffLocks(raw: String): List<OneOffLock> =
    raw.split(";").filter { it.isNotBlank() }.mapNotNull { entry ->
        runCatching {
            val p = entry.split("|", limit = 5)
            OneOffLock(
                dateEpochDay = p[0].toLong(),
                startMin = p[1].toInt(),
                endMin = p[2].toInt(),
                enabled = p[3] == "1",
                label = p.getOrElse(4) { "" },
            )
        }.getOrNull()
    }

fun encodeGentleOverrides(map: Map<String, Boolean>): String =
    map.entries.joinToString(";") { (pkg, gentle) -> "$pkg|${if (gentle) "1" else "0"}" }

fun decodeGentleOverrides(raw: String): Map<String, Boolean> =
    raw.split(";").filter { it.isNotBlank() }.mapNotNull { entry ->
        runCatching {
            val p = entry.split("|")
            p[0] to (p[1] == "1")
        }.getOrNull()
    }.toMap()

/** Everything the blocker needs to decide, cached in memory for the services. */
data class AtzorState(
    val blockedApps: Set<String> = emptySet(),
    /** Allowlist mode: block every launchable app EXCEPT allowedApps (+ safety set). */
    val allowlistMode: Boolean = false,
    val allowedApps: Set<String> = emptySet(),
    /** DEFAULT_ALLOWED safety packages (navigation/messaging) the user chose to
     *  block anyway for a stricter lock. Has no effect on HARD tier packages. */
    val safetyOverridesBlocked: Set<String> = emptySet(),
    /** 0 = no manual session. Long.MAX_VALUE = locked until a key (NFC/QR) opens it. */
    val sessionEndAt: Long = 0L,
    /**
     * elapsedRealtime() when the manual session started (0 = none). Monotonic,
     * so it cannot be moved by changing the device date. Also our reboot
     * detector: elapsedRealtime only ever increases within a single boot, so
     * a smaller value now means the device restarted.
     */
    val sessionStartElapsed: Long = 0L,
    /** elapsedRealtime() deadline for the manual session (0 = none or key-only). */
    val sessionEndElapsed: Long = 0L,
    val schedules: List<Schedule> = emptyList(),
    val oneOffLocks: List<OneOffLock> = emptyList(),
    /** A key/emergency unlock during a scheduled window suppresses schedules until this time. */
    val scheduleOverrideUntil: Long = 0L,
    val nfcTagIds: Set<String> = emptySet(),
    val qrSecret: String? = null,
    val onboarded: Boolean = false,
    /** The one-time "pick a few common apps and lock immediately" screen has been shown. */
    val quickStartSeen: Boolean = false,
    /** The skippable first-run NFC tap step after Quick Start has been shown. */
    val tapInstructionSeen: Boolean = false,
    /**
     * While locked: guard the accessibility screen, app info, and the package
     * installer, so עצור cannot be switched off or removed mid-session. The
     * rest of Settings stays usable. Opt-in: a first-time user should never
     * land in this without choosing it.
     */
    val strictMode: Boolean = false,
    /** Auto-lock from candle lighting to havdalah, computed from Jerusalem sunset times. */
    val shabbatMode: Boolean = false,
    /** Soft blocking: a 10s breathing pause with a choice, instead of a hard bounce. */
    val gentleMode: Boolean = false,
    /** Per-app override of gentleMode: true = always gentle, false = always hard, absent = follow gentleMode. */
    val appGentleOverrides: Map<String, Boolean> = emptyMap(),
    val gentlePassPkg: String? = null,
    val gentlePassUntil: Long = 0L,
    val lastDurationMs: Long = 60L * 60L * 1000L,
    val attemptsTotal: Long = 0L,
    val attemptsToday: Long = 0L,
    val attemptsYesterday: Long = 0L,
    val attemptsDayEpoch: Long = 0L,
    /** Lock-time bookkeeping: when the open accounting mark started (0 = unlocked at mark). */
    val lockMarkAt: Long = 0L,
    /** The lock end known at mark time, so a lock that expired quietly is not over-counted. */
    val lockMarkUntil: Long = 0L,
    val lockedTotalMs: Long = 0L,
    val lockedTodayMs: Long = 0L,
    val lockedYesterdayMs: Long = 0L,
    val lockedDayEpoch: Long = 0L,
    /** Consecutive calendar days with any lock time (updated when a day gets credit). */
    val streakDays: Int = 0,
    /** Last epoch-day that counted toward [streakDays]. */
    val lastStreakDay: Long = 0L,
    /** Last ~14 days of lock time: epochDay → ms. */
    val lockHistory: Map<Long, Long> = emptyMap(),
    /** Optional seal haptic when the vault closes. */
    val hapticsEnabled: Boolean = true,
    /** Optional short seal tone when the vault closes. */
    val soundEnabled: Boolean = true,
    /**
     * Bluetooth device addresses (MAC) that auto-lock on connect.
     * Only these devices - never “any Bluetooth”.
     */
    val btLockAddresses: Set<String> = emptySet(),
    /** Human labels parallel to addresses are stored separately as "addr|name;..." */
    val btLockLabels: Map<String, String> = emptyMap(),
    /** Duration used when a watched Bluetooth device connects. null = key-only. */
    val btLockDurationMs: Long = 60L * 60L * 1000L,
    val btLockEnabled: Boolean = false,
    /** When a watched BT device disconnects, unlock (only if this is on). */
    val btUnlockOnDisconnect: Boolean = false,
    /** NFC tag id → friendly name. */
    val nfcTagLabels: Map<String, String> = emptyMap(),
    /** Key id ("nfc:…", "qr", "bt:…") → last used epoch ms. */
    val keyLastUsed: Map<String, Long> = emptyMap(),
    /** Earned milestone badge ids (local only). */
    val badges: Set<String> = emptySet(),
    /** Prefer less vault motion (also combined with system reduce-motion). */
    val reduceMotion: Boolean = false,
    /** User dismissed the tips carousel. */
    val tipsSeen: Boolean = false,
    /** Last app versionCode for which "what's new" was dismissed (0 = never). */
    val lastSeenVersionCode: Int = 0,
    /** When the currently-active lock began (epoch ms). In-memory only, for
     *  analytics duration bucketing - not persisted, resets on process death. */
    val activeLockStartedAt: Long = 0L,
) {
    val manualActive: Boolean get() = sessionRemainingMs() > 0L
    val keyOnly: Boolean get() = sessionEndAt == Long.MAX_VALUE
    val hasKey: Boolean get() = nfcTagIds.isNotEmpty() || qrSecret != null

    /** End (epoch millis) of the schedule occurrence active right now, or null. */
    fun scheduleOccurrenceEnd(now: Long = System.currentTimeMillis()): Long? {
        if (scheduleOverrideUntil > now) return null
        val shabbatEnd = if (shabbatMode) app.atzor.util.SunTimes.activeShabbatEnd(now) else null
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val min = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val yesterday = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
        val today = LocalDate.now().toEpochDay()

        var best: Long? = shabbatEnd
        for (s in schedules) {
            if (!s.enabled) continue
            val end: Long? = if (s.startMin <= s.endMin) {
                if (day in s.days && min >= s.startMin && min < s.endMin) endAt(cal, s.endMin, 0) else null
            } else {
                when {
                    day in s.days && min >= s.startMin -> endAt(cal, s.endMin, 1)
                    yesterday in s.days && min < s.endMin -> endAt(cal, s.endMin, 0)
                    else -> null
                }
            }
            if (end != null && (best == null || end > best)) best = end
        }
        for (l in oneOffLocks) {
            if (!l.enabled) continue
            val end: Long? = if (l.startMin <= l.endMin) {
                if (l.dateEpochDay == today && min >= l.startMin && min < l.endMin) endAt(cal, l.endMin, 0) else null
            } else {
                when {
                    l.dateEpochDay == today && min >= l.startMin -> endAt(cal, l.endMin, 1)
                    l.dateEpochDay == today - 1 && min < l.endMin -> endAt(cal, l.endMin, 0)
                    else -> null
                }
            }
            if (end != null && (best == null || end > best)) best = end
        }
        return best
    }

    private fun endAt(base: Calendar, endMin: Int, plusDays: Int): Long =
        (base.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, plusDays)
            set(Calendar.HOUR_OF_DAY, endMin / 60)
            set(Calendar.MINUTE, endMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * True when the device restarted since the session began. elapsedRealtime()
     * restarts from zero on boot, so a value below the recorded start is a
     * reboot, not tampering, and must not be treated as one.
     */
    fun rebootedSinceSession(nowElapsed: Long): Boolean =
        sessionStartElapsed > 0L && nowElapsed < sessionStartElapsed

    /**
     * Milliseconds left on the manual session. Long.MAX_VALUE for a key-only
     * lock, 0 when there is no session or it has run out.
     *
     * Within a single boot the monotonic deadline wins, so moving the device
     * clock cannot shorten the lock (nor lengthen it, by moving it backwards).
     * After a reboot elapsedRealtime has restarted and the wall clock is the
     * only reference left, so the lock survives on wall time.
     */
    fun sessionRemainingMs(
        nowWall: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime(),
    ): Long = when {
        sessionEndAt == Long.MAX_VALUE -> Long.MAX_VALUE
        sessionEndAt <= 0L -> 0L
        sessionEndElapsed > 0L && !rebootedSinceSession(nowElapsed) ->
            (sessionEndElapsed - nowElapsed).coerceAtLeast(0L)
        else -> (sessionEndAt - nowWall).coerceAtLeast(0L)
    }

    /** The single source of truth: is anything locking the phone right now? */
    fun lockedNow(
        now: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime(),
    ): Boolean = sessionRemainingMs(now, nowElapsed) > 0L || scheduleOccurrenceEnd(now) != null

    /**
     * When the current lock ends, as a wall-clock instant for display. The
     * manual part is projected from the monotonic remainder, so the countdown
     * stays truthful even if the device clock was moved.
     * Long.MAX_VALUE for key-only manual locks.
     */
    fun lockEndAt(
        now: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime(),
    ): Long = when {
        keyOnly -> Long.MAX_VALUE
        else -> {
            val remaining = sessionRemainingMs(now, nowElapsed)
            maxOf(
                if (remaining > 0L) now + remaining else 0L,
                scheduleOccurrenceEnd(now) ?: 0L,
            )
        }
    }

    /** Is this package blocked under the current mode? (Callers pre-filter launchability.) */
    fun isAppBlocked(pkg: String, launchable: Boolean): Boolean =
        if (allowlistMode) launchable && pkg !in allowedApps
        else pkg in blockedApps

    /**
     * Whether [pkg] is blocked once safety tiers are combined with the mode's
     * own block set. [hardExempt]/[defaultAllowed] should already include
     * anything resolved dynamically (the device's actual default dialer / SMS
     * app), so an OEM not in the static SafetyPackages list is still covered.
     * HARD packages can never be overridden; DEFAULT_ALLOWED ones are open
     * unless the user explicitly added them to [safetyOverridesBlocked], in
     * which case they are blocked regardless of allowlist/blocklist mode.
     */
    fun isEffectivelyBlocked(
        pkg: String,
        launchable: Boolean,
        hardExempt: Set<String>,
        defaultAllowed: Set<String>,
    ): Boolean {
        if (pkg in hardExempt) return false
        val overridden = pkg in defaultAllowed && pkg in safetyOverridesBlocked
        if (pkg in defaultAllowed && !overridden) return false
        if (overridden) return true
        return isAppBlocked(pkg, launchable)
    }

    val blockSetEmpty: Boolean get() = if (allowlistMode) false else blockedApps.isEmpty()

    /** Whether pkg should be shown the breathing pause instead of a hard bounce. */
    fun isGentleFor(pkg: String): Boolean = appGentleOverrides[pkg] ?: gentleMode

    /** Lock-time ms credited for a calendar day (includes open mark for today). */
    fun lockedMsForDay(day: Long, now: Long = System.currentTimeMillis()): Long {
        val today = LocalDate.now().toEpochDay()
        val hist = lockHistory[day] ?: 0L
        return when {
            day == today -> {
                val openMs = if (lockMarkAt > 0L) {
                    (minOf(now, lockMarkUntil) - lockMarkAt).coerceAtLeast(0L)
                } else 0L
                maxOf(hist, lockedTodayMs) + openMs
            }
            day == today - 1 && lockedDayEpoch == today -> maxOf(hist, lockedYesterdayMs)
            else -> hist
        }
    }

    /** Days in the last 7 that had any lock time (for weekly report). */
    fun daysLockedThisWeek(today: Long = LocalDate.now().toEpochDay()): Int =
        (today - 6..today).count { lockedMsForDay(it) > 0L }

    fun weekLockedMinutes(today: Long = LocalDate.now().toEpochDay()): Long =
        (today - 6..today).sumOf { lockedMsForDay(it) } / 60_000L
}

fun encodeLockHistory(map: Map<Long, Long>): String =
    map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

fun decodeLockHistory(raw: String): Map<Long, Long> =
    raw.split(";").filter { it.isNotBlank() }.mapNotNull { entry ->
        runCatching {
            val p = entry.split(":")
            p[0].toLong() to p[1].toLong()
        }.getOrNull()
    }.toMap()

fun encodeStringLongMap(map: Map<String, Long>): String =
    map.entries.joinToString(";") { (k, v) ->
        "${k.replace(";", " ").replace("=", " ")}=$v"
    }

fun decodeStringLongMap(raw: String): Map<String, Long> =
    raw.split(";").filter { it.isNotBlank() }.mapNotNull { entry ->
        runCatching {
            val p = entry.split("=", limit = 2)
            p[0] to p[1].toLong()
        }.getOrNull()
    }.toMap()

fun encodeBtLabels(map: Map<String, String>): String =
    map.entries.joinToString(";") { (a, n) ->
        "$a|${n.replace("|", " ").replace(";", " ")}"
    }

fun decodeBtLabels(raw: String): Map<String, String> =
    raw.split(";").filter { it.isNotBlank() }.mapNotNull { entry ->
        runCatching {
            val p = entry.split("|", limit = 2)
            p[0] to p.getOrElse(1) { p[0] }
        }.getOrNull()
    }.toMap()

/** Keep only the last [keep] days of history. */
private fun pruneHistory(map: Map<Long, Long>, today: Long, keep: Long = 14L): Map<Long, Long> =
    map.filterKeys { it >= today - keep }

object Store {
    private val KEY_BLOCKED = stringSetPreferencesKey("blocked_apps")
    private val KEY_ALLOWLIST_MODE = booleanPreferencesKey("allowlist_mode")
    private val KEY_ALLOWED = stringSetPreferencesKey("allowed_apps")
    private val KEY_SAFETY_OVERRIDES = stringSetPreferencesKey("safety_overrides_blocked")
    private val KEY_END_AT = longPreferencesKey("session_end_at")
    private val KEY_SESSION_START_ELAPSED = longPreferencesKey("session_start_elapsed")
    private val KEY_SESSION_END_ELAPSED = longPreferencesKey("session_end_elapsed")
    private val KEY_SCHEDULES = stringPreferencesKey("schedules")
    private val KEY_ONE_OFF = stringPreferencesKey("one_off_locks")
    private val KEY_SCHED_OVERRIDE = longPreferencesKey("schedule_override_until")
    private val KEY_TAG = stringPreferencesKey("nfc_tag_id")
    private val KEY_TAGS = stringSetPreferencesKey("nfc_tag_ids")
    private val KEY_QR = stringPreferencesKey("qr_secret")
    private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
    private val KEY_QUICK_START_SEEN = booleanPreferencesKey("quick_start_seen")
    private val KEY_TAP_INSTRUCTION_SEEN = booleanPreferencesKey("tap_instruction_seen")
    private val KEY_STRICT = booleanPreferencesKey("strict_mode")
    private val KEY_SHABBAT = booleanPreferencesKey("shabbat_mode")
    private val KEY_GENTLE = booleanPreferencesKey("gentle_mode")
    private val KEY_GENTLE_OVERRIDES = stringPreferencesKey("gentle_overrides")
    private val KEY_GENTLE_PKG = stringPreferencesKey("gentle_pass_pkg")
    private val KEY_GENTLE_UNTIL = longPreferencesKey("gentle_pass_until")
    private val KEY_LAST_DURATION = longPreferencesKey("last_duration_ms")
    private val KEY_ATT_TOTAL = longPreferencesKey("attempts_total")
    private val KEY_ATT_TODAY = longPreferencesKey("attempts_today")
    private val KEY_ATT_YESTERDAY = longPreferencesKey("attempts_yesterday")
    private val KEY_ATT_DAY = longPreferencesKey("attempts_day_epoch")
    private val KEY_LOCK_MARK = longPreferencesKey("lock_mark_at")
    private val KEY_LOCK_MARK_UNTIL = longPreferencesKey("lock_mark_until")
    private val KEY_LOCKED_TOTAL = longPreferencesKey("locked_total_ms")
    private val KEY_LOCKED_TODAY = longPreferencesKey("locked_today_ms")
    private val KEY_LOCKED_YESTERDAY = longPreferencesKey("locked_yesterday_ms")
    private val KEY_LOCKED_DAY = longPreferencesKey("locked_day_epoch")
    private val KEY_STREAK = longPreferencesKey("streak_days")
    private val KEY_STREAK_DAY = longPreferencesKey("last_streak_day")
    private val KEY_LOCK_HISTORY = stringPreferencesKey("lock_history")
    private val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
    private val KEY_SOUND = booleanPreferencesKey("sound_enabled")
    private val KEY_BT_ADDRS = stringSetPreferencesKey("bt_lock_addresses")
    private val KEY_BT_LABELS = stringPreferencesKey("bt_lock_labels")
    private val KEY_BT_DURATION = longPreferencesKey("bt_lock_duration_ms")
    private val KEY_BT_ENABLED = booleanPreferencesKey("bt_lock_enabled")
    private val KEY_BT_UNLOCK_DISC = booleanPreferencesKey("bt_unlock_on_disconnect")
    private val KEY_NFC_LABELS = stringPreferencesKey("nfc_tag_labels")
    private val KEY_KEY_LAST = stringPreferencesKey("key_last_used")
    private val KEY_BADGES = stringSetPreferencesKey("badges")
    private val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    private val KEY_TIPS_SEEN = booleanPreferencesKey("tips_seen")
    private val KEY_LAST_SEEN_VC = longPreferencesKey("last_seen_version_code")

    /** How long the emergency unlock gesture must be held, in ms. */
    const val EMERGENCY_HOLD_MS = 5_000L

    object Badge {
        const val STREAK_3 = "streak_3"
        const val STREAK_7 = "streak_7"
        const val STREAK_30 = "streak_30"
        const val FIRST_LOCK = "first_lock"
        const val FIRST_KEY = "first_key_unlock"
        const val FIRST_BT = "first_bt_lock"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    private val _state = MutableStateFlow(AtzorState())
    val state: StateFlow<AtzorState> = _state

    /** Called from Application.onCreate; blocking read keeps service decisions correct from the first event. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = runBlocking { appContext.dataStore.data.first() }
        _state.value = AtzorState(
            blockedApps = prefs[KEY_BLOCKED] ?: emptySet(),
            allowlistMode = prefs[KEY_ALLOWLIST_MODE] ?: false,
            allowedApps = prefs[KEY_ALLOWED] ?: emptySet(),
            safetyOverridesBlocked = prefs[KEY_SAFETY_OVERRIDES] ?: emptySet(),
            sessionEndAt = prefs[KEY_END_AT] ?: 0L,
            sessionStartElapsed = prefs[KEY_SESSION_START_ELAPSED] ?: 0L,
            sessionEndElapsed = prefs[KEY_SESSION_END_ELAPSED] ?: 0L,
            schedules = decodeSchedules(prefs[KEY_SCHEDULES] ?: ""),
            oneOffLocks = decodeOneOffLocks(prefs[KEY_ONE_OFF] ?: ""),
            scheduleOverrideUntil = prefs[KEY_SCHED_OVERRIDE] ?: 0L,
            // Migrate the old single-tag field into the set on first read.
            nfcTagIds = prefs[KEY_TAGS] ?: prefs[KEY_TAG]?.let { setOf(it) } ?: emptySet(),
            qrSecret = prefs[KEY_QR],
            onboarded = prefs[KEY_ONBOARDED] ?: false,
            quickStartSeen = prefs[KEY_QUICK_START_SEEN] ?: false,
            // Already-paired installs skip the new first-run tap step.
            tapInstructionSeen = prefs[KEY_TAP_INSTRUCTION_SEEN]
                ?: (prefs[KEY_TAGS] ?: prefs[KEY_TAG]?.let { setOf(it) } ?: emptySet()).isNotEmpty(),
            // Off for new installs only. Anyone who already ran the app has an
            // explicit value stored (update() writes every key), so existing
            // users keep whatever they had.
            strictMode = prefs[KEY_STRICT] ?: false,
            shabbatMode = prefs[KEY_SHABBAT] ?: false,
            gentleMode = prefs[KEY_GENTLE] ?: false,
            appGentleOverrides = decodeGentleOverrides(prefs[KEY_GENTLE_OVERRIDES] ?: ""),
            gentlePassPkg = prefs[KEY_GENTLE_PKG],
            gentlePassUntil = prefs[KEY_GENTLE_UNTIL] ?: 0L,
            lastDurationMs = prefs[KEY_LAST_DURATION] ?: 60L * 60L * 1000L,
            attemptsTotal = prefs[KEY_ATT_TOTAL] ?: 0L,
            attemptsToday = prefs[KEY_ATT_TODAY] ?: 0L,
            attemptsYesterday = prefs[KEY_ATT_YESTERDAY] ?: 0L,
            attemptsDayEpoch = prefs[KEY_ATT_DAY] ?: 0L,
            lockMarkAt = prefs[KEY_LOCK_MARK] ?: 0L,
            lockMarkUntil = prefs[KEY_LOCK_MARK_UNTIL] ?: 0L,
            lockedTotalMs = prefs[KEY_LOCKED_TOTAL] ?: 0L,
            lockedTodayMs = prefs[KEY_LOCKED_TODAY] ?: 0L,
            lockedYesterdayMs = prefs[KEY_LOCKED_YESTERDAY] ?: 0L,
            lockedDayEpoch = prefs[KEY_LOCKED_DAY] ?: 0L,
            streakDays = (prefs[KEY_STREAK] ?: 0L).toInt(),
            lastStreakDay = prefs[KEY_STREAK_DAY] ?: 0L,
            lockHistory = decodeLockHistory(prefs[KEY_LOCK_HISTORY] ?: ""),
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            soundEnabled = prefs[KEY_SOUND] ?: true,
            btLockAddresses = prefs[KEY_BT_ADDRS] ?: emptySet(),
            btLockLabels = decodeBtLabels(prefs[KEY_BT_LABELS] ?: ""),
            btLockDurationMs = prefs[KEY_BT_DURATION] ?: 60L * 60L * 1000L,
            btLockEnabled = prefs[KEY_BT_ENABLED] ?: false,
            btUnlockOnDisconnect = prefs[KEY_BT_UNLOCK_DISC] ?: false,
            nfcTagLabels = decodeBtLabels(prefs[KEY_NFC_LABELS] ?: ""),
            keyLastUsed = decodeStringLongMap(prefs[KEY_KEY_LAST] ?: ""),
            badges = prefs[KEY_BADGES] ?: emptySet(),
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
            tipsSeen = prefs[KEY_TIPS_SEEN] ?: false,
            lastSeenVersionCode = (prefs[KEY_LAST_SEEN_VC] ?: 0L).toInt(),
        )
        // If the process starts already locked (crash/reboot mid-session), we
        // don't know the true start time; approximate as now rather than leave
        // it at 0, so the first lock_ended duration bucket isn't nonsense.
        if (_state.value.lockedNow()) {
            _state.value = _state.value.copy(activeLockStartedAt = System.currentTimeMillis())
        }
        // Close out any accounting left open across a process death or reboot.
        touchLockClock()
    }

    /**
     * Settle the lock-time books up to now. Deltas are capped at the lock end
     * that was known when the mark opened, so a schedule that expired while the
     * phone sat idle is credited exactly, not over-counted. On a day rollover,
     * the day's final total is preserved as "yesterday" for the trend comparison.
     */
    private fun settleClock(st: AtzorState): AtzorState {
        val now = System.currentTimeMillis()
        var s = st
        if (st.lockMarkAt > 0L) {
            val effectiveEnd = minOf(now, st.lockMarkUntil)
            val delta = (effectiveEnd - st.lockMarkAt).coerceAtLeast(0L)
            if (delta > 0L) {
                val today = LocalDate.now().toEpochDay()
                if (st.lockedDayEpoch == today) {
                    val todayMs = st.lockedTodayMs + delta
                    s = s.copy(
                        lockedTotalMs = st.lockedTotalMs + delta,
                        lockedTodayMs = todayMs,
                        lockHistory = pruneHistory(
                            s.lockHistory + (today to todayMs),
                            today,
                        ),
                    )
                    s = applyStreak(s, today)
                } else {
                    val hist = s.lockHistory.toMutableMap()
                    if (st.lockedDayEpoch != 0L) hist[st.lockedDayEpoch] = st.lockedTodayMs
                    hist[today] = delta
                    s = s.copy(
                        lockedTotalMs = st.lockedTotalMs + delta,
                        lockedYesterdayMs = if (st.lockedDayEpoch != 0L) st.lockedTodayMs else st.lockedYesterdayMs,
                        lockedTodayMs = delta,
                        lockedDayEpoch = today,
                        lockHistory = pruneHistory(hist, today),
                    )
                    s = applyStreak(s, today)
                }
            }
        }
        val locked = s.lockedNow(now)
        return s.copy(
            lockMarkAt = if (locked) now else 0L,
            lockMarkUntil = if (locked) s.lockEndAt(now) else 0L,
        )
    }

    /** Count a calendar day toward the streak once it has any credited lock time. */
    private fun applyStreak(s: AtzorState, day: Long): AtzorState {
        if (s.lastStreakDay == day) return s
        val next = if (s.lastStreakDay == day - 1L) s.streakDays + 1 else 1
        return withBadges(s.copy(streakDays = next, lastStreakDay = day))
    }

    private fun withBadges(s: AtzorState): AtzorState {
        var b = s.badges
        if (s.streakDays >= 3) b = b + Badge.STREAK_3
        if (s.streakDays >= 7) b = b + Badge.STREAK_7
        if (s.streakDays >= 30) b = b + Badge.STREAK_30
        return if (b === s.badges || b == s.badges) s else s.copy(badges = b)
    }

    private fun award(s: AtzorState, id: String): AtzorState =
        if (id in s.badges) s else s.copy(badges = s.badges + id)

    private fun touchKey(s: AtzorState, keyId: String): AtzorState =
        s.copy(keyLastUsed = s.keyLastUsed + (keyId to System.currentTimeMillis()))

    /** Public tick so services/UI can keep the books current without changing anything else. */
    fun touchLockClock() = update { it }

    /**
     * Detects lock start/end transitions that were NOT caused by one of the
     * explicit functions below (those log their own event, with the exact
     * method/reason they know firsthand). sessionEndAt / scheduleOverrideUntil
     * are only ever written by the explicit start/unlock functions, so if
     * neither changed, this transition was purely time-driven: a schedule
     * window opening/closing, or a manual timer reaching its own end.
     */
    private fun logPassiveLockTransition(prev: AtzorState, next: AtzorState, now: Long): AtzorState {
        val wasLocked = prev.lockedNow()
        val isLocked = next.lockedNow(now)
        val explicit = next.sessionEndAt != prev.sessionEndAt ||
            next.scheduleOverrideUntil != prev.scheduleOverrideUntil
        return when {
            isLocked && !wasLocked -> {
                if (!explicit) {
                    val end = next.lockEndAt(now)
                    Analytics.logLockStarted("schedule", if (end == Long.MAX_VALUE) null else end - now)
                }
                next.copy(activeLockStartedAt = now)
            }
            !isLocked && wasLocked -> {
                if (!explicit && prev.activeLockStartedAt > 0L) {
                    Analytics.logLockEnded("timer_expired", (now - prev.activeLockStartedAt).coerceAtLeast(0L))
                }
                next.copy(activeLockStartedAt = 0L)
            }
            else -> next
        }
    }

    private fun update(transform: (AtzorState) -> AtzorState) {
        val prev = _state.value
        val now = System.currentTimeMillis()
        val next = logPassiveLockTransition(prev, settleClock(transform(settleClock(prev))), now)
        _state.value = next
        scope.launch {
            appContext.dataStore.edit { prefs ->
                prefs[KEY_BLOCKED] = next.blockedApps
                prefs[KEY_ALLOWLIST_MODE] = next.allowlistMode
                prefs[KEY_ALLOWED] = next.allowedApps
                prefs[KEY_SAFETY_OVERRIDES] = next.safetyOverridesBlocked
                prefs[KEY_END_AT] = next.sessionEndAt
                prefs[KEY_SESSION_START_ELAPSED] = next.sessionStartElapsed
                prefs[KEY_SESSION_END_ELAPSED] = next.sessionEndElapsed
                prefs[KEY_SCHEDULES] = encodeSchedules(next.schedules)
                prefs[KEY_ONE_OFF] = encodeOneOffLocks(next.oneOffLocks)
                prefs[KEY_SCHED_OVERRIDE] = next.scheduleOverrideUntil
                prefs[KEY_TAGS] = next.nfcTagIds
                prefs.remove(KEY_TAG)
                next.qrSecret?.let { prefs[KEY_QR] = it } ?: prefs.remove(KEY_QR)
                prefs[KEY_ONBOARDED] = next.onboarded
                prefs[KEY_QUICK_START_SEEN] = next.quickStartSeen
                prefs[KEY_TAP_INSTRUCTION_SEEN] = next.tapInstructionSeen
                prefs[KEY_STRICT] = next.strictMode
                prefs[KEY_SHABBAT] = next.shabbatMode
                prefs[KEY_GENTLE] = next.gentleMode
                prefs[KEY_GENTLE_OVERRIDES] = encodeGentleOverrides(next.appGentleOverrides)
                next.gentlePassPkg?.let { prefs[KEY_GENTLE_PKG] = it } ?: prefs.remove(KEY_GENTLE_PKG)
                prefs[KEY_GENTLE_UNTIL] = next.gentlePassUntil
                prefs[KEY_LAST_DURATION] = next.lastDurationMs
                prefs[KEY_ATT_TOTAL] = next.attemptsTotal
                prefs[KEY_ATT_TODAY] = next.attemptsToday
                prefs[KEY_ATT_YESTERDAY] = next.attemptsYesterday
                prefs[KEY_ATT_DAY] = next.attemptsDayEpoch
                prefs[KEY_LOCK_MARK] = next.lockMarkAt
                prefs[KEY_LOCK_MARK_UNTIL] = next.lockMarkUntil
                prefs[KEY_LOCKED_TOTAL] = next.lockedTotalMs
                prefs[KEY_LOCKED_TODAY] = next.lockedTodayMs
                prefs[KEY_LOCKED_YESTERDAY] = next.lockedYesterdayMs
                prefs[KEY_LOCKED_DAY] = next.lockedDayEpoch
                prefs[KEY_STREAK] = next.streakDays.toLong()
                prefs[KEY_STREAK_DAY] = next.lastStreakDay
                prefs[KEY_LOCK_HISTORY] = encodeLockHistory(next.lockHistory)
                prefs[KEY_HAPTICS] = next.hapticsEnabled
                prefs[KEY_SOUND] = next.soundEnabled
                prefs[KEY_BT_ADDRS] = next.btLockAddresses
                prefs[KEY_BT_LABELS] = encodeBtLabels(next.btLockLabels)
                prefs[KEY_BT_DURATION] = next.btLockDurationMs
                prefs[KEY_BT_ENABLED] = next.btLockEnabled
                prefs[KEY_BT_UNLOCK_DISC] = next.btUnlockOnDisconnect
                prefs[KEY_NFC_LABELS] = encodeBtLabels(next.nfcTagLabels)
                prefs[KEY_KEY_LAST] = encodeStringLongMap(next.keyLastUsed)
                prefs[KEY_BADGES] = next.badges
                prefs[KEY_REDUCE_MOTION] = next.reduceMotion
                prefs[KEY_TIPS_SEEN] = next.tipsSeen
                prefs[KEY_LAST_SEEN_VC] = next.lastSeenVersionCode.toLong()
            }
        }
    }

    // ── blocked / allowed apps ──

    fun toggleApp(pkg: String) = update {
        if (it.allowlistMode) {
            val next = it.allowedApps.toMutableSet()
            if (!next.remove(pkg)) next.add(pkg)
            it.copy(allowedApps = next)
        } else {
            val next = it.blockedApps.toMutableSet()
            if (!next.remove(pkg)) next.add(pkg)
            it.copy(blockedApps = next)
        }
    }

    /** Bulk-set the blocked list; used by the quick-start screen. */
    fun setBlockedApps(pkgs: Set<String>) = update {
        Analytics.logBlocklistConfigured("blocklist", pkgs.size)
        it.copy(blockedApps = pkgs)
    }

    /** Tier-2 safety apps (navigation/messaging) are open by default; this lets
     *  the user explicitly block one anyway for a stricter lock. */
    fun setSafetyOverride(pkg: String, blocked: Boolean) = update { st ->
        val next = st.safetyOverridesBlocked.toMutableSet()
        if (blocked) next.add(pkg) else next.remove(pkg)
        st.copy(safetyOverridesBlocked = next)
    }

    /** Second tap on a quick-pick category: take the whole category back off. */
    fun removeBlockedApps(pkgs: Set<String>) = update {
        it.copy(blockedApps = it.blockedApps - pkgs)
    }

    /** Empties whichever list the current mode is actually using. */
    fun clearSelection() = update {
        if (it.allowlistMode) {
            Analytics.logBlocklistConfigured("allowlist", 0)
            it.copy(allowedApps = emptySet())
        } else {
            Analytics.logBlocklistConfigured("blocklist", 0)
            it.copy(blockedApps = emptySet())
        }
    }

    fun addBlockedApps(pkgs: Set<String>) = update {
        it.copy(blockedApps = it.blockedApps + pkgs)
    }

    fun setAllowlistMode(on: Boolean) = update { it.copy(allowlistMode = on) }

    // ── sessions ──

    /** [method] identifies the UI surface that triggered the lock, for analytics only. */
    fun startSession(durationMs: Long?, method: String) {
        Analytics.logLockStarted(method, durationMs)
        val startElapsed = SystemClock.elapsedRealtime()
        update {
            var s = it.copy(
                sessionEndAt = if (durationMs == null) Long.MAX_VALUE else System.currentTimeMillis() + durationMs,
                sessionStartElapsed = startElapsed,
                sessionEndElapsed = if (durationMs == null) 0L else startElapsed + durationMs,
                lastDurationMs = durationMs ?: it.lastDurationMs,
            )
            s = award(s, Badge.FIRST_LOCK)
            s
        }
    }

    /** Lock triggered by a watched Bluetooth device connecting. */
    fun startSessionFromBluetooth(durationMs: Long?) {
        Analytics.logLockStarted("bluetooth", durationMs)
        val startElapsed = SystemClock.elapsedRealtime()
        update {
            var s = it.copy(
                sessionEndAt = if (durationMs == null) Long.MAX_VALUE else System.currentTimeMillis() + durationMs,
                sessionStartElapsed = startElapsed,
                sessionEndElapsed = if (durationMs == null) 0L else startElapsed + durationMs,
                lastDurationMs = durationMs ?: it.lastDurationMs,
            )
            s = award(s, Badge.FIRST_LOCK)
            s = award(s, Badge.FIRST_BT)
            s
        }
    }

    /**
     * A user-sanctioned unlock (key, matured emergency, or manual end of a timed
     * session). Ends the manual session and, if a scheduled window is active,
     * suppresses it for the remainder of this occurrence.
     */
    fun unlockNow(reason: String = "manual") = update { st ->
        val now = System.currentTimeMillis()
        if (st.activeLockStartedAt > 0L) {
            Analytics.logLockEnded(reason, (now - st.activeLockStartedAt).coerceAtLeast(0L))
        }
        val occ = st.scheduleOccurrenceEnd()
        st.copy(
            sessionEndAt = 0L,
            sessionStartElapsed = 0L,
            sessionEndElapsed = 0L,
            scheduleOverrideUntil = occ ?: st.scheduleOverrideUntil,
        )
    }

    // ── schedules ──

    fun setSchedules(list: List<Schedule>) = update { it.copy(schedules = list) }

    fun setOneOffLocks(list: List<OneOffLock>) = update { it.copy(oneOffLocks = list) }

    // ── keys ──

    fun registerNfcTag(tagId: String, label: String? = null) = update {
        if (tagId !in it.nfcTagIds) Analytics.logKeyRegistered("nfc")
        val labels = it.nfcTagLabels.toMutableMap()
        if (label != null) labels[tagId] = label
        else if (tagId !in labels) {
            labels[tagId] = appContext.getString(R.string.key_tag_default_label, it.nfcTagIds.size + 1)
        }
        it.copy(nfcTagIds = it.nfcTagIds + tagId, nfcTagLabels = labels)
    }

    fun setNfcTagLabel(tagId: String, label: String) = update {
        it.copy(
            nfcTagLabels = it.nfcTagLabels +
                (tagId to label.ifBlank { appContext.getString(R.string.key_tag_fallback_label) }),
        )
    }

    fun clearNfcTags() = update { it.copy(nfcTagIds = emptySet(), nfcTagLabels = emptyMap()) }

    fun ensureQrSecret(): String {
        _state.value.qrSecret?.let { return it }
        val secret = "atzor:" + UUID.randomUUID().toString()
        Analytics.logKeyRegistered("qr")
        update { it.copy(qrSecret = secret) }
        return secret
    }

    fun clearQrSecret() = update { it.copy(qrSecret = null) }

    /** A key (NFC tag or QR) was presented: toggle the lock. Returns true if it matched. */
    fun keyPresented(value: String): Boolean {
        val s = _state.value
        val isNfc = value in s.nfcTagIds
        val isQr = value == s.qrSecret
        if (!isNfc && !isQr) return false
        val keyId = if (isNfc) "nfc:$value" else "qr"
        val wasLocked = s.lockedNow()
        update { st ->
            var next = touchKey(st, keyId)
            if (wasLocked) {
                if (st.activeLockStartedAt > 0L) {
                    Analytics.logLockEnded(
                        "key",
                        (System.currentTimeMillis() - st.activeLockStartedAt).coerceAtLeast(0L),
                    )
                }
                val occ = next.scheduleOccurrenceEnd()
                next = next.copy(
                    sessionEndAt = 0L,
                    sessionStartElapsed = 0L,
                    sessionEndElapsed = 0L,
                    scheduleOverrideUntil = occ ?: next.scheduleOverrideUntil,
                )
                next = award(next, Badge.FIRST_KEY)
            } else {
                Analytics.logLockStarted(if (isNfc) "nfc" else "qr", null)
                next = next.copy(
                    sessionEndAt = Long.MAX_VALUE,
                    sessionStartElapsed = SystemClock.elapsedRealtime(),
                    sessionEndElapsed = 0L,
                    lastDurationMs = next.lastDurationMs,
                )
                next = award(next, Badge.FIRST_LOCK)
            }
            next
        }
        return true
    }

    fun recordKeyUsed(keyId: String) = update { touchKey(it, keyId) }

    // ── emergency ──

    /**
     * The user started the emergency unlock gesture. Paired with
     * [emergencyUnlock] this still answers "how often does someone reach for
     * the escape hatch versus actually go through with it", which is the
     * number the friction is tuned against.
     */
    fun emergencyHoldStarted() = Analytics.logEmergencyRequested()

    /**
     * The gesture was held all the way through. There is no waiting period any
     * more: the friction is the deliberate hold itself, which is impossible to
     * trigger by accident but instant when someone genuinely needs out.
     */
    fun emergencyUnlock() {
        Analytics.logEmergencyCompleted()
        unlockNow(reason = "emergency")
    }

    // ── misc ──

    fun setOnboarded() = update {
        Analytics.logOnboardingCompleted(stepReached = 1)
        it.copy(onboarded = true)
    }

    fun setQuickStartSeen() = update {
        Analytics.logOnboardingCompleted(stepReached = 2)
        it.copy(quickStartSeen = true)
    }

    fun setTapInstructionSeen() = update {
        it.copy(tapInstructionSeen = true)
    }

    fun setStrictMode(on: Boolean) = update { it.copy(strictMode = on) }

    fun setShabbatMode(on: Boolean) = update { it.copy(shabbatMode = on) }

    fun setGentleMode(on: Boolean) = update { it.copy(gentleMode = on) }

    fun setHapticsEnabled(on: Boolean) = update { it.copy(hapticsEnabled = on) }

    fun setSoundEnabled(on: Boolean) = update { it.copy(soundEnabled = on) }

    fun setBtLockEnabled(on: Boolean) = update { it.copy(btLockEnabled = on) }

    fun setBtLockDurationMs(ms: Long) = update { it.copy(btLockDurationMs = ms) }

    fun setBtUnlockOnDisconnect(on: Boolean) = update { it.copy(btUnlockOnDisconnect = on) }

    fun setBtLockDevice(address: String, label: String, enabled: Boolean) = update {
        val addrs = it.btLockAddresses.toMutableSet()
        val labels = it.btLockLabels.toMutableMap()
        if (enabled) {
            addrs.add(address)
            labels[address] = label.ifBlank { address }
        } else {
            addrs.remove(address)
            labels.remove(address)
        }
        it.copy(btLockAddresses = addrs, btLockLabels = labels)
    }

    fun setReduceMotion(on: Boolean) = update { it.copy(reduceMotion = on) }

    fun setTipsSeen() = update { it.copy(tipsSeen = true) }

    fun setLastSeenVersionCode(code: Int) = update { it.copy(lastSeenVersionCode = code) }

    fun focusPresetDefaults(id: String): Schedule? = when (id) {
        "work" -> Schedule(days = (1..5).toSet(), startMin = 9 * 60, endMin = 17 * 60, enabled = true)
        "sleep" -> Schedule(days = (1..7).toSet(), startMin = 22 * 60, endMin = 7 * 60, enabled = true)
        "family" -> Schedule(days = (1..7).toSet(), startMin = 16 * 60, endMin = 20 * 60, enabled = true)
        else -> null
    }

    /**
     * Apply a focus preset: installs a schedule and/or Shabbat mode.
     * Does not replace the blocked-app list (user already manages that).
     */
    fun applyFocusPreset(id: String) {
        if (id == "shabbat") {
            update { it.copy(shabbatMode = true) }
            return
        }
        focusPresetDefaults(id)?.let { addSchedule(it) }
    }

    /** Add a schedule (used by long-press editor on presets). */
    fun addSchedule(schedule: Schedule) = update {
        it.copy(schedules = listOf(schedule) + it.schedules)
    }

    /** null clears the override, going back to following the global gentleMode. */
    fun setAppGentleOverride(pkg: String, gentle: Boolean?) = update {
        val next = it.appGentleOverrides.toMutableMap()
        if (gentle == null) next.remove(pkg) else next[pkg] = gentle
        it.copy(appGentleOverrides = next)
    }

    /** "Enter anyway" from the breathing pause: 5 minutes of access, then the pause returns. */
    fun grantGentlePass(pkg: String) = update {
        Analytics.logGentlePassUsed()
        it.copy(gentlePassPkg = pkg, gentlePassUntil = System.currentTimeMillis() + 5L * 60L * 1000L)
    }

    /** One blocked-app bounce = one attempt, with daily rollover (yesterday's total preserved for the trend line). */
    fun recordAttempt() = update {
        val today = LocalDate.now().toEpochDay()
        if (it.attemptsDayEpoch == today) {
            it.copy(attemptsTotal = it.attemptsTotal + 1, attemptsToday = it.attemptsToday + 1)
        } else {
            it.copy(
                attemptsTotal = it.attemptsTotal + 1,
                attemptsYesterday = if (it.attemptsDayEpoch != 0L) it.attemptsToday else it.attemptsYesterday,
                attemptsToday = 1L,
                attemptsDayEpoch = today,
            )
        }
    }
}
