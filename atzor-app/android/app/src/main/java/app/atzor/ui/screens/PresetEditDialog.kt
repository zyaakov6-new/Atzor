package app.atzor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import app.atzor.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.data.Schedule
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Leaf
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.Night



@Composable
fun PresetEditDialog(
    title: String,
    initial: Schedule,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    var days by remember { mutableStateOf(initial.days) }
    var startMin by remember { mutableIntStateOf(initial.startMin) }
    var endMin by remember { mutableIntStateOf(initial.endMin) }

    fun fmt(m: Int) = "%d:%02d".format(m / 60, m % 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(stringResource(R.string.preset_edit_title, title), color = Cream, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.preset_days), color = CreamSoft, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day ->
                        val on = day in days
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(if (on) Leaf else Color.Transparent, CircleShape)
                                .border(1.dp, if (on) Leaf else Line, CircleShape)
                                .clickable {
                                    days = if (on) days - day else days + day
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringArrayResource(app.atzor.R.array.day_letters)[day - 1],
                                color = if (on) Night else CreamSoft,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.preset_hours), color = CreamSoft, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeAdjustChip(stringResource(R.string.sched_from, fmt(startMin))) {
                        startMin = (startMin + 30) % (24 * 60)
                    }
                    TimeAdjustChip(stringResource(R.string.sched_to, fmt(endMin))) {
                        endMin = (endMin + 30) % (24 * 60)
                    }
                }
                if (startMin == endMin) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.preset_same_time_error), color = Coral, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (days.isNotEmpty() && startMin != endMin) {
                        onSave(Schedule(days, startMin, endMin, enabled = true))
                    }
                },
                enabled = days.isNotEmpty() && startMin != endMin,
            ) {
                Text(stringResource(R.string.preset_save), color = Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.preset_cancel), color = CreamSoft)
            }
        },
    )
}

@Composable
private fun TimeAdjustChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Cream,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Night, RoundedCornerShape(999.dp))
            .border(1.dp, Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
