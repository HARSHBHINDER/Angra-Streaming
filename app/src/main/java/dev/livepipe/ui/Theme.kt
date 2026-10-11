package dev.livepipe.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AppleRed = Color(0xFFFF3B30)
val AppleGreen = Color(0xFF34C759)

/**
 * One app theme. Apple's structure (grouped background, cards, fills, separators, labels) stays fixed; a theme
 * swaps the neutrals, the tint, the button gradient and the two glow lights. dark = null follows the system.
 */
class Palette(
    val id: String, val name: String, val dark: Boolean?,
    val bg: Color, val card: Color, val fill: Color,
    val accent: Color, val brush: List<Color>, val glow: List<Color>,
)

private fun c(v: Long) = Color(v)
private val None = c(0x00000000)

/**
 * The 20 themes, in picker order. Named ones follow their sources: Tron Legacy = ggsci pal_tron("legacy")
 * (light cyan, white light, Clu orange); Cyberpunk = CDPR's acid yellow with red and turquoise; Nord = nordtheme.com.
 */
val Palettes = listOf(
    Palette("matte", "Matte Black", true, c(0xFF000000), c(0xFF111111), c(0xFF1C1C1C), c(0xFFA1A1A6), listOf(c(0xFF4A4A4E), c(0xFF242426)), listOf(c(0x14FFFFFF), None)),
    Palette("white", "White", false, c(0xFFFFFFFF), c(0xFFF5F5F7), c(0xFFE8E8ED), c(0xFF1D1D1F), listOf(c(0xFF3A3A3C), c(0xFF1D1D1F)), listOf(None, None)),
    Palette("original", "Original", null, c(0xFF000000), c(0xFF1C1C1E), c(0xFF2C2C2E), c(0xFFF2542D), listOf(c(0xFFFF2D3A), c(0xFFFF8A00)), listOf(c(0xFFFF3B30), c(0xFFFF8A00))),
    Palette("tron", "Tron Legacy", true, c(0xFF02070B), c(0xFF0A161D), c(0xFF10222C), c(0xFF6EE2FF), listOf(c(0xFFF79D1E), c(0xFFFF410D)), listOf(c(0xFF6EE2FF), c(0xFFF79D1E))),
    Palette("cyberpunk", "Cyberpunk", true, c(0xFF0B0A0F), c(0xFF16141C), c(0xFF221F2B), c(0xFFFCEE0A), listOf(c(0xFFFF003C), c(0xFFB8002B)), listOf(c(0xFFFCEE0A), c(0xFF00F0FF))),
    Palette("whiteninja", "White Ninja", false, c(0xFFF6F3EC), c(0xFFFFFFFF), c(0xFFEAE5DA), c(0xFFB3001B), listOf(c(0xFFD7263D), c(0xFF8B0011)), listOf(c(0xFFD7263D), None)),
    Palette("blackninja", "Black Ninja", true, c(0xFF050505), c(0xFF121212), c(0xFF1D1D1D), c(0xFFE01E37), listOf(c(0xFFE01E37), c(0xFF6A040F)), listOf(c(0xFF9D0208), None)),
    Palette("synthwave", "Synthwave", true, c(0xFF150A26), c(0xFF22123B), c(0xFF2E1A4D), c(0xFFFF4F9A), listOf(c(0xFFFF4F9A), c(0xFFFF9E3D)), listOf(c(0xFFFF4F9A), c(0xFF7B2FF7))),
    Palette("aurora", "Aurora", true, c(0xFF040A16), c(0xFF0C1729), c(0xFF13223A), c(0xFF3DDC97), listOf(c(0xFF3DDC97), c(0xFF2B8FED)), listOf(c(0xFF3DDC97), c(0xFF7F5AF0))),
    Palette("glass", "Liquid Glass", false, c(0xFFEAF1F8), c(0xFFF9FBFE), c(0xFFDCE5EF), c(0xFF007AFF), listOf(c(0xFF5AC8FA), c(0xFF007AFF)), listOf(c(0xFF5AC8FA), c(0xFFBF5AF2))),
    Palette("ocean", "Deep Ocean", true, c(0xFF03111C), c(0xFF0A2031), c(0xFF112C42), c(0xFF22C3E6), listOf(c(0xFF48CAE4), c(0xFF0077B6)), listOf(c(0xFF0096C7), c(0xFF00B4D8))),
    Palette("sakura", "Sakura", false, c(0xFFFFF4F6), c(0xFFFFFFFF), c(0xFFFBE3EA), c(0xFFE0457B), listOf(c(0xFFFF8FAB), c(0xFFE0457B)), listOf(c(0xFFFF8FAB), c(0xFFFFC2D1))),
    Palette("matrix", "Matrix", true, c(0xFF000400), c(0xFF051208), c(0xFF0A1F0E), c(0xFF00E676), listOf(c(0xFF1DB954), c(0xFF0A6E2A)), listOf(c(0xFF00FF41), None)),
    Palette("vaporwave", "Vaporwave", true, c(0xFF1C1033), c(0xFF281845), c(0xFF342058), c(0xFF01CDFE), listOf(c(0xFFFF71CE), c(0xFFB967FF)), listOf(c(0xFFFF71CE), c(0xFF01CDFE))),
    Palette("gold", "Gold Luxe", true, c(0xFF0B0906), c(0xFF17140D), c(0xFF231E13), c(0xFFD4AF37), listOf(c(0xFFE6C55A), c(0xFFA87B12)), listOf(c(0xFFD4AF37), None)),
    Palette("nord", "Nord", true, c(0xFF2E3440), c(0xFF3B4252), c(0xFF434C5E), c(0xFF88C0D0), listOf(c(0xFF81A1C1), c(0xFF5E81AC)), listOf(c(0xFF88C0D0), c(0xFFB48EAD))),
    Palette("dune", "Dune", false, c(0xFFF2E8DA), c(0xFFFFF9F0), c(0xFFE6D6BF), c(0xFFC2410C), listOf(c(0xFFF59E0B), c(0xFFC2410C)), listOf(c(0xFFF59E0B), c(0xFFC2410C))),
    Palette("ultraviolet", "Ultraviolet", true, c(0xFF0B0515), c(0xFF170B2A), c(0xFF22113E), c(0xFFB57BFF), listOf(c(0xFFA855F7), c(0xFF6D28D9)), listOf(c(0xFFA855F7), c(0xFF3B82F6))),
    Palette("titanium", "Titanium", false, c(0xFFE4E4E1), c(0xFFF4F4F2), c(0xFFD5D5D1), c(0xFF55555A), listOf(c(0xFF8E8E93), c(0xFF48484A)), listOf(None, None)),
    Palette("hologram", "Hologram", false, c(0xFFF3F0FF), c(0xFFFFFFFF), c(0xFFE7E1FF), c(0xFF7C5CFF), listOf(c(0xFFFF7AD9), c(0xFF7C5CFF), c(0xFF3DE1FF)), listOf(c(0xFFFF7AD9), c(0xFF3DE1FF))),
)

