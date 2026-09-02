package app.atzor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Line

private data class Tip(@StringRes val title: Int, @StringRes val body: Int)

private val tips = listOf(
    Tip(R.string.tip_open_title, R.string.tip_open_body),
    Tip(R.string.tip_shabbat_title, R.string.tip_shabbat_body),
    Tip(R.string.tip_bt_title, R.string.tip_bt_body),
    Tip(R.string.tip_emergency_title, R.string.tip_emergency_body),
)

@Composable
fun TipsCarousel(modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    val tip = tips[index.coerceIn(0, tips.lastIndex)]

    Column(
        modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.tips_title), style = MaterialTheme.typography.titleMedium, color = Cream)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(tip.title), color = Coral, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(tip.body), color = CreamSoft, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (index < tips.lastIndex) R.string.tips_next else R.string.tips_got_it),
                color = Coral,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        if (index < tips.lastIndex) index++
                        else Store.setTipsSeen()
                    }
                    .padding(8.dp),
            )
            Text(
                "${index + 1}/${tips.size}",
                color = CreamSoft,
                fontSize = 13.sp,
            )
            Text(
                stringResource(R.string.tips_close),
                color = CreamSoft,
                modifier = Modifier
                    .clickable { Store.setTipsSeen() }
                    .padding(8.dp),
            )
        }
    }
}
