package com.dhanuk.lofiga.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Lofiga Brand Colors (M3 expressive tonal palette around the purple seed #993DF5)
// Light/dark schemes are derived from these via the M3 tonal-spot mapping below.
// All names below are kept as stable public API — re-exports of the underlying
// tonal values used across the app. Editing the hex here propagates everywhere.
val Purple500 = Color(0xFF993DF5)   // primary seed (M3 tone-40 in light, tone-80 in dark)
val Purple400 = Color(0xFFC6A0FF)   // soft accent (was 0xFFB060F7)
val Purple700 = Color(0xFF7226D6)   // deep accent (was 0xFF7C2FD4)
val Cyan400   = Color(0xFF22D8C0)   // secondary teal (was 0xFF3DF5E6 — more saturated for contrast)
val Cyan200   = Color(0xFF73F0DE)   // secondary light
val DarkBg    = Color(0xFF110718)   // darker plum-black for stronger contrast
val DarkSurface = Color(0xFF1B1126) // surface lifted clearly above background
val DarkSurfaceHighlight = Color(0xFF2B1B3D) // clearer separation from surface
val White12 = Color(0x1FFFFFFF)
val White38 = Color(0x61FFFFFF)
val White60 = Color(0x99FFFFFF)

// Light theme colors
val LightBg      = Color(0xFFFBF7FF)   // warm off-white, slight lavender cast
val LightSurface = Color(0xFFFFFBFF)   // nearly pure white surface

data class AppColors(
    val surface: Color,
    val surfaceHighlight: Color,
    val bg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        surface = DarkSurface,
        surfaceHighlight = DarkSurfaceHighlight,
        bg = DarkBg,
        textPrimary = Color.White,
        textSecondary = White60,
        textTertiary = White38,
        outline = White38,
    )
}

private val LightAppColors = AppColors(
    surface = Color(0xFFFFFBFF),
    surfaceHighlight = Color(0xFFEDE2F5),     // clearly differentiated from surface
    bg = Color(0xFFFBF7FF),
    textPrimary = Color(0xFF1D0B2A),
    textSecondary = Color(0xFF49454F),
    textTertiary = Color(0xFF7A7585),
    outline = Color(0xFF7A7585),             // raised — outline was barely visible before
)

private val LofigaDarkColorScheme = darkColorScheme(
    primary = Purple500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF581CA8),     // deep purple container
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Cyan400,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF005044),
    onSecondaryContainer = Color(0xFF73F0DE),
    tertiary = Purple400,
    onTertiary = Color(0xFF382059),
    tertiaryContainer = Color(0xFF4F3878),
    onTertiaryContainer = Color(0xFFEADDFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBg,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceHighlight,
    onSurfaceVariant = White60,
    outline = White38,                          // was White12 — barely visible before
    outlineVariant = White12,
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF322F38),      // unused-but-complete
    inversePrimary = Color(0xFFC6A0FF),
    surfaceTint = Purple500,                    // M3 dynamic surface tinting source
)

private val LofigaLightColorScheme = lightColorScheme(
    primary = Purple700,                        // use deeper purple on light for contrast
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF380971),
    secondary = Color(0xFF006B5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF74F8E3),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF6F43C1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF260059),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBg,
    onBackground = Color(0xFF1D0B2A),
    surface = LightSurface,
    onSurface = Color(0xFF1D0B2A),
    surfaceVariant = Color(0xFFEDE2F5),         // clearly differentiated from surface
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A7585),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF322F38),
    inverseOnSurface = Color(0xFFE6E1E9),
    inversePrimary = Color(0xFF993DF5),
    surfaceTint = Purple500,
)

val LofigaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

val LofigaTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp)
)

private val DarkAppColors = AppColors(
    surface = DarkSurface,
    surfaceHighlight = DarkSurfaceHighlight,
    bg = DarkBg,
    textPrimary = Color.White,
    textSecondary = White60,
    textTertiary = White38,
    outline = White38,              // was White12 — outline was near-invisible before
)

@Composable
fun LofigaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LofigaDarkColorScheme else LofigaLightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = LofigaShapes,
            typography = LofigaTypography,
            content = content
        )
    }
}