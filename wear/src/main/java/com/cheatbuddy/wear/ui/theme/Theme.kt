package com.cheatbuddy.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.cheatbuddy.wear.ui.components.LocalAmbientState

/** One accent (mint) on true black. Secondary/tertiary roles reuse the accent family to keep it calm. */
private val Mint = Color(0xFF6FE3C1)
private val MintDim = Color(0xFF4FC7A5)
private val MintContainer = Color(0xFF0F4A3C)
private val OnMint = Color(0xFF00382B)
private val OnMintContainer = Color(0xFFB8F5E2)

val CheatBuddyColors = ColorScheme(
    primary = Mint,
    primaryDim = MintDim,
    primaryContainer = MintContainer,
    onPrimary = OnMint,
    onPrimaryContainer = OnMintContainer,
    secondary = Color(0xFFB4CCC3),
    secondaryDim = Color(0xFF98B0A7),
    secondaryContainer = Color(0xFF1F2E2A),
    onSecondary = Color(0xFF1F352E),
    onSecondaryContainer = Color(0xFFD0E8DE),
    tertiary = Mint,
    tertiaryDim = MintDim,
    tertiaryContainer = MintContainer,
    onTertiary = OnMint,
    onTertiaryContainer = OnMintContainer,
    surfaceContainerLow = Color(0xFF0E1312),
    surfaceContainer = Color(0xFF161D1B),
    surfaceContainerHigh = Color(0xFF212A27),
    onSurface = Color(0xFFE6EFEB),
    onSurfaceVariant = Color(0xFFB7C5BF),
    outline = Color(0xFF7B8984),
    outlineVariant = Color(0xFF38433F),
    background = Color.Black,
    onBackground = Color(0xFFE6EFEB),
)

/** Ambient: no fills, no accent, grey text, so very few pixels are lit. */
private val AmbientColors = ColorScheme(
    primary = Color(0xFFD0D0D0),
    primaryDim = Color(0xFFA0A0A0),
    primaryContainer = Color.Black,
    onPrimary = Color.Black,
    onPrimaryContainer = Color(0xFFD0D0D0),
    secondary = Color(0xFFB0B0B0),
    secondaryDim = Color(0xFF909090),
    secondaryContainer = Color.Black,
    onSecondary = Color.Black,
    onSecondaryContainer = Color(0xFFB0B0B0),
    tertiary = Color(0xFFD0D0D0),
    tertiaryDim = Color(0xFFA0A0A0),
    tertiaryContainer = Color.Black,
    onTertiary = Color.Black,
    onTertiaryContainer = Color(0xFFD0D0D0),
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    onSurface = Color(0xFFD0D0D0),
    onSurfaceVariant = Color(0xFF9A9A9A),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF303030),
    background = Color.Black,
    onBackground = Color(0xFFD0D0D0),
)

@Composable
fun CheatBuddyWearTheme(content: @Composable () -> Unit) {
    val ambient = LocalAmbientState.current.isAmbient
    MaterialTheme(colorScheme = if (ambient) AmbientColors else CheatBuddyColors, content = content)
}
