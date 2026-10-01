package dev.locket.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocketCoral = androidx.compose.ui.graphics.Color(0xFFE8756D)
val LocketOnCoral = androidx.compose.ui.graphics.Color(0xFF3B0706)
val LocketCoralContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
val LocketOnCoralContainer = androidx.compose.ui.graphics.Color(0xFF410002)
val LocketPlum = androidx.compose.ui.graphics.Color(0xFF77525A)
val LocketOnPlum = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
val LocketLightBackground = androidx.compose.ui.graphics.Color(0xFFFFF8F7)
val LocketLightSurface = androidx.compose.ui.graphics.Color(0xFFFFF8F7)
val LocketLightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFF5DEDB)
val LocketLightInk = androidx.compose.ui.graphics.Color(0xFF201A19)
val LocketLightMuted = androidx.compose.ui.graphics.Color(0xFF857370)
val LocketLightOutline = androidx.compose.ui.graphics.Color(0xFF857370)
val LocketLightOutlineVariant = androidx.compose.ui.graphics.Color(0xFFD8C2BF)
val LocketCoralDark = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
val LocketDarkInk = androidx.compose.ui.graphics.Color(0xFF690005)
val LocketCoralDarkContainer = androidx.compose.ui.graphics.Color(0xFF93000A)
val LocketOnCoralDarkContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
val LocketPlumDark = androidx.compose.ui.graphics.Color(0xFFE6BDC3)
val LocketDarkBackground = androidx.compose.ui.graphics.Color(0xFF201A19)
val LocketDarkSurface = androidx.compose.ui.graphics.Color(0xFF201A19)
val LocketDarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF534341)
val LocketDarkText = androidx.compose.ui.graphics.Color(0xFFEDE0DE)
val LocketDarkMuted = androidx.compose.ui.graphics.Color(0xFFD8C2BF)
val LocketDarkOutline = androidx.compose.ui.graphics.Color(0xFFA08C89)
val LocketDarkOutlineVariant = androidx.compose.ui.graphics.Color(0xFF534341)

private val LocketLightColors = lightColorScheme(
    primary = LocketCoral,
    onPrimary = LocketOnCoral,
    primaryContainer = LocketCoralContainer,
    onPrimaryContainer = LocketOnCoralContainer,
    secondary = LocketPlum,
    onSecondary = LocketOnPlum,
    background = LocketLightBackground,
    onBackground = LocketLightInk,
    surface = LocketLightSurface,
    onSurface = LocketLightInk,
    surfaceVariant = LocketLightSurfaceVariant,
    onSurfaceVariant = LocketLightMuted,
    outline = LocketLightOutline,
    outlineVariant = LocketLightOutlineVariant,
)

private val LocketDarkColors = darkColorScheme(
    primary = LocketCoralDark,
    onPrimary = LocketDarkInk,
    primaryContainer = LocketCoralDarkContainer,
    onPrimaryContainer = LocketOnCoralDarkContainer,
    secondary = LocketPlumDark,
    onSecondary = LocketDarkInk,
    background = LocketDarkBackground,
    onBackground = LocketDarkText,
    surface = LocketDarkSurface,
    onSurface = LocketDarkText,
    surfaceVariant = LocketDarkSurfaceVariant,
    onSurfaceVariant = LocketDarkMuted,
    outline = LocketDarkOutline,
    outlineVariant = LocketDarkOutlineVariant,
)

val LocketTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LocketTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) LocketDarkColors else LocketLightColors
    }
    MaterialTheme(colorScheme = colors, typography = LocketTypography, content = content)
}
