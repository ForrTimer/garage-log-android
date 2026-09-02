package com.garagelog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared corner rounding for cards/pills/chips/buttons — replaces the old hard-edged look. */
val GarageCardShape = RoundedCornerShape(14.dp)
val GarageChipShape = RoundedCornerShape(10.dp)
val GaragePillShape = RoundedCornerShape(6.dp)
val GarageFabShape = RoundedCornerShape(18.dp)

private val DarkScheme = darkColorScheme(
    primary = DarkInverse,
    onPrimary = DarkOnInverse,
    secondary = DarkOk,
    onSecondary = DarkOnInverse,
    tertiary = DarkWarn,
    onTertiary = DarkOnInverse,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkPanel,
    onSurface = DarkText,
    surfaceVariant = DarkChrome,
    onSurfaceVariant = DarkTextDim,
    outline = DarkEdge,
    outlineVariant = DarkRule,
    error = DarkAlarm,
    onError = DarkOnInverse,
)

private val LightScheme = lightColorScheme(
    primary = LightInverse,
    onPrimary = LightOnInverse,
    secondary = LightOk,
    onSecondary = LightOnInverse,
    tertiary = LightWarn,
    onTertiary = LightOnInverse,
    background = LightBg,
    onBackground = LightText,
    surface = LightPanel,
    onSurface = LightText,
    surfaceVariant = LightChrome,
    onSurfaceVariant = LightTextDim,
    outline = LightEdge,
    outlineVariant = LightRule,
    error = LightAlarm,
    onError = LightOnInverse,
)

/**
 * Roles the redesign needs that Material3 has no slot for. Read these via
 * [garageColors] instead of importing raw Color values, or they won't follow the theme.
 */
data class GarageColors(
    val chrome: Color,
    val chromeEdge: Color,
    val textMuted: Color,
    val alarm: Color,
    val alarmText: Color,
    val warn: Color,
    val ok: Color,
    val info: Color,
    val pillTintAlpha: Float,
)

private val DarkExtras = GarageColors(
    chrome = DarkChrome,
    chromeEdge = DarkChromeEdge,
    textMuted = DarkTextMuted,
    alarm = DarkAlarm,
    alarmText = DarkAlarmText,
    warn = DarkWarn,
    ok = DarkOk,
    info = DarkInfo,
    pillTintAlpha = PillTintAlphaDark,
)

private val LightExtras = GarageColors(
    chrome = LightChrome,
    chromeEdge = LightChromeEdge,
    textMuted = LightTextMuted,
    alarm = LightAlarm,
    alarmText = LightAlarmText,
    warn = LightWarn,
    ok = LightOk,
    info = LightInfo,
    pillTintAlpha = PillTintAlphaLight,
)

private val LocalGarageColors = staticCompositionLocalOf { DarkExtras }

val garageColors: GarageColors
    @Composable @ReadOnlyComposable get() = LocalGarageColors.current

private val GarageLogTypography = Typography(
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp),
)

@Composable
fun GarageLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGarageColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = GarageLogTypography,
            content = content,
        )
    }
}
