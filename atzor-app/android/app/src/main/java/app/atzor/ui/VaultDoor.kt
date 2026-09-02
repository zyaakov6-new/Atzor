package app.atzor.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Steel / bronze palette for the vault.
private val SteelLight = Color(0xFFD4D0C8)
private val SteelMid = Color(0xFF9A958C)
private val SteelDark = Color(0xFF4A4740)
private val SteelDeep = Color(0xFF2A2824)
private val Bronze = Color(0xFFC67139)
private val BronzeDeep = Color(0xFF8C491A)
private val VaultGlow = Color(0x33F5EAD8)
private val OpenGlow = Color(0x55F2CF7B)

/**
 * Heavy industrial circular vault door.
 * [sealProgress] 0 = open, 1 = sealed.
 * [wheelSpin] degrees layered on top of the seal animation.
 */
@Composable
fun VaultDoor(
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    sealProgress: Float = 1f,
    wheelSpin: Float = 0f,
    idle: Boolean = true,
) {
    val breath = rememberInfiniteTransition(label = "vaultBreath")
    val glow by breath.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "glow",
    )
    val idleTick by breath.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing)),
        label = "idleSpin",
    )

    val doorClose = sealProgress.coerceIn(0f, 1f)
    val wheelAngle = wheelSpin + doorClose * 540f +
        if (idle && doorClose >= 0.99f) idleTick * 0.015f else 0f

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension / 2f

        // Ambient glow: warm when open, cool when sealed.
        drawCircle(
            color = if (doorClose < 0.4f)
                OpenGlow.copy(alpha = 0.22f + 0.12f * glow * (1f - doorClose))
            else
                VaultGlow.copy(alpha = 0.12f + 0.10f * glow * doorClose),
            radius = r * 1.08f,
            center = Offset(cx, cy),
        )

        // Outer mounting ring.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SteelMid, SteelDark, SteelDeep),
                center = Offset(cx - r * 0.15f, cy - r * 0.18f),
                radius = r,
            ),
            radius = r * 0.98f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = SteelLight.copy(alpha = 0.35f),
            radius = r * 0.98f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.018f),
        )

        // Rivets.
        val rivetCount = 16
        for (i in 0 until rivetCount) {
            val a = Math.toRadians(i * 360.0 / rivetCount - 90.0)
            val rr = r * 0.90f
            val px = cx + (cos(a) * rr).toFloat()
            val py = cy + (sin(a) * rr).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(SteelLight, SteelDark),
                    center = Offset(px - 1.5f, py - 1.5f),
                    radius = r * 0.045f,
                ),
                radius = r * 0.038f,
                center = Offset(px, py),
            )
        }

        // Inner barrel.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6B675F), SteelDark, SteelDeep),
                center = Offset(cx - r * 0.12f, cy - r * 0.14f),
                radius = r * 0.82f,
            ),
            radius = r * 0.78f,
            center = Offset(cx, cy),
        )

        // Warm “open vault” core when unsealed.
        if (doorClose < 0.85f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF2CF7B).copy(alpha = 0.55f * (1f - doorClose)),
                        Color(0xFFC67139).copy(alpha = 0.15f * (1f - doorClose)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r * 0.42f,
                ),
                radius = r * 0.42f,
                center = Offset(cx, cy),
            )
        }

        for (f in listOf(0.72f, 0.62f, 0.52f)) {
            drawCircle(
                color = SteelLight.copy(alpha = 0.18f + 0.08f * doorClose),
                radius = r * f,
                center = Offset(cx, cy),
                style = Stroke(width = r * 0.012f),
            )
        }

        drawVaultIris(cx, cy, r * 0.48f, doorClose)

        // Center hub.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SteelMid, SteelDeep),
                center = Offset(cx - r * 0.06f, cy - r * 0.07f),
                radius = r * 0.22f,
            ),
            radius = r * 0.18f,
            center = Offset(cx, cy),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Bronze.copy(alpha = 0.9f), BronzeDeep),
                center = Offset(cx - r * 0.03f, cy - r * 0.03f),
                radius = r * 0.10f,
            ),
            radius = r * 0.08f,
            center = Offset(cx, cy),
        )

        rotate(wheelAngle, Offset(cx, cy)) {
            drawVaultWheel(cx, cy, r)
        }

        if (doorClose > 0.85f) {
            drawCircle(
                color = Bronze.copy(alpha = 0.25f * glow * ((doorClose - 0.85f) / 0.15f)),
                radius = r * 0.98f,
                center = Offset(cx, cy),
                style = Stroke(width = r * 0.02f),
            )
        }
    }
}

