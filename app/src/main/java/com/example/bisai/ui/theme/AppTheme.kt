package com.example.bisai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BrandBlue = Color(0xFF0E73E3)
private val BrandBlueDark = Color(0xFF0A5CC2)
private val BrandAmber = Color(0xFFFFB300)
private val BrandSurface = Color(0xFFF6F8FC)
private val BrandOutline = Color(0xFFDEE3EB)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE9FF),
    onPrimaryContainer = BrandBlueDark,
    secondary = Color(0xFF4E5D78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDF8),
    onSecondaryContainer = Color(0xFF203243),
    tertiary = BrandAmber,
    onTertiary = Color(0xFF202020),
    background = BrandSurface,
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE7EDF8),
    onSurfaceVariant = Color(0xFF4E5D78),
    outline = BrandOutline,
    error = Color(0xFFB00020),
    onError = Color.White
)

private val AppTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(letterSpacing = 0.5.sp),
    headlineMedium = Typography().headlineMedium.copy(letterSpacing = 0.4.sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = Typography().bodyMedium.copy(lineHeight = 20.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}