package com.aura.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aura.player.data.prefs.ThemeMode

/**
 * Strict black & white "glass" system: near-black canvas with translucent
 * white panels in dark mode, near-white canvas with translucent black panels
 * in light mode. Zero accent color — artwork is the only color on screen.
 */
private val Ink = Color(0xFF0A0A0B)
private val Paper = Color(0xFFFAFAFA)

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color(0xFFF5F5F4),
    surface = Color(0x14FFFFFF), // white @ 8% — glass panel
    onSurface = Color(0xFFF5F5F4),
    surfaceVariant = Color(0x0DFFFFFF), // white @ 5%
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Color(0x14FFFFFF),
    surfaceContainerHigh = Color(0x1FFFFFFF),
    surfaceContainerHighest = Color(0x29FFFFFF),
    outline = Color(0x2EFFFFFF), // white @ 18%
    outlineVariant = Color(0x1AFFFFFF), // hairline white @ 10%
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    scrim = Color(0xB30A0A0B),
)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Color(0x0A000000), // black @ 4%
    onSurface = Ink,
    surfaceVariant = Color(0x06000000),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainer = Color(0x0A000000),
    surfaceContainerHigh = Color(0x14000000),
    surfaceContainerHighest = Color(0x1F000000),
    outline = Color(0x2E000000),
    outlineVariant = Color(0x1A000000),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    scrim = Color(0xB3FAFAFA),
)

/** Tight, high-contrast type scale; default system font for zero startup cost. */
private val AuraTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

@Composable
fun AuraTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AuraTypography,
        shapes = AuraShapes,
        content = content,
    )
}
