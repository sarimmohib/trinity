package com.sarim.trinity.ui.qwen_theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

/**
 * Qwen's Suggested Theme: "The Architect"
 *
 * Keeps the strict 8-color achromatic palette (no gradients, no hue) but changes
 * the UI language from "utilitarian UI kit" to "editorial / architectural".
 *
 * Key changes:
 * 1. TYPOGRAPHY: Serif for Display/Headlines (editorial weight), SansSerif for Body,
 *    Monospace for UI chrome/labels.
 * 2. SHAPES: 0.dp rounded corners everywhere. Sharp, intentional rectangles.
 * 3. LAYOUT: More vertical breathing room. Heavy 3.dp ink lines for major sections,
 *    thin indented lines for list items.
 */
object QwenPalette {
    // EXACT same values as the original Palette to respect the brief
    val Bone = Color(0xFFF1EDE4)
    val Paper = Color(0xFFFAF8F2)
    val Ink = Color(0xFF17160F)
    val Muted = Color(0xFF6D6A5C)
    val Faint = Color(0xFF9C9788)
    val Line = Color(0xFFC6BEA8)
    val LineSoft = Color(0xFFDCD6C8)
    val Wash = Color(0xFFE7E2D5)
}

private val QwenColors = lightColorScheme(
    primary = QwenPalette.Ink,
    onPrimary = QwenPalette.Paper,
    primaryContainer = QwenPalette.Wash,
    onPrimaryContainer = QwenPalette.Ink,
    secondary = QwenPalette.Muted,
    onSecondary = QwenPalette.Paper,
    background = QwenPalette.Bone,
    onBackground = QwenPalette.Ink,
    surface = QwenPalette.Paper,
    onSurface = QwenPalette.Ink,
    surfaceVariant = QwenPalette.Wash,
    onSurfaceVariant = QwenPalette.Muted,
    outline = QwenPalette.Line,
    outlineVariant = QwenPalette.LineSoft,
    error = QwenPalette.Ink,
    onError = QwenPalette.Paper,
)

private val QwenType = Typography(
    // Editorial Serif for major titles
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-2.0).sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        letterSpacing = (-1.5).sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
    ),

    // Clean SansSerif for body and titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),

    // Technical Monospace for UI chrome
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 2.0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun QwenTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = QwenColors,
        typography = QwenType,
        content = content,
    )
}
