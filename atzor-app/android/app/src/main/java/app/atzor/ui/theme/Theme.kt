package app.atzor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.atzor.R

/**
 * "Organic" system, implemented from the Claude Design project
 * (Atzor Redesign.dc.html): warm sand ground, terracotta accent, olive
 * second accent, round shapes, soft ink-tinted shadows, Miriam Libre headings.
 *
 * Token names kept from earlier iterations; VALUES follow the design system:
 *  Night   = --color-bg        NightDeep = --color-surface
 *  CardBg  = --color-neutral-100
 *  Cream   = --color-text      CreamSoft = --color-neutral-600   CreamFaint = --color-neutral-500
 *  Coral   = --color-accent    CoralDeep = --color-accent-700
 *  Leaf    = --color-accent-2  LeafDeep  = --color-accent-2-700
 *  Sun     = --color-accent-400 (emergency-ready highlight)
 *  Line    = --color-divider
 */
val Night = Color(0xFFF5EAD8)
val NightDeep = Color(0xFFEBDDC5)
val CardBg = Color(0xFFF9F4ED)
val Cream = Color(0xFF201E1D)
val CreamSoft = Color(0xFF82796A)
val CreamFaint = Color(0xFFA19786)
val Coral = Color(0xFFC67139)
val CoralDeep = Color(0xFF8C491A)
val Leaf = Color(0xFF7A8A5E)
val LeafDeep = Color(0xFF56633F)
val Sun = Color(0xFFF6A06B)
val Line = Color(0x29201E1D)

// Accent ramps used directly by screens.
val Accent100 = Color(0xFFFFF2EB)
val Accent300 = Color(0xFFFFC6A5)
val Accent600 = Color(0xFFB2622D)
val A2_100 = Color(0xFFF0FAE1)
val A2_200 = Color(0xFFE1EECC)
val A2_300 = Color(0xFFCCDBB2)
val A2_400 = Color(0xFFAEBF92)
val A2_700 = Color(0xFF56633F)
val A2_800 = Color(0xFF3D472B)
val A2_900 = Color(0xFF272E1B)
val OnAccent = Color(0xFFF9F4ED)

val Assistant = FontFamily(Font(R.font.assistant))
val MiriamLibre = FontFamily(Font(R.font.miriam_libre))
val FrankRuhl = FontFamily(Font(R.font.frank_ruhl))

private val scheme = lightColorScheme(
    primary = Coral,
    onPrimary = OnAccent,
    secondary = Leaf,
    onSecondary = OnAccent,
    background = Night,
    onBackground = Cream,
    surface = CardBg,
    onSurface = Cream,
    surfaceVariant = NightDeep,
    onSurfaceVariant = CreamSoft,
    outline = Line,
    error = Color(0xFFB14F42),
)

private val type = Typography(
    displayLarge = TextStyle(fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = MiriamLibre, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Assistant, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Assistant, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Assistant, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Assistant, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = Assistant, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
)

@Composable
fun AtzorTheme(content: @Composable () -> Unit) {
    // The UI used to be Hebrew-only, so this forced RTL. Now that English is
    // the default for non-Hebrew devices, direction has to follow the strings
    // actually being shown: forcing RTL would render the English build
    // right-to-left. Resolved from the resource-selected locale rather than
    // the raw system locale, so a Hebrew-language device gets RTL and every
    // other language gets LTR, matching whichever strings.xml Android picked.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val direction = if (android.text.TextUtils.getLayoutDirectionFromLocale(locale) ==
        android.view.View.LAYOUT_DIRECTION_RTL
    ) {
        androidx.compose.ui.unit.LayoutDirection.Rtl
    } else {
        androidx.compose.ui.unit.LayoutDirection.Ltr
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides direction,
    ) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
