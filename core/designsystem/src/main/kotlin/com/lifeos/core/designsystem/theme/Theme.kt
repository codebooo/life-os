package com.lifeos.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalLifeSemanticColors = staticCompositionLocalOf { LifeSemanticColors() }

/**
 * LifeOS theme (§7): Material 3 Expressive, dynamic color first (wallpaper),
 * dark-mode-first, expressive spring motion scheme, neutral fallback palette.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LifeOsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: String = PALETTE_DYNAMIC,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val seed = ThemePalettes[palette]
    val colorScheme = when {
        seed != null -> if (darkTheme) seededDarkScheme(seed) else seededLightScheme(seed)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    CompositionLocalProvider(LocalLifeSemanticColors provides LifeSemanticColors()) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = LifeOsTypography,
            content = content,
        )
    }
}
