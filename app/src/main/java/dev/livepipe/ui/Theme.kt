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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.livepipe.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val AppleRed = Color(0xFFFF3B30)
val AppleGreen = Color(0xFF34C759)

/**
 * One app theme: neutrals, tint and button gradient (bg, card, fill, accent, brush) plus its [Look]: the world it
 * lives in, its materials, edges, corners and typographic voice. dark = null follows the system (Original).
 */
class Palette(
    val id: String, val name: String, val dark: Boolean?,
    val bg: Color, val card: Color, val fill: Color,
    val accent: Color, val brush: List<Color>, val look: Look,
)

private fun c(v: Long) = Color(v)

/**
 * Each theme speaks in its own face (OFL / Apache, from Google Fonts; subset to the app's characters, see
 * NOTICE-fonts.md). Chosen for the feeling: stealth precision, Apple clarity, the Grid's wide geometry, Night
 * City's cut-corner tech, a calm mincho brush, a lethal brush, 80s neon tubes, airy light, rounded water, polite
 * spring elegance, terminal glyphs, retro italic, Nordic calm and monumental desert.
 */
private object Faces2 {
    private fun f(id: Int) = FontFamily(Font(id))
    val manrope = f(R.font.theme_manrope); val inter = f(R.font.theme_inter); val grid = f(R.font.theme_grid)
    val oxanium = f(R.font.theme_oxanium); val oxaniumBody = f(R.font.theme_oxanium_body); val zen = f(R.font.theme_zenmincho)
    val shojumaru = f(R.font.theme_shojumaru); val monoton = f(R.font.theme_monoton); val raleway = f(R.font.theme_raleway)
    val quicksand = f(R.font.theme_quicksand); val cormorant = f(R.font.theme_cormorant); val shareTech = f(R.font.theme_sharetech)
    val shippori = f(R.font.theme_shippori); val zenMaru = f(R.font.theme_zenmaru)
    val baskerville = f(R.font.theme_baskerville); val jost = f(R.font.theme_jost); val syncopate = f(R.font.theme_syncopate)
}
private val N = FontWeight.Normal

/**
 * The 17 themes, in picker order, each built from research into its source:
 *  Tron Legacy — GMUNK's UI for the film: minimal, angled lines and neon glows on dark, travelling linework; cool
 *    cyan for users, orange for Clu (ggsci pal_tron "legacy" hexes).
 *  Cyberpunk — CDPR's UI Art Bible ("Neomilitarism"): acid yellow, red and turquoise, condensed technical type,
 *    scanlines, glitch transitions, chamfered panels.
 *  White / Black Ninja — sumi ink, washi paper and one vermilion (shu) sun; Platinum's "sci-fi ninja" red/black.
 *  Synthwave — segmented sunset sun, perspective grid, chrome titles; Vaporwave — pastel collage, VHS, katakana,
 *    full-width type.
 *  Aurora — colours by altitude (green O₂ core, red crowns, violet N₂ hems). Nord — nordtheme.com Polar Night,
 *    Frost and Aurora. Dune — Arrakis orange with a complementary Fremen blue accent.
 *  Liquid Glass — frosted backdrop blur + edge lensing + a specular rim.
 */
/*
 * Wa: paper and ink. By day unbleached silk (kinari) with ink type and one indigo (ai) action colour; by night sumi
 * ink with the indigo lifted. Seal red is kept for the stamp and live states. Shippori Mincho titles (brush-born,
 * calm authority), Zen Maru Gothic interface (soft, rounded, friendly for every age). All text in English.
 */
private fun waLook(night: Boolean) = Look(Scene.WA, Edge.NONE, radius = 18.dp,
    shadow = if (night) Color.Black.copy(alpha = 0.35f) else c(0x2E165E83),
    hero = listOf(c(0xFF1F6E98), c(0xFF0F2540)),
    font = Faces2.zenMaru, titleFont = Faces2.shippori, titleWeight = FontWeight.Bold, titleTracking = 0f, stamp = "配")