private fun DrawScope.drawVaultIris(cx: Float, cy: Float, radius: Float, close: Float) {
    val segments = 6
    val openGap = 1f - close
    for (i in 0 until segments) {
        val base = i * 360f / segments
        val sweep = (360f / segments) * (0.55f + 0.45f * close)
        val start = base - sweep / 2f + openGap * 8f
        val path = Path().apply {
            moveTo(cx, cy)
            val a0 = Math.toRadians(start.toDouble())
            lineTo(cx + (cos(a0) * radius).toFloat(), cy + (sin(a0) * radius).toFloat())
            var t = 0
            while (t <= 8) {
                val a = Math.toRadians((start + sweep * t / 8f).toDouble())
                lineTo(cx + (cos(a) * radius).toFloat(), cy + (sin(a) * radius).toFloat())
                t++
            }
            close()
        }
        drawPath(
            path,
            brush = Brush.radialGradient(
                colors = listOf(
                    SteelDark.copy(alpha = 0.35f + 0.5f * close),
                    SteelDeep.copy(alpha = 0.7f + 0.3f * close),
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
        )
        drawPath(
            path,
            color = SteelLight.copy(alpha = 0.12f + 0.1f * close),
            style = Stroke(width = 1.5f),
        )
    }
}

private fun DrawScope.drawVaultWheel(cx: Float, cy: Float, r: Float) {
    val spokeCount = 6
    val outer = r * 0.58f
    val inner = r * 0.20f
    val spokeW = r * 0.055f

    drawCircle(
        brush = Brush.sweepGradient(
            listOf(SteelLight, SteelMid, SteelDark, SteelMid, SteelLight),
            center = Offset(cx, cy),
        ),
        radius = outer,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.07f),
    )
    drawCircle(
        color = SteelLight.copy(alpha = 0.4f),
        radius = outer + r * 0.035f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.012f),
    )
    drawCircle(
        color = SteelDeep.copy(alpha = 0.5f),
        radius = outer - r * 0.035f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.012f),
    )

    for (i in 0 until spokeCount) {
        val a = Math.toRadians(i * 360.0 / spokeCount)
        val cosA = cos(a).toFloat()
        val sinA = sin(a).toFloat()
        drawLine(
            brush = Brush.linearGradient(
                listOf(SteelLight, SteelDark),
                start = Offset(cx + cosA * inner, cy + sinA * inner),
                end = Offset(cx + cosA * outer, cy + sinA * outer),
            ),
            start = Offset(cx + cosA * inner, cy + sinA * inner),
            end = Offset(cx + cosA * (outer - r * 0.02f), cy + sinA * (outer - r * 0.02f)),
            strokeWidth = spokeW,
            cap = StrokeCap.Round,
        )
        val bx = cx + cosA * outer
        val by = cy + sinA * outer
        drawCircle(
            brush = Brush.radialGradient(
                listOf(SteelLight, SteelDark),
                center = Offset(bx - 1f, by - 1f),
                radius = r * 0.05f,
            ),
            radius = r * 0.042f,
            center = Offset(bx, by),
        )
    }

    val handleR = r * 0.58f
    drawLine(
        brush = Brush.linearGradient(
            listOf(BronzeDeep, Bronze, BronzeDeep),
            start = Offset(cx - handleR, cy),
            end = Offset(cx + handleR, cy),
        ),
        start = Offset(cx - handleR, cy),
        end = Offset(cx + handleR, cy),
        strokeWidth = r * 0.048f,
        cap = StrokeCap.Round,
    )
    for (sign in listOf(-1f, 1f)) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Bronze, BronzeDeep),
                center = Offset(cx + sign * handleR - 2f, cy - 2f),
                radius = r * 0.06f,
            ),
            radius = r * 0.055f,
            center = Offset(cx + sign * handleR, cy),
        )
    }
}

private data class AppIconBm(
    val bitmap: androidx.compose.ui.graphics.ImageBitmap,
    val index: Int,
)

/**
 * Vault + real blocked-app logos:
 * - On seal: logos spiral inward into the door.
 * - While locked: logos keep a slow spiral orbit *inside* the vault.
 * - While open: logos rest in a loose outer ring (ready to be sealed).
 */
