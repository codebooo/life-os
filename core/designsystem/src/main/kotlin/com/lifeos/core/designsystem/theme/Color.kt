package com.lifeos.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Restrained neutral fallback schemes (§7.1). On the target device (S22 Ultra,
 * Android 13+) dynamic color from the wallpaper always wins; these only apply
 * on devices without dynamic color or in previews.
 */
private val FallbackPrimary = Color(0xFF4C662B)
private val FallbackOnPrimary = Color(0xFFFFFFFF)
private val FallbackPrimaryContainer = Color(0xFFCDEDA3)
private val FallbackOnPrimaryContainer = Color(0xFF102000)
private val FallbackSecondary = Color(0xFF586249)
private val FallbackTertiary = Color(0xFF386663)

internal val FallbackLightColorScheme = lightColorScheme(
    primary = FallbackPrimary,
    onPrimary = FallbackOnPrimary,
    primaryContainer = FallbackPrimaryContainer,
    onPrimaryContainer = FallbackOnPrimaryContainer,
    secondary = FallbackSecondary,
    tertiary = FallbackTertiary,
)

internal val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB1D18A),
    onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16),
    onPrimaryContainer = FallbackPrimaryContainer,
    secondary = Color(0xFFBFCBAD),
    tertiary = Color(0xFFA0D0CB),
)

/** Sentinel palette id: follow the wallpaper via Material You dynamic color. */
const val PALETTE_DYNAMIC = "dynamic"

/** Fixed seed palettes selectable in Settings — soft, muted pastels. */
val ThemePalettes: Map<String, Color> = linkedMapOf(
    "blue" to Color(0xFF8FB4E8),
    "red" to Color(0xFFE59AA0),
    "peach" to Color(0xFFF2C29B),
    "green" to Color(0xFF9FCBA6),
    "lavender" to Color(0xFFB9A7E0),
)

internal fun seededLightScheme(seed: Color): ColorScheme = lightColorScheme(
    primary = lerp(seed, Color.Black, 0.15f),
    onPrimary = Color.White,
    primaryContainer = lerp(seed, Color.White, 0.82f),
    onPrimaryContainer = lerp(seed, Color.Black, 0.65f),
    secondary = lerp(seed, Color(0xFF5F6368), 0.45f),
    onSecondary = Color.White,
    secondaryContainer = lerp(seed, Color.White, 0.72f),
    onSecondaryContainer = lerp(seed, Color.Black, 0.6f),
    tertiary = lerp(seed, Color(0xFF00838F), 0.5f),
    surfaceTint = lerp(seed, Color.Black, 0.15f),
)

internal fun seededDarkScheme(seed: Color): ColorScheme = darkColorScheme(
    primary = lerp(seed, Color.White, 0.45f),
    onPrimary = lerp(seed, Color.Black, 0.65f),
    primaryContainer = lerp(seed, Color.Black, 0.45f),
    onPrimaryContainer = lerp(seed, Color.White, 0.82f),
    secondary = lerp(seed, Color(0xFFDADCE0), 0.55f),
    onSecondary = lerp(seed, Color.Black, 0.7f),
    secondaryContainer = lerp(seed, Color.Black, 0.6f),
    onSecondaryContainer = lerp(seed, Color.White, 0.75f),
    tertiary = lerp(seed, Color(0xFF80DEEA), 0.45f),
    surfaceTint = lerp(seed, Color.White, 0.45f),
)

/** Semantic status colors used by package/finance/etc. surfaces (§7.1). */
data class LifeSemanticColors(
    val success: Color = Color(0xFF3E8E41),
    val warning: Color = Color(0xFFB88700),
    val info: Color = Color(0xFF2E6BB0),
)
