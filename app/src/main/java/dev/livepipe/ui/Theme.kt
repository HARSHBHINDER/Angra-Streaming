package dev.livepipe.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Angra brand: an ember accent taken from the oni icon's flame, on Apple's system neutrals.
val Accent = Color(0xFFF2542D)
val EmberBrush = Brush.linearGradient(listOf(Color(0xFFFF2D3A), Color(0xFFFF8A00)))
val AppleRed = Color(0xFFFF3B30)
val AppleGreen = Color(0xFF34C759)

private val Light = lightColorScheme(
    primary = Accent,
    background = Color(0xFFF2F2F7),   // systemGroupedBackground
    surface = Color(0xFFFFFFFF),      // secondarySystemGroupedBackground (cards)
    surfaceVariant = Color(0xFFE5E5EA),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    error = AppleRed,
)

private val Dark = darkColorScheme(
    primary = Accent,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = AppleRed,
)

/** iOS semantic colors Material has no slot for. */
object Ios {
    val separator @Composable get() = if (isSystemInDarkTheme()) Color(0x99545458) else Color(0x4A3C3C43)
    val secondaryLabel @Composable get() = if (isSystemInDarkTheme()) Color(0x99EBEBF5) else Color(0x993C3C43)
    val segmentTrack @Composable get() = if (isSystemInDarkTheme()) Color(0x3D767680) else Color(0x1F767680)
    val segmentThumb @Composable get() = if (isSystemInDarkTheme()) Color(0xFF636366) else Color.White
    val switchOff @Composable get() = if (isSystemInDarkTheme()) Color(0xFF39393D) else Color(0xFFE9E9EA)
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
fun LivePipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = IosType,
    ) {
        // No Material ripples anywhere: iOS gives press feedback by dimming/scaling (see iosTap).
        CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
    }
}
