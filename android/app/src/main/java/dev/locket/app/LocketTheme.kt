package dev.locket.app

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocketThemeColors = darkColorScheme(
    primary = Color(0xFF9BD7C6), onPrimary = Color(0xFF10201A),
    secondary = Color(0xFFB7CCBF), onSecondary = Color(0xFF17201B),
    background = Color(0xFF111311), onBackground = Color(0xFFE8EEE9),
    surface = Color(0xFF1A201C), onSurface = Color(0xFFE8EEE9),
    surfaceVariant = Color(0xFF29322D), onSurfaceVariant = Color(0xFFB8C4BB),
)

val LocketTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Light),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)