val WaDay = Palette("wa", "Wa", false, c(0xFFFBFAF5), c(0xFFFFFFFF), c(0xFFF3F0E6), c(0xFF165E83), listOf(c(0xFF1F77A6), c(0xFF165E83)), waLook(false))
private val WaNight = Palette("wa", "Wa", true, c(0xFF161513), c(0xFF22201C), c(0xFF2C2925), c(0xFF5B9BC2), listOf(c(0xFF5B9BC2), c(0xFF2C6E95)), waLook(true))

/** Wa follows the phone's day/night setting; every other theme keeps its own. */
fun Palette.forSystem(dark: Boolean) = if (this === WaDay && dark) WaNight else this

val Palettes = listOf(
    WaDay,
    Palette("matte", "Matte Black", true, c(0xFF000000), c(0xFF111111), c(0xFF1C1C1C), c(0xFFAEAEB2), listOf(c(0xFF48484A), c(0xFF1C1C1E)),
        Look(Scene.MATTE, Edge.HAIRLINE, radius = 22.dp, edgeColors = listOf(Color.White.copy(alpha = 0.07f)),
            hero = listOf(c(0xFF141414), c(0xFF050505)), titleFont = Faces2.manrope, titleWeight = N, titleTracking = -0.4f)),
    Palette("white", "White", false, c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFF2F2F7), c(0xFF1D1D1F), listOf(c(0xFF3A3A3C), c(0xFF000000)),
        Look(Scene.WHITE, Edge.NONE, radius = 24.dp, shadow = Color.Black.copy(alpha = 0.10f),
            hero = listOf(c(0xFF1C1C1E), c(0xFF000000)), titleFont = Faces2.inter, titleWeight = N, titleTracking = -0.8f)),
    Palette("original", "Original", null, c(0xFF000000), c(0xFF1C1C1E), c(0xFF2C2C2E), c(0xFFF2542D), listOf(c(0xFFFF2D3A), c(0xFFFF8A00)),
        Look(Scene.EMBER, Edge.HAIRLINE, radius = 20.dp)),
    Palette("tron", "Tron Legacy", true, c(0xFF02070B), c(0xD9061219), c(0xFF0B1F28), c(0xFF6EE2FF), listOf(c(0xFF6EE2FF), c(0xFFD0DFE6)),
        Look(Scene.TRON, Edge.PULSE, radius = 14.dp, edgeColors = listOf(c(0xFF6EE2FF)), hero = listOf(c(0xFF03121A), c(0xFF01060A)),
            titleFont = Faces2.grid, titleCaps = true, titleTracking = 1f, titleWeight = N, titleScale = 0.74f,
            outlineButton = true, onBrush = c(0xFF6EE2FF))),
    Palette("cyberpunk", "Cyberpunk", true, c(0xFF0B0A0F), c(0xE6121018), c(0xFF1E1A26), c(0xFFFCEE0A), listOf(c(0xFFFCEE0A), c(0xFFE8D400)),
        Look(Scene.CYBER, Edge.GLITCH, corner = Corner.CUT, radius = 16.dp, edgeColors = listOf(c(0xFFFCEE0A)),
            hero = listOf(c(0xFF16141C), c(0xFF2A0710)), font = Faces2.oxaniumBody, titleFont = Faces2.oxanium, titleCaps = true,
            titleTracking = 1.5f, titleWeight = N, titleScale = 0.85f,
            onBrush = Color.Black)),
    Palette("whiteninja", "White Ninja", false, c(0xFFF6F3EC), c(0xF2FBF9F4), c(0xFFECE7DC), c(0xFFC8102E), listOf(c(0xFFD7263D), c(0xFF9B0F1E)),
        Look(Scene.WASHI, Edge.BRUSH, Fill.PAPER, radius = 4.dp, edgeColors = listOf(c(0xFF1A1A1A)),
            hero = listOf(c(0xFF1A1A1A), c(0xFF2B2B2B)), titleFont = Faces2.zen, titleTracking = 1f, titleWeight = N, seal = c(0xFFC8102E))),
    Palette("blackninja", "Black Ninja", true, c(0xFF050505), c(0xF0101010), c(0xFF1A1A1A), c(0xFFE01E37), listOf(c(0xFFE01E37), c(0xFF7A0712)),
        Look(Scene.NIGHT, Edge.BLADE, corner = Corner.BLADE, radius = 14.dp, edgeColors = listOf(c(0xFFE01E37)),
            hero = listOf(c(0xFF0E0E0E), c(0xFF1F0306)), titleFont = Faces2.shojumaru, titleTracking = 1f, titleWeight = N,
            titleScale = 0.85f, seal = c(0xFFE01E37))),
    Palette("synthwave", "Synthwave", true, c(0xFF0D0221), c(0xD91A0B33), c(0xFF2A1250), c(0xFFFF3EC8), listOf(c(0xFFFF3864), c(0xFFFF9E3D)),
        Look(Scene.OUTRUN, Edge.NEON, radius = 14.dp, edgeColors = listOf(c(0xFFFF2BD6), c(0xFF2DE2E6)),
            hero = listOf(c(0xFF1A0533), c(0xFF2D0A4E)), titleFont = Faces2.monoton, titleCaps = true, titleTracking = 1.5f,
            titleWeight = N, titleScale = 0.72f,
            titleBrush = listOf(c(0xFFE0E0FF), Color.White, c(0xFF8A8FB3), Color.White, c(0xFFFF9BE8)))),
    Palette("aurora", "Aurora", true, c(0xFF01040C), c(0x660E1A2B), c(0xFF13223A), c(0xFF3DFFB0), listOf(c(0xFF39FFB0), c(0xFF2B8FED)),
        Look(Scene.AURORA, Edge.HAIRLINE, Fill.GLASS, radius = 24.dp, edgeColors = listOf(Color.White.copy(alpha = 0.16f)),
            titleFont = Faces2.raleway, titleWeight = N, titleTracking = 1f)),
    Palette("glass", "Liquid Glass", false, c(0xFFE9EEF6), c(0x59FFFFFF), c(0xFFDCE5EF), c(0xFF007AFF), listOf(c(0xFF5AC8FA), c(0xFF007AFF)),
        Look(Scene.GLASS, Edge.SPECULAR, Fill.GLASS, radius = 28.dp, floatingBar = true, frost = 30.dp,
            titleFont = Faces2.inter, titleWeight = N, titleTracking = -0.8f)),
    Palette("ocean", "Deep Ocean", true, c(0xFF021421), c(0x660A3550), c(0xFF112C42), c(0xFF4FE3FF), listOf(c(0xFF48CAE4), c(0xFF0077B6)),
        Look(Scene.OCEAN, Edge.SPECULAR, Fill.GLASS, radius = 24.dp, floatingBar = true,
            titleFont = Faces2.quicksand, titleWeight = N, titleTracking = -0.2f)),
    Palette("sakura", "Sakura", false, c(0xFFFFF6F8), c(0xEBFFFFFF), c(0xFFFBE3EA), c(0xFFD6336C), listOf(c(0xFFFF8FAB), c(0xFFE0457B)),
        Look(Scene.SAKURA, Edge.HAIRLINE, radius = 24.dp, edgeColors = listOf(c(0x66FFB3C6)), shadow = c(0x29E0457B),
            hero = listOf(c(0xFF3A1F29), c(0xFF1F1418)), titleFont = Faces2.cormorant, titleWeight = N, titleTracking = 0.3f, titleScale = 1.1f)),
    Palette("matrix", "Matrix", true, c(0xFF000500), c(0xD1021006), c(0xFF04210C), c(0xFF00FF41), listOf(c(0xFF00C837), c(0xFF007A22)),
        Look(Scene.RAIN, Edge.NEON, radius = 2.dp, edgeColors = listOf(c(0xFF00FF41)), hero = listOf(c(0xFF010C04), c(0xFF000300)),
            font = Faces2.shareTech, titleCaps = true, titleTracking = 1f, titleWeight = N, titleScale = 0.85f)),
    Palette("vaporwave", "Vaporwave", true, c(0xFF1B0F3B), c(0xD12A1A52), c(0xFF3A2366), c(0xFF01CDFE), listOf(c(0xFFFF71CE), c(0xFFB967FF)),
        Look(Scene.VAPOR, Edge.CHROMA, radius = 6.dp, edgeColors = listOf(c(0xFFFF71CE), c(0xFF01CDFE)),
            hero = listOf(c(0xFF2A1A52), c(0xFF1B0F3B)), titleFont = Faces2.baskerville, titleTracking = 3f, titleWeight = N, titleScale = 0.9f)),
    Palette("nord", "Nord", true, c(0xFF2E3440), c(0xEB3B4252), c(0xFF434C5E), c(0xFF88C0D0), listOf(c(0xFF81A1C1), c(0xFF5E81AC)),
        Look(Scene.SNOW, Edge.HAIRLINE, radius = 14.dp, edgeColors = listOf(c(0x5588C0D0)), hero = listOf(c(0xFF3B4252), c(0xFF2E3440)),
            titleFont = Faces2.jost, titleWeight = N, titleTracking = 0.2f)),
    Palette("dune", "Dune", false, c(0xFFF2E3C9), c(0xE0FFF8EC), c(0xFFEAD6B4), c(0xFF1F6FB2), listOf(c(0xFFE8822E), c(0xFFB8541C)),
        Look(Scene.DUNES, Edge.HAIRLINE, Fill.PAPER, radius = 10.dp, edgeColors = listOf(c(0x66C97A3A)),
            hero = listOf(c(0xFF3A2414), c(0xFF1E120A)), titleFont = Faces2.syncopate, titleCaps = true, titleTracking = 4f, titleWeight = N, titleScale = 0.62f)),
)

