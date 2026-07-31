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

// ═══════════════════════════════════════════════════════════════════════════════
// Lofiga v2 Premium Design System
// Light-first, pure white surfaces, single deep indigo accent, 1px outlines.
// Inspired by 2026 Material 3 expressive + top Play Store utility apps.
// ═══════════════════════════════════════════════════════════════════════════════

// ── Brand colours ──────────────────────────────────────────────────────────────
val Indigo       = Color(0xFF1F3A8A)   // primary accent — deep indigo
val IndigoLight  = Color(0xFFB6C4FF)   // dark-theme primary (lighter indigo)
val IndigoDark   = Color(0xFF002270)   // on-primary-container in light
val IndigoContainer = Color(0xFFDCE1FF) // primary container (light)

// Neutral scale
val Ink          = Color(0xFF111111)   // text-primary
val Gray         = Color(0xFF666666)   // text-secondary
val GrayLight    = Color(0xFF999999)   // text-tertiary
val Hairline     = Color(0xFFE0E0E0)   // 1px outline (slightly stronger than #EEE)
val Subtle       = Color(0xFFF5F5F7)   // surface variant / subtle bg (warm light grey)
val PureWhite    = Color(0xFFFFFFFF)   // surface
val PageBg       = Color(0xFFFAFAFB)   // off-white page background for layering

// Dark-theme neutrals
val DarkBg       = Color(0xFF121318)
val DarkSurface  = Color(0xFF1A1B21)
val DarkSurfaceHi = Color(0xFF2F3036)
val DarkHairline = Color(0xFF444651)
val DarkInk      = Color(0xFFE6E1E9)
val DarkGray     = Color(0xFF999999)
val DarkGrayLt   = Color(0xFF666666)

// Error
val ErrorRed     = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)

// ── Backward-compat aliases (old code references these names) ──────────────────
val Purple500    = Indigo
val Purple400    = IndigoLight
val Purple700    = IndigoDark
val Cyan400      = Gray
val Cyan200      = GrayLight
val DarkSurfaceHighlight = DarkSurfaceHi
val White12      = Color(0x1F9E9E9E)
val White38      = Color(0x619E9E9E)
val White60      = Color(0x999E9E9E)
val LightBg      = PureWhite
val LightSurface = PureWhite

// ── App-level colour tokens (consumed via LocalAppColors) ──────────────────────
data class AppColors(
    val surface: Color,
    val surfaceHighlight: Color,
    val bg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,
    val pageBg: Color = bg,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        surface = PureWhite,
        surfaceHighlight = Subtle,
        bg = PageBg,
        textPrimary = Ink,
        textSecondary = Gray,
        textTertiary = GrayLight,
        outline = Hairline,
        pageBg = PageBg,
    )
}

private val LightAppColors = AppColors(
    surface = PureWhite,
    surfaceHighlight = Subtle,
    bg = PageBg,
    textPrimary = Ink,
    textSecondary = Gray,
    textTertiary = GrayLight,
    outline = Hairline,
    pageBg = PageBg,
)

private val DarkAppColors = AppColors(
    surface = DarkSurface,
    surfaceHighlight = DarkSurfaceHi,
    bg = DarkBg,
    textPrimary = DarkInk,
    textSecondary = DarkGray,
    textTertiary = DarkGrayLt,
    outline = DarkHairline,
    pageBg = DarkBg,
)

// ── M3 Color Schemes ───────────────────────────────────────────────────────────
private val LofigaLightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = PureWhite,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoDark,
    secondary = Gray,
    onSecondary = PureWhite,
    secondaryContainer = Subtle,
    onSecondaryContainer = Gray,
    tertiary = GrayLight,
    onTertiary = PureWhite,
    tertiaryContainer = Subtle,
    onTertiaryContainer = GrayLight,
    error = ErrorRed,
    onError = PureWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFF93000A),
    background = PageBg,
    onBackground = Ink,
    surface = PureWhite,
    onSurface = Ink,
    surfaceVariant = Subtle,
    onSurfaceVariant = Gray,
    outline = Hairline,
    outlineVariant = Color(0xFFF5F5F7),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = IndigoLight,
    surfaceTint = Indigo,
    surfaceContainer = PureWhite,
    surfaceContainerHigh = Subtle,
    surfaceContainerHighest = Subtle,
    surfaceContainerLow = PureWhite,
    surfaceContainerLowest = PageBg,
    surfaceDim = Subtle,
    surfaceBright = PureWhite,
)

private val LofigaDarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = IndigoDark,
    primaryContainer = Color(0xFF274191),
    onPrimaryContainer = IndigoContainer,
    secondary = DarkGray,
    onSecondary = PureWhite,
    secondaryContainer = DarkSurfaceHi,
    onSecondaryContainer = DarkGray,
    tertiary = DarkGrayLt,
    onTertiary = PureWhite,
    tertiaryContainer = DarkSurfaceHi,
    onTertiaryContainer = DarkGrayLt,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceHi,
    onSurfaceVariant = DarkGray,
    outline = DarkHairline,
    outlineVariant = Color(0xFF444651),
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Indigo,
    surfaceTint = IndigoLight,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHi,
    surfaceContainerHighest = DarkSurfaceHi,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkBg,
    surfaceDim = DarkBg,
    surfaceBright = DarkSurfaceHi,
)

// ── Shapes ─────────────────────────────────────────────────────────────────────
val LofigaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ── Typography (Inter-like; Android default Roboto is visually equivalent) ─────
val LofigaTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.01).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

// ── Theme entry point ──────────────────────────────────────────────────────────
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
