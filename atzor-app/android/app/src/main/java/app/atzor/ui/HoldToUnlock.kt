package app.atzor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.R
import app.atzor.data.Store

/**
 * The emergency way out of a lock.
 *
 * There is no waiting period: the friction is the gesture itself. Holding for
 * five straight seconds is impossible to do by accident and impossible to do
 * absent-mindedly, but it is instant when someone genuinely needs out, which
 * matters because a key-only lock has no other exit if the tag is lost.
 *
 * Letting go early resets it, and the fill is the whole feedback loop: you can
 * see how much longer you have to mean it.
 */
@Composable
fun HoldToUnlock(
    accent: Color,
    trackColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onUnlock: () -> Unit,
) {
    var holding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(holding) {
        if (holding) {
            Store.emergencyHoldStarted()
            val remainingMs = (Store.EMERGENCY_HOLD_MS * (1f - progress.value)).toInt()
            progress.animateTo(1f, tween(remainingMs, easing = LinearEasing))
            // Only reached when the hold ran the full distance; releasing early
            // cancels this coroutine before we get here.
            if (progress.value >= 1f) onUnlock()
        } else {
            progress.animateTo(0f, tween(220, easing = LinearEasing))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // The fill sits behind the label and grows with the hold.
        Box(
            Modifier
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val w = (constraints.maxWidth * progress.value).toInt().coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(w, placeable.height) { placeable.placeRelative(0, 0) }
                }
                .background(accent.copy(alpha = 0.35f)),
        )
        Text(
            stringResource(if (holding) R.string.emergency_holding else R.string.emergency_hold_prompt),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
