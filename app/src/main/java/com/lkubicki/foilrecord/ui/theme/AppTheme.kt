package com.lkubicki.foilrecord.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Define the colors from our colors.xml for Compose
private val Primary = Color(0xFFFF8C42)
private val PrimaryDark = Color(0xFFE67E3B)
private val PrimaryDarker = Color(0xFFCC6A2F)

private val Secondary = Color(0xFF6B705C)
private val SecondaryDark = Color(0xFF565B49)
private val SecondaryDarker = Color(0xFF3F4236)

private val AccentLight = Color(0xFFFFB997)
private val AccentDark = Color(0xFFA68A7B)

private val BackgroundLight = Color(0xFFF8F4F1)
private val BackgroundDark = Color(0xFF2C2824)

private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceDark = Color(0xFF1F1B17)

private val TextPrimaryLight = Color(0xFF2C2824)
private val TextSecondaryLight = Color(0xFF6B705C)
private val TextPrimaryDark = Color(0xFFF8F4F1)
private val TextSecondaryDark = Color(0xFFB4B4A9)

private val ErrorColor = Color(0xFFCF6679)
private val SuccessColor = Color(0xFF7FA650)

// Define light theme colors
private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = Color.White,
    tertiary = AccentLight,
    onTertiary = TextPrimaryLight,
    tertiaryContainer = AccentLight,
    onTertiaryContainer = TextPrimaryLight,
    error = ErrorColor,
    errorContainer = ErrorColor.copy(alpha = 0.3f),
    onError = Color.White,
    onErrorContainer = ErrorColor,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = TextSecondaryLight
)

// Define dark theme colors
private val DarkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = TextPrimaryDark,
    primaryContainer = PrimaryDarker,
    onPrimaryContainer = Color.White,
    secondary = AccentDark,
    onSecondary = Color.White,
    secondaryContainer = SecondaryDarker,
    onSecondaryContainer = Color.White,
    tertiary = AccentDark,
    onTertiary = Color.White,
    tertiaryContainer = AccentDark,
    onTertiaryContainer = Color.White,
    error = ErrorColor,
    errorContainer = ErrorColor.copy(alpha = 0.3f),
    onError = Color.White,
    onErrorContainer = ErrorColor,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BackgroundDark.copy(alpha = 0.7f),
    onSurfaceVariant = TextSecondaryDark,
    outline = TextSecondaryDark
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}