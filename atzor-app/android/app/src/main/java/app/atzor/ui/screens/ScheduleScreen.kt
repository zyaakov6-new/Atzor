package app.atzor.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.data.OneOffLock
import app.atzor.data.Schedule
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamFaint
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Leaf
import app.atzor.ui.theme.LeafDeep
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.CardBg

// Calendar.DAY_OF_WEEK order: 1=ראשון .. 7=שבת


private fun fmt(min: Int) = "%d:%02d".format(min / 60, min % 60)

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val state by Store.state.collectAsState()
    val context = LocalContext.current

    // Draft for a new schedule.
    var draftDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var draftStart by remember { mutableStateOf(21 * 60) }
    var draftEnd by remember { mutableStateOf(23 * 60) }

    // Draft for a new one-off occasion.
    var oneOffDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(1).toEpochDay()) }
    var oneOffLabel by remember { mutableStateOf("") }
    var oneOffStart by remember { mutableStateOf(18 * 60) }
    var oneOffEnd by remember { mutableStateOf(21 * 60) }

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
            Text(stringResource(R.string.sched_title), style = MaterialTheme.typography.headlineMedium, color = Cream)
            Text(
                stringResource(R.string.sched_back),
                style = MaterialTheme.typography.labelLarge,
                color = CoralDeep,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sched_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = CreamSoft,
        )

        Spacer(Modifier.height(20.dp))

        // Shabbat mode card.
        run {
            val window = remember { app.atzor.util.SunTimes.shabbatWindow(System.currentTimeMillis()) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(26.dp))
                    .border(1.dp, if (state.shabbatMode) LeafDeep.copy(alpha = 0.45f) else Line, RoundedCornerShape(26.dp))
                    .padding(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sched_shabbat_title), style = MaterialTheme.typography.titleMedium, color = Cream)
                        Text(
                            stringResource(R.string.sched_shabbat_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CreamSoft,
                            fontSize = 13.sp,
                        )
                        window?.let { (start, end) ->
                            Spacer(Modifier.height(4.dp))
                            val zone = java.time.ZoneId.systemDefault()
                            val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                            Text(
                                stringResource(
                                    R.string.sched_shabbat_next,
                                    java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalTime().format(f),
                                    java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalTime().format(f),
                                ),
                                color = CoralDeep,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    app.atzor.ui.AtzorToggle(checked = state.shabbatMode) { on ->
                            if (state.lockedNow() && !on) {
                                app.atzor.ui.UiBus.say(app.atzor.R.string.sched_shabbat_locked)
                            } else {
                                Store.setShabbatMode(on)
                            }
                        }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ─── Special occasions: one-off, date-specific locks. Same family as
        // Shabbat mode - the identity feature no generic English blocker has. ───
        Text(stringResource(R.string.sched_oneoff_title), style = MaterialTheme.typography.titleMedium, color = Cream)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sched_oneoff_body),
            style = MaterialTheme.typography.bodyMedium,
            color = CreamSoft,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        val today = java.time.LocalDate.now().toEpochDay()
        state.oneOffLocks
            .withIndex()
            .filter { (_, l) -> l.dateEpochDay >= today }
            .sortedBy { (_, l) -> l.dateEpochDay }
            .forEach { (index, l) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(26.dp))
                        .border(1.dp, if (l.enabled) LeafDeep.copy(alpha = 0.4f) else Line, RoundedCornerShape(26.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        val date = java.time.LocalDate.ofEpochDay(l.dateEpochDay)
                        val dateLabel = "%02d/%02d".format(date.dayOfMonth, date.monthValue)
                        Text(
                            if (l.label.isNotBlank()) l.label else dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = Cream,
                        )
                        Text(
                            "$dateLabel · ${fmt(l.startMin)}-${fmt(l.endMin)}" +
                                if (l.startMin > l.endMin) " " + stringResource(R.string.sched_crosses_midnight) else "",
                            color = CreamSoft,
                            fontSize = 13.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        app.atzor.ui.AtzorToggle(checked = l.enabled) { on ->
                            Store.setOneOffLocks(
                                state.oneOffLocks.toMutableList().also { it[index] = l.copy(enabled = on) },
                            )
                        }
                        Text(
                            "✕",
                            color = CreamFaint,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clickable {
                                    Store.setOneOffLocks(state.oneOffLocks.toMutableList().also { it.removeAt(index) })
                                }
                                .padding(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

        // New occasion composer.
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(26.dp))
                .border(1.dp, Line, RoundedCornerShape(26.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.sched_new_date), style = MaterialTheme.typography.titleMedium, color = Cream)
            Spacer(Modifier.height(12.dp))

            OccasionLabelField(value = oneOffLabel, onValueChange = { oneOffLabel = it })

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val date = java.time.LocalDate.ofEpochDay(oneOffDate)
                TimeChip("%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)) {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> oneOffDate = java.time.LocalDate.of(y, m + 1, d).toEpochDay() },
                        date.year, date.monthValue - 1, date.dayOfMonth,
                    ).show()
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeChip(stringResource(R.string.sched_from, fmt(oneOffStart))) {
                    TimePickerDialog(context, { _, h, m -> oneOffStart = h * 60 + m }, oneOffStart / 60, oneOffStart % 60, true).show()
                }
                TimeChip(stringResource(R.string.sched_to, fmt(oneOffEnd))) {
                    TimePickerDialog(context, { _, h, m -> oneOffEnd = h * 60 + m }, oneOffEnd / 60, oneOffEnd % 60, true).show()
                }
            }
            if (oneOffStart > oneOffEnd) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.sched_ends_next_day), color = CreamFaint, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (oneOffStart != oneOffEnd) {
                        Store.setOneOffLocks(
                            state.oneOffLocks + OneOffLock(oneOffDate, oneOffStart, oneOffEnd, oneOffLabel.trim(), enabled = true),
                        )
                        oneOffLabel = ""
                    }
                },
                enabled = oneOffStart != oneOffEnd,
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.sched_add_date), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Existing schedules.
        state.schedules.forEachIndexed { index, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(26.dp))
                    .border(1.dp, if (s.enabled) LeafDeep.copy(alpha = 0.4f) else Line, RoundedCornerShape(26.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${fmt(s.startMin)}-${fmt(s.endMin)}" + if (s.startMin > s.endMin) " " + stringResource(R.string.sched_crosses_midnight) else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Cream,
                    )
                    val letters = stringArrayResource(app.atzor.R.array.day_letters)
                    Text(
                        s.days.sorted().joinToString(" ") { letters[it - 1] },
                        color = CreamSoft,
                        fontSize = 13.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    app.atzor.ui.AtzorToggle(checked = s.enabled) { on ->
                            Store.setSchedules(
                                state.schedules.toMutableList().also { it[index] = s.copy(enabled = on) },
                            )
                        }
                    Text(
                        "✕",
                        color = CreamFaint,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable {
                                Store.setSchedules(state.schedules.toMutableList().also { it.removeAt(index) })
                            }
                            .padding(8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (state.schedules.isEmpty()) {
            Text(stringResource(R.string.sched_empty), color = CreamFaint, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))

        // New schedule composer.
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(26.dp))
                .border(1.dp, Line, RoundedCornerShape(26.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.sched_new_hours), style = MaterialTheme.typography.titleMedium, color = Cream)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    val on = day in draftDays
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(if (on) Leaf else CardBg, CircleShape)
                            .border(1.dp, if (on) Leaf else Line, CircleShape)
                            .clickable {
                                draftDays = if (on) draftDays - day else draftDays + day
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringArrayResource(app.atzor.R.array.day_letters)[day - 1],
                            color = if (on) app.atzor.ui.theme.Night else CreamSoft,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeChip(stringResource(R.string.sched_from, fmt(draftStart))) {
                    TimePickerDialog(context, { _, h, m -> draftStart = h * 60 + m }, draftStart / 60, draftStart % 60, true).show()
                }
                TimeChip(stringResource(R.string.sched_to, fmt(draftEnd))) {
                    TimePickerDialog(context, { _, h, m -> draftEnd = h * 60 + m }, draftEnd / 60, draftEnd % 60, true).show()
                }
            }
            if (draftStart > draftEnd) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.sched_ends_next_day), color = CreamFaint, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (draftDays.isNotEmpty() && draftStart != draftEnd) {
                        Store.setSchedules(state.schedules + Schedule(draftDays, draftStart, draftEnd, enabled = true))
                    }
                },
                enabled = draftDays.isNotEmpty() && draftStart != draftEnd,
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.sched_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun OccasionLabelField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cream),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Coral),
        modifier = Modifier
            .fillMaxWidth()
            .background(app.atzor.ui.theme.Night, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.sched_name_hint), style = MaterialTheme.typography.bodyLarge, color = CreamFaint)
                }
                inner()
            }
        },
    )
}

@Composable
private fun TimeChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Cream,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .background(CardBg, RoundedCornerShape(999.dp))
            .border(1.dp, Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
