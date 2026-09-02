package app.atzor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atzor.ui.theme.CardBg
import app.atzor.ui.theme.Coral
import app.atzor.ui.theme.Cream
import app.atzor.ui.theme.CreamSoft
import app.atzor.ui.theme.Line

private val Steel = Color(0xFF6B675F)
private val SteelLight = Color(0xFFB8B2A6)

enum class EmptyKind { Keys, Apps }

/**
 * Friendly empty state: illustration + title + body.
 */
@Composable
fun EmptyStateCard(
    kind: EmptyKind,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (kind) {
            EmptyKind.Keys -> EmptyKeysIllustration()
            EmptyKind.Apps -> EmptyAppsIllustration()
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Cream,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            color = CreamSoft,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyKeysIllustration() {
    Canvas(Modifier.size(96.dp)) {
        val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        val cx = size.width * 0.38f
        val cy = size.height * 0.48f
        val r = size.minDimension * 0.22f
        drawCircle(color = Coral, radius = r, center = Offset(cx, cy), style = stroke)
        drawCircle(color = Coral.copy(alpha = 0.35f), radius = r * 0.35f, center = Offset(cx, cy))
        val shaftY = cy
        drawLine(
            color = Coral,
            start = Offset(cx + r * 0.95f, shaftY),
            end = Offset(size.width * 0.88f, shaftY),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        val toothX = size.width * 0.72f
        drawLine(Coral, Offset(toothX, shaftY), Offset(toothX, shaftY + size.height * 0.14f), stroke.width, StrokeCap.Round)
        drawLine(
            Coral,
            Offset(toothX + size.width * 0.08f, shaftY),
            Offset(toothX + size.width * 0.08f, shaftY + size.height * 0.1f),
            stroke.width,
            StrokeCap.Round,
        )
        drawCircle(
            color = SteelLight.copy(alpha = 0.5f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.5f, size.height * 0.5f),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun EmptyAppsIllustration() {
    Canvas(Modifier.size(96.dp)) {
        val gap = 6.dp.toPx()
        val cell = (size.minDimension - gap * 2) / 3f
        val origin = Offset((size.width - cell * 3 - gap * 2) / 2f, (size.height - cell * 3 - gap * 2) / 2f)
        val colors = listOf(
            Coral.copy(alpha = 0.85f), Steel, Coral.copy(alpha = 0.45f),
            SteelLight, Coral, Steel.copy(alpha = 0.7f),
            SteelLight.copy(alpha = 0.6f), Coral.copy(alpha = 0.55f), Steel,
        )
        var i = 0
        for (row in 0..2) {
            for (col in 0..2) {
                val left = origin.x + col * (cell + gap)
                val top = origin.y + row * (cell + gap)
                drawRoundRect(
                    color = colors[i % colors.size],
                    topLeft = Offset(left, top),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.22f),
                    style = Stroke(width = 2.4.dp.toPx()),
                )
                i++
            }
        }
        val cx = origin.x + 1 * (cell + gap) + cell / 2f
        val cy = origin.y + 1 * (cell + gap) + cell / 2f
        val arm = cell * 0.22f
        drawLine(Coral, Offset(cx - arm, cy), Offset(cx + arm, cy), 2.8.dp.toPx(), StrokeCap.Round)
        drawLine(Coral, Offset(cx, cy - arm), Offset(cx, cy + arm), 2.8.dp.toPx(), StrokeCap.Round)
    }
}
