package app.atzor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.BuildConfig
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Line

/** Short post-update notes. Shown once per versionCode. */
@Composable
fun WhatsNewCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(
            stringResource(R.string.whatsnew_title, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.titleMedium,
            color = Cream,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        WhatsNewLine(stringResource(R.string.whatsnew_line_2))
        WhatsNewLine(stringResource(R.string.whatsnew_line_1))
        WhatsNewLine(stringResource(R.string.whatsnew_line_3))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.whatsnew_got_it),
            color = Coral,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .clickable { Store.setLastSeenVersionCode(BuildConfig.VERSION_CODE) }
                .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun WhatsNewLine(text: String) {
    Text(
        "• $text",
        color = CreamSoft,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** True when we should show the card for this install/update. */
fun shouldShowWhatsNew(lastSeenVersionCode: Int): Boolean =
    BuildConfig.VERSION_CODE > lastSeenVersionCode
