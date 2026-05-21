package com.ssafy.culture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ssafy.culture.R

private val LightColors = lightColorScheme(
    primary = Color(0xFFA62C53),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFF7197),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFD5668B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEC2CF),
    onSecondaryContainer = Color(0xFF6B3249),
    tertiary = Color(0xFFE0A400),
    onTertiary = Color(0xFF472C00),
    tertiaryContainer = Color(0xFFFCCF5A),
    onTertiaryContainer = Color(0xFF3F2A00),
    background = Color(0xFFFFF4F6),
    onBackground = Color(0xFF492136),
    surface = Color(0xFFFFECF2),
    onSurface = Color(0xFF492136),
    surfaceVariant = Color(0xFFFFD0E3),
    onSurfaceVariant = Color(0xFF7A5565),
    surfaceContainer = Color(0xFFFFE0EB),
    surfaceContainerLow = Color(0xFFFFECF2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFFD69DB6),
    surfaceTint = Color(0xFFA62C53),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFAFC7),
    onPrimary = Color(0xFF5F1730),
    primaryContainer = Color(0xFFC24E74),
    onPrimaryContainer = Color(0xFFFFF2F5),
    secondary = Color(0xFFFFC5D5),
    onSecondary = Color(0xFF5F2039),
    secondaryContainer = Color(0xFF6A3044),
    onSecondaryContainer = Color(0xFFFFE7EE),
    tertiary = Color(0xFFFFE18D),
    onTertiary = Color(0xFF473200),
    tertiaryContainer = Color(0xFF715100),
    onTertiaryContainer = Color(0xFFFFF0BC),
    background = Color(0xFF27131D),
    onBackground = Color(0xFFFFE8EF),
    surface = Color(0xFF321722),
    onSurface = Color(0xFFFFE8EF),
    surfaceVariant = Color(0xFF6E4153),
    onSurfaceVariant = Color(0xFFE8C1CF),
    surfaceContainer = Color(0xFF43212E),
    surfaceContainerLow = Color(0xFF3A1C28),
    surfaceContainerLowest = Color(0xFF2B141E),
    outlineVariant = Color(0xFF9C7082),
    surfaceTint = Color(0xFFFFAFC7),
)

private val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_extra_light, FontWeight.ExtraLight),
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semi_bold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extra_bold, FontWeight.ExtraBold),
    Font(R.font.pretendard_black, FontWeight.Black),
)

private fun TextStyle.withPretendard(letterSpacing: TextUnit = 0.sp): TextStyle = copy(
    fontFamily = PretendardFontFamily,
    letterSpacing = letterSpacing,
)

private val DefaultTypography = Typography()

private val CultureTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.ExtraBold,
    ).withPretendard(letterSpacing = (-0.7).sp),
    displayMedium = TextStyle(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.ExtraBold,
    ).withPretendard(letterSpacing = (-0.6).sp),
    displaySmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold,
    ).withPretendard(letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold,
    ).withPretendard(letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ).withPretendard(letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
    ).withPretendard(letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ).withPretendard(letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ).withPretendard(letterSpacing = (-0.3).sp),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ).withPretendard(letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    ).withPretendard(letterSpacing = (-0.3).sp),
    bodyMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ).withPretendard(letterSpacing = (-0.2).sp),
    bodySmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ).withPretendard(letterSpacing = (-0.2).sp),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ).withPretendard(letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ).withPretendard(letterSpacing = (-0.1).sp),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ).withPretendard(),
)

@Composable
fun CultureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CultureTypography,
        content = content,
    )
}
