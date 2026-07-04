package com.lifeos.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

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

/** Semantic status colors used by package/finance/etc. surfaces (§7.1). */
data class LifeSemanticColors(
    val success: Color = Color(0xFF3E8E41),
    val warning: Color = Color(0xFFB88700),
    val info: Color = Color(0xFF2E6BB0),
)
