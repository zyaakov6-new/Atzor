package app.atzor.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.CoralDeep
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamFaint
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Leaf
import app.atzor.ui.theme.LeafDeep
import app.atzor.ui.theme.Line
import app.atzor.ui.theme.MiriamLibre
import app.atzor.ui.theme.Night
import app.atzor.ui.theme.OnAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class QuickApp(val pkg: String, val label: String, val icon: ImageBitmap?, val suggested: Boolean)

/** The usual first candidates; picked only if actually installed. */
private val candidatePackages = listOf(
    "com.instagram.android" to true,
    "com.zhiliaoapp.musically" to true,
    "com.ss.android.ugc.trill" to true,
    "com.google.android.youtube" to true,
    "com.facebook.katana" to false,
    "com.twitter.android" to false,
    "com.snapchat.android" to false,
)

/**
 * Shown once, right after the accessibility service is granted: pick from a
 * short, pre-checked list of the usual suspects and lock immediately, so the
 * very first thing a new user feels is the product working - not a settings
 * maze. Skipping is always one tap away.
 */
@Composable
fun QuickStartScreen(modifier: Modifier = Modifier, onDone: () -> Unit) {
    val context = LocalContext.current

    val apps by produceState<List<QuickApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            candidatePackages.mapNotNull { (pkg, suggested) ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    QuickApp(
                        pkg = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                        suggested = suggested,
                    )
                }.getOrNull()
            }
        }
    }

    var selected by remember(apps) { mutableStateOf(apps?.filter { it.suggested }?.map { it.pkg }?.toSet() ?: emptySet()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Coral, CircleShape))
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Cream)
        }

        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.qs_title), style = MaterialTheme.typography.displayMedium, color = Cream, fontFamily = MiriamLibre)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.qs_body),
            style = MaterialTheme.typography.bodyLarge,
            color = CreamSoft,
        )

        Spacer(Modifier.height(24.dp))

        when (val list = apps) {
            null -> Text(stringResource(R.string.qs_loading), color = CreamSoft)
            else -> if (list.isEmpty()) Text(
                stringResource(R.string.qs_none_found),
                color = CreamSoft,
                style = MaterialTheme.typography.bodyLarge,
            ) else Column(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(26.dp))
                    .border(1.dp, Line, RoundedCornerShape(26.dp))
                    .padding(vertical = 6.dp),
            ) {
                list.forEach { app ->
                    val checked = app.pkg in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - app.pkg else selected + app.pkg
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let {
                            Image(it, contentDescription = null, modifier = Modifier.size(36.dp))
                        } ?: Box(Modifier.size(36.dp).background(Line, CircleShape))
                        Spacer(Modifier.size(12.dp))
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(if (checked) LeafDeep else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                .then(if (!checked) Modifier.border(1.dp, Line, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) Text("✓", color = Night, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (selected.isNotEmpty()) {
                    Store.setBlockedApps(selected)
                    Store.startSession(30L * 60L * 1000L, "manual_timed")
                }
                Store.setQuickStartSeen()
                onDone()
            },
            enabled = selected.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = OnAccent),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.qs_lock_30), style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = { Store.setQuickStartSeen(); onDone() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.qs_later), color = CreamFaint, fontSize = 14.sp)
        }
    }
}
