package app.atzor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.atzor.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Deliberately dependency-free (no custom theme/fonts) so it cannot itself crash.
 * Shows the previous run's stack trace for screenshotting.
 */
@Composable
fun CrashReportScreen(trace: String, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF25343A))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.crash_title),
            color = Color(0xFFF2CF7B),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Screenshot everything below and send it over.",
            color = Color(0xB3F2EEE4),
            fontSize = 13.sp,
        )
        Text(
            text = trace,
            color = Color(0xFFF2EEE4),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A262B), RoundedCornerShape(10.dp))
                .padding(12.dp),
        )
        Button(
            onClick = onClear,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF2EEE4),
                contentColor = Color(0xFF25343A),
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.crash_retry), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}
