package com.cheatbuddy.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The watch accent (mint) is used as the seed for the non-dynamic fallback palettes. */
val WatchAccent = Color(0xFF6FE3C1)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8EF7D6),
    onPrimaryContainer = Color(0xFF002118),
    secondary = Color(0xFF4B635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DC),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF406377),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4E8FF),
    onTertiaryContainer = Color(0xFF001E2D),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE4EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
)

private val DarkColors = darkColorScheme(
    primary = WatchAccent,
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFF8EF7D6),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1D352D),
    secondaryContainer = Color(0xFF344C43),
    onSecondaryContainer = Color(0xFFCDE9DC),
    tertiary = Color(0xFFA8CBE2),
    onTertiary = Color(0xFF0E3446),
    tertiaryContainer = Color(0xFF284B5E),
    onTertiaryContainer = Color(0xFFC4E8FF),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CheatBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        content = content,
    )
}