fun paletteOf(id: String) = Palettes.firstOrNull { it.id == id } ?: Palettes[2]

/** The theme in use and whether it renders dark (Original follows the system). */
class Themed(val p: Palette, val dark: Boolean)
val LocalTheme = staticCompositionLocalOf { Themed(Palettes[2], true) }

val Accent @Composable get() = LocalTheme.current.p.accent
val EmberBrush @Composable get() = Brush.linearGradient(LocalTheme.current.p.brush)
val isDark @Composable get() = LocalTheme.current.dark

private fun scheme(p: Palette, dark: Boolean) = when {
    p.dark == null && dark -> darkColorScheme(primary = p.accent, background = c(0xFF000000), surface = c(0xFF1C1C1E),
        surfaceVariant = c(0xFF2C2C2E), onBackground = Color.White, onSurface = Color.White, error = AppleRed)
    p.dark == null -> lightColorScheme(primary = p.accent, background = c(0xFFF2F2F7), surface = Color.White,   // systemGroupedBackground + cards
        surfaceVariant = c(0xFFE5E5EA), onBackground = Color.Black, onSurface = Color.Black, error = AppleRed)
    dark -> darkColorScheme(primary = p.accent, background = p.bg, surface = p.card, surfaceVariant = p.fill,
        onBackground = Color.White, onSurface = Color.White, error = AppleRed)
    else -> lightColorScheme(primary = p.accent, background = p.bg, surface = p.card, surfaceVariant = p.fill,
        onBackground = Color.Black, onSurface = Color.Black, error = AppleRed)
}

/** iOS semantic colors Material has no slot for. */
object Ios {
    val separator @Composable get() = if (isDark) Color(0x99545458) else Color(0x4A3C3C43)
    val secondaryLabel @Composable get() = if (isDark) Color(0x99EBEBF5) else Color(0x993C3C43)
    val segmentTrack @Composable get() = if (isDark) Color(0x3D767680) else Color(0x1F767680)
    val segmentThumb @Composable get() = if (isDark) Color(0xFF636366) else Color.White
    val switchOff @Composable get() = if (isDark) Color(0xFF39393D) else Color(0xFFE9E9EA)
}

/** iOS-ish rounded corners. */
object AppleShapes {
    val card = RoundedCornerShape(12.dp)
    val capsule = RoundedCornerShape(50)
}

// SF-like metrics: 17sp body with slightly negative tracking (Material's default +0.5sp reads un-Apple).
private val IosType = Typography(
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePipeTheme(theme: String = "original", content: @Composable () -> Unit) {
    val p = paletteOf(theme)
    val dark = p.dark ?: isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {          // status bar icons follow the theme, not the system
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark }
    }
    MaterialTheme(colorScheme = scheme(p, dark), typography = IosType) {
        // No Material ripples anywhere: iOS gives press feedback by dimming/scaling (see iosTap).
        CompositionLocalProvider(LocalRippleConfiguration provides null, LocalTheme provides Themed(p, dark), content = content)
    }
}
