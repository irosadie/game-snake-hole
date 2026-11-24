package com.binarydev.startergame.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.binarydev.startergame.R

val FredokaFontFamily: FontFamily by lazy {
    FontFamily(Font(R.font.fredoka, FontWeight.Normal))
}

val PlusJakartaSansFontFamily: FontFamily by lazy {
    FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
}

/**
 * Single-accent theme configuration so developers can re-skin the entire starter game
 * by changing `accentColor`, `fontFamily`, or `appName` without touching core or ViewModels.
 */
data class StarterThemeConfig(
    val appName: String = "Gecko Out",
    val accentColor: Color = Color(0xFF0D9488),
    val secondaryAccentColor: Color = Color(0xFF0F766E),
    val backgroundColor: Color = Color(0xFFF8FAFC),
    val surfaceColor: Color = Color(0xFFFFFFFF),
    val fontFamily: FontFamily = FontFamily.Default,
    val displayFontFamily: FontFamily = fontFamily
)

fun geckoOutThemeConfig(): StarterThemeConfig = StarterThemeConfig(
    appName = "Gecko Out",
    accentColor = Color(0xFF1FAF75),
    secondaryAccentColor = Color(0xFF138055),
    backgroundColor = Color(0xFFF4F8FB),
    surfaceColor = Color(0xFFFFFFFF),
    fontFamily = PlusJakartaSansFontFamily,
    displayFontFamily = FredokaFontFamily
)

fun buildStarterTypography(
    fontFamily: FontFamily = FontFamily.Default,
    displayFontFamily: FontFamily = fontFamily
): Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun StarterGameTheme(
    themeConfig: StarterThemeConfig = StarterThemeConfig(),
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary = themeConfig.accentColor,
        onPrimary = Color.White,
        primaryContainer = themeConfig.accentColor.copy(alpha = 0.14f),
        onPrimaryContainer = themeConfig.secondaryAccentColor,
        secondary = themeConfig.secondaryAccentColor,
        onSecondary = Color.White,
        background = themeConfig.backgroundColor,
        surface = themeConfig.surfaceColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildStarterTypography(
            fontFamily = themeConfig.fontFamily,
            displayFontFamily = themeConfig.displayFontFamily
        ),
        content = content
    )
}
