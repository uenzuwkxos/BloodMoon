package com.bloodmoon.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.bloodmoon.app.data.model.AppThemeMode

private const val DEFAULT_ACCENT_HEX = "#C41E3A"

fun parseColor(hex: String, fallback: Color = DarkCrimson): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(colorInt or 0xFF000000)
        } else {
            Color(colorInt)
        }
    } catch (_: Exception) {
        fallback
    }
}

/** Readable text/icon on [color] (black or white, whichever has more contrast). */
private fun onColorFor(color: Color): Color =
    if (color.luminance() > 0.35f) Color(0xFF2B0008) else Color.White

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun lightScheme(primary: Color): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onColorFor(primary),
    primaryContainer = LightPalette.primaryContainer,
    onPrimaryContainer = LightPalette.onPrimaryContainer,
    inversePrimary = LightPalette.inversePrimary,
    secondary = LightPalette.secondary,
    onSecondary = Color.White,
    secondaryContainer = LightPalette.secondaryContainer,
    onSecondaryContainer = LightPalette.onSecondaryContainer,
    tertiary = LightPalette.tertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightPalette.tertiaryContainer,
    onTertiaryContainer = LightPalette.onTertiaryContainer,
    background = LightPalette.background,
    onBackground = LightPalette.onBackground,
    surface = LightPalette.surface,
    onSurface = LightPalette.onSurface,
    surfaceVariant = LightPalette.surfaceVariant,
    onSurfaceVariant = LightPalette.onSurfaceVariant,
    surfaceTint = primary,
    inverseSurface = LightPalette.inverseSurface,
    inverseOnSurface = LightPalette.inverseOnSurface,
    error = LightPalette.error,
    onError = Color.White,
    errorContainer = LightPalette.errorContainer,
    onErrorContainer = LightPalette.onErrorContainer,
    outline = LightPalette.outline,
    outlineVariant = LightPalette.outlineVariant,
    scrim = Color.Black,
    surfaceBright = LightPalette.surfaceBright,
    surfaceDim = LightPalette.surfaceDim,
    surfaceContainerLowest = LightPalette.containerLowest,
    surfaceContainerLow = LightPalette.containerLow,
    surfaceContainer = LightPalette.container,
    surfaceContainerHigh = LightPalette.containerHigh,
    surfaceContainerHighest = LightPalette.containerHighest
)

private fun darkScheme(primary: Color): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onColorFor(primary),
    primaryContainer = DarkPalette.primaryContainer,
    onPrimaryContainer = DarkPalette.onPrimaryContainer,
    inversePrimary = DarkPalette.inversePrimary,
    secondary = DarkPalette.secondary,
    onSecondary = DarkPalette.onSecondary,
    secondaryContainer = DarkPalette.secondaryContainer,
    onSecondaryContainer = DarkPalette.onSecondaryContainer,
    tertiary = DarkPalette.tertiary,
    onTertiary = DarkPalette.onTertiary,
    tertiaryContainer = DarkPalette.tertiaryContainer,
    onTertiaryContainer = DarkPalette.onTertiaryContainer,
    background = DarkPalette.background,
    onBackground = DarkPalette.onBackground,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onSurface,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceVariant,
    surfaceTint = primary,
    inverseSurface = DarkPalette.inverseSurface,
    inverseOnSurface = DarkPalette.inverseOnSurface,
    error = DarkPalette.error,
    onError = DarkPalette.onError,
    errorContainer = DarkPalette.errorContainer,
    onErrorContainer = DarkPalette.onErrorContainer,
    outline = DarkPalette.outline,
    outlineVariant = DarkPalette.outlineVariant,
    scrim = Color.Black,
    surfaceBright = DarkPalette.surfaceBright,
    surfaceDim = DarkPalette.surfaceDim,
    surfaceContainerLowest = DarkPalette.containerLowest,
    surfaceContainerLow = DarkPalette.containerLow,
    surfaceContainer = DarkPalette.container,
    surfaceContainerHigh = DarkPalette.containerHigh,
    surfaceContainerHighest = DarkPalette.containerHighest
)

