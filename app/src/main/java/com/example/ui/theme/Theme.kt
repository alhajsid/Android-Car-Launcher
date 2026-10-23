package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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
    scaleFactor: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val dynamicScheme = CockpitDarkColorScheme.copy(
        primary = accentColor,
        outlineVariant = accentColor.copy(alpha = 0.25f)
    )

    val currentDensity = LocalDensity.current
    val effectiveDensity = remember(currentDensity, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides effectiveDensity) {
        MaterialTheme(
            colorScheme = dynamicScheme,
            typography = Typography,
            content = content
        )
    }
}
