package app.atzor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.R
import app.atzor.data.Store
import app.atzor.ui.theme.MiriamLibre
import kotlinx.coroutines.delay

/** Lock overlay: dark vault night - apps sealed behind the door. */
@Composable
fun BlockScreenContent(appLabel: String?, protection: Boolean, onHome: () -> Unit, onFinished: () -> Unit) {
    val state by Store.state.collectAsState()
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(state, now) {
        if (!state.lockedNow(now)) onFinished()
    }

    val endAt = state.lockEndAt(now)

    // Packages to suck into the vault: the app just blocked, plus a few others.
    val suckPkgs = remember(appLabel, state.blockedApps, state.allowlistMode, protection) {
        buildSet {
            if (!state.allowlistMode) addAll(state.blockedApps.take(8))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161C11), Color(0xFF232B1A), Color(0xFF2C3520)),
                ),
            ),
    ) {
        // Stars.
        Star(52.dp, 70.dp, 5.dp, 0.7f)
        Star(150.dp, 132.dp, 3.dp, 0.5f)
        Star(250.dp, 56.dp, 4.dp, 0.6f)
        Star(80.dp, 180.dp, 3.dp, 0.4f)
        Star(300.dp, 104.dp, 4.dp, 0.55f)

        // Vault door with seal animation.
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            VaultSealScene(
                active = true,
                blockedPackages = suckPkgs,
                vaultSize = 176.dp,
                reduceMotion = state.reduceMotion,
            )
        }

        HillsCanvas(
            nightHills,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(230.dp),
        )

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 36.dp).offset(y = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.lock_title),
                color = Color(0xFFF5EAD8),
                fontFamily = MiriamLibre,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    protection -> stringResource(R.string.lock_body_protection)
                    appLabel != null -> stringResource(R.string.lock_body_app, appLabel)
                    else -> stringResource(R.string.lock_body_generic)
                },
                color = Color(0xFFA8B193),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                vaultTimeLabel(context, endAt, now),
                color = Color(0xFFE9DCC0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0x1AF5EAD8), RoundedCornerShape(999.dp))
                    .border(1.dp, Color(0x2EF5EAD8), RoundedCornerShape(999.dp))
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
            val canTapUnlock = state.manualActive && !state.keyOnly
            Text(
                when {
                    canTapUnlock -> stringResource(R.string.lock_hint_tap)
                    state.hasKey -> stringResource(R.string.lock_hint_key)
                    else -> stringResource(R.string.lock_hint_none)
                },
                color = Color(0xFF8A9475),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 30.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFF5EAD8), RoundedCornerShape(999.dp))
                    .clickable(onClick = onHome),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.lock_home_button), color = Color(0xFF20261A), style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
            }
            HoldToUnlock(
                accent = Color(0xFFF6A06B),
                trackColor = Color(0x1AF5EAD8),
                textColor = Color(0xFFE9DCC0),
                onUnlock = { Store.emergencyUnlock() },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Star(
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    size: androidx.compose.ui.unit.Dp,
    alpha: Float,
) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .background(Color(0xFFF5EAD8).copy(alpha = alpha), CircleShape),
    )
}

/** Gentle pause, per design 2c: dawn - the sun is only rising. */
@Composable
fun GentleScreenContent(pkg: String, appLabel: String, onEnterAnyway: () -> Unit, onHome: () -> Unit) {
    var secondsLeft by remember(pkg) { mutableLongStateOf(10L) }
    LaunchedEffect(pkg) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val breath = rememberInfiniteTransition(label = "dawn")
    val halo1 by breath.animateFloat(
        0.88f, 1.08f,
        infiniteRepeatable(tween(5000), RepeatMode.Reverse),
        label = "halo1",
    )
    val halo2 by breath.animateFloat(
        0.9f, 1.1f,
        infiniteRepeatable(tween(5000), RepeatMode.Reverse, StartOffset(350)),
        label = "halo2",
    )
    val sunBreath by breath.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(5000), RepeatMode.Reverse, StartOffset(700)),
        label = "sunBreath",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEED9B6), Color(0xFFF3E5C8), Color(0xFFF7EDD9)),
                ),
            ),
    ) {
        // Rising sun with breathing halos.
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(300.dp).scale(halo1).background(Color(0x1AC67139), CircleShape))
            Box(Modifier.size(200.dp).scale(halo2).background(Color(0x24C67139), CircleShape))
            Box(
                Modifier
                    .size(108.dp)
                    .scale(sunBreath)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFDD9A5C), Color(0xFFC67139))),
                        CircleShape,
                    ),
            )
        }

        HillsCanvas(
            dawnHills,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(180.dp),
        )

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 36.dp).offset(y = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.gentle_kicker),
                color = Color(0xFFA4713F),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.gentle_title),
                color = Color(0xFF201E1D),
                fontFamily = MiriamLibre,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.gentle_body, appLabel),
                color = Color(0xFF6B6353),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 30.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFC67139), RoundedCornerShape(999.dp))
                    .clickable(onClick = onHome),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.gentle_home), color = Color(0xFFFFFBF2), style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
            }
            if (secondsLeft > 0) {
                Text(
                    stringResource(R.string.gentle_wait, secondsLeft),
                    color = Color(0xFF5C5344),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    stringResource(R.string.gentle_enter),
                    color = Color(0xFF8C491A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(onClick = onEnterAnyway).padding(4.dp),
                )
            }
        }
    }
}

// sunriseLabel/lockCountdownLabel lived here for the retired sunset design and
// had no callers left; the vault countdown is vaultTimeLabel in VaultDoor.kt.
