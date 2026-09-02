package app.atzor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The layered hills of the sunset design, as normalized cubic paths
 * (fractions of the canvas), traced from the design's SVG.
 */
class Hill(val color: Color, val alpha: Float = 1f, private val pts: FloatArray) {
    fun path(w: Float, h: Float): Path = Path().apply {
        moveTo(0f, pts[0] * h)
        var i = 1
        while (i + 5 < pts.size) {
            cubicTo(
                pts[i] * w, pts[i + 1] * h,
                pts[i + 2] * w, pts[i + 3] * h,
                pts[i + 4] * w, pts[i + 5] * h,
            )
            i += 6
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
}

/** Day hills, from design 2a (viewBox 412x340). */
val dayHills = listOf(
    Hill(Color(0xFF95A375), pts = floatArrayOf(0.441f, 0.17f, 0.324f, 0.315f, 0.376f, 0.485f, 0.282f, 0.655f, 0.188f, 0.801f, 0.271f, 1f, 0.212f)),
    Hill(Color(0xFF76865A), pts = floatArrayOf(0.632f, 0.218f, 0.515f, 0.388f, 0.603f, 0.582f, 0.524f, 0.752f, 0.456f, 0.874f, 0.529f, 1f, 0.476f)),
    Hill(Color(0xFF576440), pts = floatArrayOf(0.853f, 0.267f, 0.735f, 0.51f, 0.829f, 0.728f, 0.759f, 0.862f, 0.718f, 0.947f, 0.759f, 1f, 0.735f)),
)

/** Night hills, from design 2b (viewBox 412x260). */
val nightHills = listOf(
    Hill(Color(0xFF1C2314), pts = floatArrayOf(0.423f, 0.218f, 0.269f, 0.437f, 0.385f, 0.631f, 0.285f, 0.801f, 0.2f, 0.922f, 0.285f, 1f, 0.231f)),
    Hill(Color(0xFF141A0E), pts = floatArrayOf(0.731f, 0.267f, 0.577f, 0.534f, 0.7f, 0.777f, 0.608f, 0.898f, 0.562f, 0.971f, 0.608f, 1f, 0.585f)),
)

/** Dawn hills, from design 2c (viewBox 412x200, drawn faint). */
val dawnHills = listOf(
    Hill(Color(0xFF95A375), alpha = 0.55f, pts = floatArrayOf(0.45f, 0.218f, 0.275f, 0.437f, 0.425f, 0.631f, 0.31f, 0.801f, 0.21f, 0.922f, 0.31f, 1f, 0.25f)),
    Hill(Color(0xFF76865A), alpha = 0.55f, pts = floatArrayOf(0.75f, 0.267f, 0.575f, 0.534f, 0.725f, 0.777f, 0.62f, 0.898f, 0.57f, 0.971f, 0.62f, 1f, 0.59f)),
)

fun DrawScope.drawHills(hills: List<Hill>) {
    hills.forEach { hill ->
        drawPath(hill.path(size.width, size.height), hill.color, alpha = hill.alpha)
    }
}

@Composable
fun HillsCanvas(hills: List<Hill>, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawHills(hills) }
}
