package com.voiceprompter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.voiceprompter.data.ThemeMode

private val Amber = Color(0xFFF6AD55)
private val AmberDim = Color(0xFF7A4F1F)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF9AA5B1),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Color(0xFFBFC4CC),
    outline = Color(0xFF3A3A42),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = AmberDim,
    onPrimary = Color.White,
    secondary = Color(0xFF4A5568),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF14161A),
    surface = Color.White,
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFEFEFF2),
    onSurfaceVariant = Color(0xFF45474D),
    outline = Color(0xFFD2D4D9)
)

// A single type scale override: generous line height, because most text in this
// app is read at arm's length from a lectern rather than held close.
private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun VoicePrompterTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
