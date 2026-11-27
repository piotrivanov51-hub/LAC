package com.fashionstudio.aipro.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object StudioColors {
    // Primary Editorial Dark Palette (Exact Specification)
    val DarkBackground = Color(0xFF101114)
    val DarkSurface = Color(0xFF191B20)
    val DarkSurfaceSecondary = Color(0xFF24262C)
    val DarkPrimaryText = Color(0xFFF5F5F5)
    val DarkSecondaryText = Color(0xFF9CA3AF)
    val AccentGold = Color(0xFFC9A66B)
    val SuccessGreen = Color(0xFF22C55E)
    val WarningAmber = Color(0xFFF59E0B)
    val ErrorRed = Color(0xFFEF4444)
    val InfoBlue = Color(0xFF38BDF8)

    // Full Editorial Light Palette
    val LightBackground = Color(0xFFF7F7F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceSecondary = Color(0xFFECECE7)
    val LightPrimaryText = Color(0xFF141519)
    val LightSecondaryText = Color(0xFF525660)
    val LightAccentGold = Color(0xFF9E7B3E)
}

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioColors.AccentGold,
    onPrimary = StudioColors.DarkBackground,
    secondary = StudioColors.DarkSurfaceSecondary,
    onSecondary = StudioColors.DarkPrimaryText,
    background = StudioColors.DarkBackground,
    onBackground = StudioColors.DarkPrimaryText,
    surface = StudioColors.DarkSurface,
    onSurface = StudioColors.DarkPrimaryText,
    surfaceVariant = StudioColors.DarkSurfaceSecondary,
    onSurfaceVariant = StudioColors.DarkSecondaryText,
    error = StudioColors.ErrorRed,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = StudioColors.LightAccentGold,
    onPrimary = Color.White,
    secondary = StudioColors.LightSurfaceSecondary,
    onSecondary = StudioColors.LightPrimaryText,
    background = StudioColors.LightBackground,
    onBackground = StudioColors.LightPrimaryText,
    surface = StudioColors.LightSurface,
    onSurface = StudioColors.LightPrimaryText,
    surfaceVariant = StudioColors.LightSurfaceSecondary,
    onSurfaceVariant = StudioColors.LightSecondaryText,
    error = StudioColors.ErrorRed,
    onError = Color.White
)

private val StudioTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
)

@Composable
fun FashionStudioTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme,
        typography = StudioTypography,
        content = content
    )
}
