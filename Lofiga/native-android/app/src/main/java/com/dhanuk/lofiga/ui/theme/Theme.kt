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

// Grayscale color constants (replacing the old purple/cyan palette)
val Purple500 = Color(0xFFBDBDBD)
val Purple400 = Color(0xFF9E9E9E)
val Purple700 = Color(0xFF757575)
val Cyan400 = Color(0xFFE0E0E0)
val Cyan200 = Color(0xFFF5F5F5)
val DarkBg = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceHighlight = Color(0xFF2C2C2C)
val White12 = Color(0x1FFFFFFF)
val White38 = Color(0x61FFFFFF)
val White60 = Color(0x99FFFFFF)

// Light theme colors
val LightBg = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)

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
        outline = White12,
    )
}

private val LightAppColors = AppColors(
    surface = Color.White,
    surfaceHighlight = Color(0xFFF0F0F0),
    bg = LightBg,
    textPrimary = Color(0xFF1C1B1F),
    textSecondary = Color(0xFF616161),
    textTertiary = Color(0xFF9E9E9E),
    outline = Color(0xFFCCCCCC),
)

private val LofigaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    secondary = Color(0xFF9E9E9E),
    tertiary = Color(0xFFBDBDBD),
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceHighlight,
    onPrimary = Color(0xFF121212),
    onSecondary = Color(0xFF121212),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = White60,
    outline = White12
)

private val LofigaLightColorScheme = lightColorScheme(
    primary = Color(0xFF616161),
    secondary = Color(0xFF9E9E9E),
    tertiary = Color(0xFF757575),
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = Color(0xFFEEEEEE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFCCCCCC)
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

@Composable
fun LofigaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LofigaDarkColorScheme else LofigaLightColorScheme
    val appColors = if (darkTheme) AppColors(
        surface = DarkSurface,
        surfaceHighlight = DarkSurfaceHighlight,
        bg = DarkBg,
        textPrimary = Color.White,
        textSecondary = White60,
        textTertiary = White38,
        outline = White12,
    ) else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = LofigaShapes,
            typography = LofigaTypography,
            content = content
        )
    }
}
