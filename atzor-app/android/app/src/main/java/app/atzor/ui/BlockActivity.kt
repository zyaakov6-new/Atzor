package app.atzor.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.data.Store
import app.atzor.ui.theme.A2_100
import app.atzor.ui.theme.A2_200
import app.atzor.ui.theme.A2_300
import app.atzor.ui.theme.A2_400
import app.atzor.ui.theme.A2_700
import app.atzor.ui.theme.A2_800
import app.atzor.ui.theme.A2_900
import app.atzor.ui.theme.AtzorTheme
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamFaint
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.MiriamLibre
import app.atzor.ui.theme.Night
import app.atzor.ui.theme.OnAccent
import app.atzor.ui.theme.Sun
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PKG = "blocked_pkg"
        const val EXTRA_REASON = "reason"
        const val REASON_PROTECTION = "protection"
    }

    // The shown screen is driven by state, never by the intent captured at onCreate.
    private val blockedPkgState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val protectionState = androidx.compose.runtime.mutableStateOf(false)

    private fun applyIntent(i: Intent?) {
        blockedPkgState.value = i?.getStringExtra(EXTRA_BLOCKED_PKG)
        protectionState.value = i?.getStringExtra(EXTRA_REASON) == REASON_PROTECTION
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        applyIntent(intent)

        setContent {
            AtzorTheme {
                val blockedPkg = blockedPkgState.value
                val protection = protectionState.value
                val store by Store.state.collectAsState()
                val appLabel = remember(blockedPkg) {
                    blockedPkg?.let {
                        try {
                            packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                }

                if (store.gentleMode && blockedPkg != null && !protection) {
                    app.atzor.ui.GentleScreenContent(
                        pkg = blockedPkg,
                        appLabel = appLabel ?: blockedPkg,
                        onEnterAnyway = {
                            Store.grantGentlePass(blockedPkg)
                            packageManager.getLaunchIntentForPackage(blockedPkg)?.let { launch ->
                                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            finish()
                        },
                        onHome = ::goHome,
                    )
                } else {
                    app.atzor.ui.BlockScreenContent(
                        appLabel = appLabel,
                        protection = protection,
                        onHome = ::goHome,
                        onFinished = { finish() },
                    )
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