fun paletteOf(id: String) = Palettes.firstOrNull { it.id == id } ?: WaDay

/** The theme in use and whether it renders dark (Original follows the system). */
class Themed(val p: Palette, val dark: Boolean)
val LocalTheme = staticCompositionLocalOf { Themed(WaDay, false) }

val Accent @Composable get() = LocalTheme.current.p.accent
val EmberBrush @Composable get() = Brush.linearGradient(LocalTheme.current.p.brush)
val isDark @Composable get() = LocalTheme.current.dark

/** Material slots; surface is the card made opaque so sheets and menus stay readable. */
fun schemeFor(p: Palette, dark: Boolean) = when {
    p.dark == null && dark -> darkColorScheme(primary = p.accent, background = c(0xFF000000), surface = c(0xFF1C1C1E),
        surfaceVariant = c(0xFF2C2C2E), onBackground = Color.White, onSurface = Color.White, error = AppleRed)
    p.dark == null -> lightColorScheme(primary = p.accent, background = c(0xFFF2F2F7), surface = Color.White,   // systemGroupedBackground + cards
        surfaceVariant = c(0xFFE5E5EA), onBackground = Color.Black, onSurface = Color.Black, error = AppleRed)
    dark -> darkColorScheme(primary = p.accent, background = p.bg, surface = p.card.compositeOver(p.bg), surfaceVariant = p.fill,
        onBackground = Color.White, onSurface = Color.White, error = AppleRed)
    else -> lightColorScheme(primary = p.accent, background = p.bg, surface = p.card.compositeOver(p.bg), surfaceVariant = p.fill,
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
private fun iosType(font: FontFamily) = Typography(
    bodyLarge = TextStyle(fontFamily = font, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    bodyMedium = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePipeTheme(theme: String = "wa", live: Boolean = false, content: @Composable () -> Unit) {
    val p = paletteOf(theme).forSystem(isSystemInDarkTheme())
    val dark = p.dark ?: isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {          // status bar icons follow the theme, not the system
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark }
    }
    val clock = rememberSceneClock(live)
    val host = remember { SceneHost(clock) }
    val type = remember(p.look.font) { iosType(p.look.font) }
    MaterialTheme(colorScheme = schemeFor(p, dark), typography = type) {
        // No Material ripples anywhere: iOS gives press feedback by dimming/scaling (see iosTap).
        CompositionLocalProvider(LocalRippleConfiguration provides null, LocalTheme provides Themed(p, dark), LocalScene provides host,
            content = content)
    }
}
