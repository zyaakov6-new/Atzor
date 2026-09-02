package app.atzor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import app.atzor.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.LeafDeep
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.CardBg

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    serviceEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(Coral, CircleShape),
            )
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Cream)
        }

        Spacer(Modifier.height(34.dp))
        Text(
            stringResource(R.string.onb_title),
            style = MaterialTheme.typography.displayMedium,
            color = Cream,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.onb_body),
            style = MaterialTheme.typography.bodyLarge,
            color = CreamSoft,
        )

        Spacer(Modifier.height(26.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(26.dp))
                .border(1.dp, Line, RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepLine("1", stringResource(R.string.onb_step_1))
            StepLine("2", stringResource(R.string.onb_step_2))
            StepLine("3", stringResource(R.string.onb_step_3))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onb_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = CreamSoft,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(28.dp))
        if (!serviceEnabled) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.onb_open_settings), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.onb_continue), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StepLine(num: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .background(app.atzor.ui.theme.A2_100, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(num, color = app.atzor.ui.theme.A2_800, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Cream)
    }
}
