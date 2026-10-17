package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CockpitDarkColorScheme = darkColorScheme(
    primary = BmwCyan,
    onPrimary = TextDark,
    primaryContainer = BmwDeepBlue,
    onPrimaryContainer = TextPrimaryWhite,
    secondary = BmwPureBlue,
    onSecondary = TextPrimaryWhite,
    tertiary = BmwCrimson,
    onTertiary = TextPrimaryWhite,
    background = OledBlack,
    onBackground = TextPrimaryWhite,
    surface = CockpitSurface,
    onSurface = TextPrimaryWhite,
    surfaceVariant = CockpitSurfaceHigh,
    onSurfaceVariant = TextSecondaryGrey,
    outline = GlassCardBorder,
    outlineVariant = Color(0x334CC2FF),
    error = BmwCrimson,
    onError = TextPrimaryWhite
)

@Composable
fun VisionHudTheme(
    accentColor: Color = BmwCyan,
    content: @Composable () -> Unit
) {
    val dynamicScheme = CockpitDarkColorScheme.copy(
        primary = accentColor,
        outlineVariant = accentColor.copy(alpha = 0.25f)
    )

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = Typography,
        content = content
    )
}
