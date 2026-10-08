package dev.livepipe.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Apple system palette.
val AppleBlue = Color(0xFF007AFF)
val AppleRed = Color(0xFFFF3B30)
val AppleGreen = Color(0xFF34C759)

private val Light = lightColorScheme(
    primary = AppleBlue,
    background = Color(0xFFF2F2F7),   // systemGroupedBackground
    surface = Color(0xFFFFFFFF),      // card
    surfaceVariant = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    error = AppleRed,
)

private val Dark = darkColorScheme(
    primary = AppleBlue,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = AppleRed,
)

/** iOS-ish rounded corners. */
object AppleShapes {
    val card = RoundedCornerShape(14.dp)
    val capsule = RoundedCornerShape(50)
}

@Composable
fun LivePipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content,
    )
}
