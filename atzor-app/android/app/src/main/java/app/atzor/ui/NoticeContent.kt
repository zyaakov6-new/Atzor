package app.atzor.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The banner shown when a screen was bounced rather than blocked. It answers
 * what happened, that the rest of Settings still works, and where the way out
 * is, then disappears on its own. Calm on purpose: the user probably did not
 * mean to end up here.
 */
@Composable
fun NoticeContent(@StringRes messageRes: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(messageRes),
            color = Color(0xFFF5EAD8),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color(0xF2272E1B), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x33F5EAD8), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
