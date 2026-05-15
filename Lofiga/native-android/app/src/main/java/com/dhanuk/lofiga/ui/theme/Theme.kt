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

// Light theme colors
val LightBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHighlight = Color(0xFFFFFFFF)

// Dark theme colors
val DarkBg = Color(0xFF1A1A1A)
val DarkSurface = Color(0xFF252525)
val DarkSurfaceHighlight = Color(0xFF333333)

// Opacity values for light mode (black on white)
val Black12 = Color(0x1F000000)
val Black38 = Color(0x61000000)
val Black60 = Color(0x99000000)

// Opacity values for dark mode (white on black)
val White12 = Color(0x1FFFFFFF)
val White38 = Color(0x61FFFFFF)
val White60 = Color(0x99FFFFFF)

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
        surface = LightSurface,
        surfaceHighlight = LightSurfaceHighlight,
        bg = LightBg,
        textPrimary = Color.Black,
        textSecondary = Black60,
        textTertiary = Black38,
        outline = Black12,
    )
}

private val LightAppColors = AppColors(
    surface = Color.White,
    surfaceHighlight = Color.White,
    bg = LightBg,
    textPrimary = Color.Black,
    textSecondary = Black60,
    textTertiary = Black38,
    outline = Black12,
)

private val DarkAppColors = AppColors(
    surface = DarkSurface,
    surfaceHighlight = DarkSurfaceHighlight,
    bg = DarkBg,
    textPrimary = Color.White,
    textSecondary = White60,
    textTertiary = White38,
    outline = White12,
)

private val LofigaDarkColorScheme = darkColorScheme(
    primary = Color.White,
    secondary = Color.White,
    tertiary = Color.White,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceHighlight,
    onPrimary = DarkBg,
    onSecondary = DarkBg,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = White60,
    outline = White12
)

private val LofigaLightColorScheme = lightColorScheme(
    primary = Color.Black,
    secondary = Color.Black,
    tertiary = Color.Black,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Black60,
    outline = Black12
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