private fun blackScheme(primary: Color): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onColorFor(primary),
    primaryContainer = BlackPalette.primaryContainer,
    onPrimaryContainer = BlackPalette.onPrimaryContainer,
    inversePrimary = BlackPalette.inversePrimary,
    secondary = BlackPalette.secondary,
    onSecondary = BlackPalette.onSecondary,
    secondaryContainer = BlackPalette.secondaryContainer,
    onSecondaryContainer = BlackPalette.onSecondaryContainer,
    tertiary = BlackPalette.tertiary,
    onTertiary = BlackPalette.onTertiary,
    tertiaryContainer = BlackPalette.tertiaryContainer,
    onTertiaryContainer = BlackPalette.onTertiaryContainer,
    background = BlackPalette.background,
    onBackground = BlackPalette.onBackground,
    surface = BlackPalette.surface,
    onSurface = BlackPalette.onSurface,
    surfaceVariant = BlackPalette.surfaceVariant,
    onSurfaceVariant = BlackPalette.onSurfaceVariant,
    surfaceTint = Color.Transparent, // no elevation tint: black stays black
    inverseSurface = BlackPalette.inverseSurface,
    inverseOnSurface = BlackPalette.inverseOnSurface,
    error = BlackPalette.error,
    onError = BlackPalette.onError,
    errorContainer = BlackPalette.errorContainer,
    onErrorContainer = BlackPalette.onErrorContainer,
    outline = BlackPalette.outline,
    outlineVariant = BlackPalette.outlineVariant,
    scrim = Color.Black,
    surfaceBright = BlackPalette.surfaceBright,
    surfaceDim = BlackPalette.surfaceDim,
    surfaceContainerLowest = BlackPalette.containerLowest,
    surfaceContainerLow = BlackPalette.containerLow,
    surfaceContainer = BlackPalette.container,
    surfaceContainerHigh = BlackPalette.containerHigh,
    surfaceContainerHighest = BlackPalette.containerHighest
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodMoonTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    primaryAccentHex: String = DEFAULT_ACCENT_HEX,
    content: @Composable () -> Unit
) {
    val resolvedMode = when (themeMode) {
        AppThemeMode.SYSTEM -> if (isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.LIGHT
        else -> themeMode
    }
    val isDark = resolvedMode != AppThemeMode.LIGHT

    val colorScheme = remember(resolvedMode, primaryAccentHex) {
        val isDefaultAccent = primaryAccentHex.equals(DEFAULT_ACCENT_HEX, ignoreCase = true)
        val custom = parseColor(primaryAccentHex, DarkCrimson)
        // Default accent: brand crimson on Light, a lightened version on the dark themes
        // (contrast). Custom accent: respected as is on any theme.
        when (resolvedMode) {
            AppThemeMode.LIGHT -> lightScheme(if (isDefaultAccent) DarkCrimson else custom)
            AppThemeMode.PURE_BLACK -> blackScheme(if (isDefaultAccent) CrimsonOnDark else custom)
            else -> darkScheme(if (isDefaultAccent) CrimsonOnDark else custom)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    // Stronger focus/press feedback on all Material components (the default 10% focus barely shows
    // on a TV from 3 meters away): focus 30%, press 24%, in the accent color.
    val rippleConfiguration = remember(colorScheme.primary) {
        RippleConfiguration(
            color = colorScheme.primary,
            rippleAlpha = RippleAlpha(
                draggedAlpha = 0.20f,
                focusedAlpha = 0.30f,
                hoveredAlpha = 0.12f,
                pressedAlpha = 0.24f
            )
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
            content()
        }
    }
}

/**
 * Surface for dialogs (AlertDialog and the like): always one step above the screen's cards, so the
 * dialog looks like it floats above them. On Light the cards are already white, so the dialog uses
 * pure white (surfaceContainerLowest); on the dark ones it uses surfaceContainerHigh. Use as
 * `AlertDialog(containerColor = dialogSurfaceColor(), ...)`.
 */
@Composable
fun dialogSurfaceColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() >= 0.5f) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh
}