@Composable
fun VaultSealScene(
    active: Boolean,
    blockedPackages: Set<String>,
    modifier: Modifier = Modifier,
    vaultSize: Dp = 180.dp,
    playSealOnActivate: Boolean = true,
    reduceMotion: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val systemReduce = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
    val calm = reduceMotion || systemReduce
    val seal = remember { Animatable(if (active) 1f else 0f) }
    val spin = remember { Animatable(0f) }
    var icons by remember { mutableStateOf<List<AppIconBm>>(emptyList()) }

    LaunchedEffect(blockedPackages) {
        if (blockedPackages.isEmpty()) {
            icons = emptyList()
            return@LaunchedEffect
        }
        val pm = context.packageManager
        // Stable order so logos don't reshuffle every recomposition.
        val pkgs = blockedPackages.toList().sorted().take(10)
        icons = pkgs.mapIndexedNotNull { index, pkg ->
            val bmp = loadAppIconBitmap(pm, pkg) ?: return@mapIndexedNotNull null
            AppIconBm(bmp.asImageBitmap(), index)
        }
    }

    LaunchedEffect(active, calm) {
        if (active) {
            if (playSealOnActivate && !calm) {
                seal.snapTo(0.08f)
                spin.snapTo(0f)
                delay(40)
                launch {
                    seal.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
                }
                spin.animateTo(720f, tween(1600, easing = FastOutSlowInEasing))
            } else {
                seal.snapTo(1f)
                spin.snapTo(if (calm) 0f else 720f)
            }
        } else {
            if (calm) seal.snapTo(0f) else seal.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
            spin.snapTo(0f)
        }
    }

    val orbit = rememberInfiniteTransition(label = "orbit")
    val orbitAngle by orbit.animateFloat(
        initialValue = 0f,
        targetValue = if (calm) 0f else 360f,
        animationSpec = infiniteRepeatable(
            tween(if (calm) 1 else 14_000, easing = LinearEasing),
        ),
        label = "orbitAngle",
    )
    val spiralPulse by orbit.animateFloat(
        initialValue = if (calm) 0.5f else 0f,
        targetValue = if (calm) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            tween(if (calm) 1 else 4200),
            RepeatMode.Reverse,
        ),
        label = "spiralPulse",
    )

    val sealP = seal.value.coerceIn(0f, 1f)
    val vaultPx = with(density) { vaultSize.toPx() }
    // Inner orbit radius (inside the door window).
    val innerR = vaultPx * 0.22f
    // Outer ring when open / start of suck-in.
    val outerR = vaultPx * 0.72f

    Box(
        modifier = modifier.size(vaultSize * 1.55f),
        contentAlignment = Alignment.Center,
    ) {
        // App logos - under the door so they sit “inside” once sealed.
        icons.forEach { icon ->
            val n = icons.size.coerceAtLeast(1)
            val baseAngle = icon.index * (360f / n)
            val t = sealP
            // Spiral: radius shrinks while angle winds in during seal.
            val wind = t * 280f
            val radius = outerR + (innerR - outerR) * (t * t * (3f - 2f * t))
            // After sealed, keep orbiting slowly with a slight spiral pulse.
            val liveAngle = if (t > 0.92f) {
                baseAngle + orbitAngle + icon.index * 12f
            } else {
                baseAngle + wind + orbitAngle * 0.15f
            }
            val liveR = if (t > 0.92f) {
                innerR * (0.72f + 0.28f * spiralPulse + 0.06f * (icon.index % 3))
            } else {
                radius
            }
            val rad = Math.toRadians(liveAngle.toDouble())
            val x = (cos(rad) * liveR).toFloat()
            val y = (sin(rad) * liveR).toFloat()
            // Shrink as they enter; stay small inside.
            val scale = (1f - 0.55f * t).coerceIn(0.38f, 1f)
            val alpha = if (t < 0.05f && !active) 0.9f else (0.95f - 0.15f * t).coerceIn(0.55f, 1f)

            Image(
                bitmap = icon.bitmap,
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = with(density) { x.toDp() },
                        y = with(density) { y.toDp() },
                    )
                    .size(if (t > 0.9f) 28.dp else 36.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        rotationZ = if (t > 0.5f) liveAngle * 0.25f else 0f
                    }
                    .clip(CircleShape),
            )
        }

        VaultDoor(
            size = vaultSize,
            sealProgress = if (active) sealP.coerceAtLeast(0.12f) else sealP,
            wheelSpin = spin.value,
            idle = active && sealP >= 0.99f && !calm,
        )
    }
}

private fun loadAppIconBitmap(pm: PackageManager, pkg: String): Bitmap? = try {
    val d = pm.getApplicationIcon(pkg)
    drawableToBitmap(d, 96)
} catch (_: Exception) {
    null
}

private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return bmp
}

/** Clear countdown for the vault lock. Takes a Context so the copy stays in strings.xml. */
fun vaultTimeLabel(context: android.content.Context, endAt: Long, now: Long): String {
    if (endAt == Long.MAX_VALUE) return context.getString(app.atzor.R.string.vault_until_key)
    val endClock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(endAt))
    val left = ((endAt - now).coerceAtLeast(0L)) / 1000
    val leftLabel = if (left >= 3600) "%d:%02d".format(left / 3600, (left % 3600) / 60)
    else "%d:%02d".format(left / 60, left % 60)
    return context.getString(app.atzor.R.string.vault_time_left, leftLabel, endClock)
}
