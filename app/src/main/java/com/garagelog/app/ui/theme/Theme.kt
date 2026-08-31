package com.garagelog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val GarageLogColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentOnAccent,
    secondary = Accent2,
    onSecondary = AccentOnAccent,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    outline = Border,
    error = Danger,
    onError = TextPrimary,
)

private val GarageLogTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun GarageLogTheme(content: @Composable () -> Unit) {
    // Dark theme only — the PWA never had a light variant, this app doesn't need one either.
    MaterialTheme(
        colorScheme = GarageLogColorScheme,
        typography = GarageLogTypography,
        content = content,
    )
}
