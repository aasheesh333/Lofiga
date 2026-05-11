package com.dhanuk.lofiga.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Lofiga Brand Colors
val Purple500 = Color(0xFF993DF5)
val Purple400 = Color(0xFFB060F7)
val Purple700 = Color(0xFF7C2FD4)
val Cyan400 = Color(0xFF3DF5E6)
val Cyan200 = Color(0xFF80F8EF)
val DarkBg = Color(0xFF191022)
val DarkSurface = Color(0xFF231B2E)
val DarkSurfaceHighlight = Color(0xFF2D243A)
val White12 = Color(0x1FFFFFFF)
val White38 = Color(0x61FFFFFF)
val White60 = Color(0x99FFFFFF)

// Light theme colors
val LightBg = Color(0xFFF5F0FA)
val LightSurface = Color(0xFFFFFFFF)

private val LofigaDarkColorScheme = darkColorScheme(
    primary = Purple500,
    secondary = Cyan400,
    tertiary = Purple400,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceHighlight,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = White60,
    outline = White12
)

private val LofigaLightColorScheme = lightColorScheme(
    primary = Purple500,
    secondary = Cyan400,
    tertiary = Purple700,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = Color(0xFFF0EAF5),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFCAC4D0)
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

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = LofigaShapes,
        typography = LofigaTypography,
        content = content
    )
}