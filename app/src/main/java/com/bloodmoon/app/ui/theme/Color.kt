package com.bloodmoon.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palettes of the three themes (Light, Dark, Black). They all start from the same DNA - slightly
 * reddish neutrals + crimson as the only accent color - but each one is designed as a complete
 * theme, not as a variation of another:
 *
 * - Light: warm off-white background, white cards, controls in a soft grayish pink. - Dark: reddish
 * graphite (NOT black) with light surface steps, so the cards "detach" from the background without
 * needing borders. - Black: true #000 background (AMOLED); cards, tracks, fields and other controls
 * in near-black tones (L* < 8), just enough to stand out from the background.
 *
 * All Material 3 roles (including surfaceContainer*, inverse*, scrim) are defined in Theme.kt, so
 * Switch, Slider, Checkbox, buttons, text fields, dialogs, menus and the navigation bar inherit
 * consistent colors without needing a fixed color on each screen.
 */

// ---- Marca -------------------------------------------------------------------------------
val DarkCrimson = Color(0xFFC41E3A)          // brand crimson (default in the Light theme)
val CrimsonOnDark = Color(0xFFE5405A)        // mesmo carmim, clareado p/ manter contraste >= 4:1 no escuro
val BloodRed = Color(0xFFE50914)

// ---- Claro -------------------------------------------------------------------------------
object LightPalette {
    val primaryContainer = Color(0xFFFFDADE)
    val onPrimaryContainer = Color(0xFF410010)
    val secondary = Color(0xFF7A5A5F)
    val secondaryContainer = Color(0xFFF4DEE0)
    val onSecondaryContainer = Color(0xFF2E1519)
    val tertiary = Color(0xFF7A5900)
    val tertiaryContainer = Color(0xFFFFDEA3)
    val onTertiaryContainer = Color(0xFF261900)

    val background = Color(0xFFF8F3F2)
    val onBackground = Color(0xFF231819)
    val surface = Color(0xFFFFFFFF)          // cards
    val onSurface = Color(0xFF231819)
    val surfaceVariant = Color(0xFFF1E7E6)   // linhas internas, chips, campos
    val onSurfaceVariant = Color(0xFF5A4A4B)
    val surfaceDim = Color(0xFFE6DCDB)
    val surfaceBright = Color(0xFFFFFFFF)
    val containerLowest = Color(0xFFFFFFFF)
    val containerLow = Color(0xFFFBF6F5)
    val container = Color(0xFFF5EDEC)
    val containerHigh = Color(0xFFEFE5E4)
    val containerHighest = Color(0xFFE8DCDB)
    val outline = Color(0xFF8A7778)
    val outlineVariant = Color(0xFFDDCFCF)

    val error = Color(0xFFBA1A1A)
    val errorContainer = Color(0xFFFFDAD6)
    val onErrorContainer = Color(0xFF410002)

    val inverseSurface = Color(0xFF382E2F)
    val inverseOnSurface = Color(0xFFFBEEEE)
    val inversePrimary = Color(0xFFFFB2BA)
}

// ---- Escuro ------------------------------------------------------------------------------
object DarkPalette {
    val primaryContainer = Color(0xFF5E1224)
    val onPrimaryContainer = Color(0xFFFFDADE)
    val secondary = Color(0xFFE6BDC2)
    val onSecondary = Color(0xFF45292D)
    val secondaryContainer = Color(0xFF4A3235)
    val onSecondaryContainer = Color(0xFFFFD9DD)
    val tertiary = Color(0xFFF1BF4D)
    val onTertiary = Color(0xFF3F2E00)
    val tertiaryContainer = Color(0xFF5C4300)
    val onTertiaryContainer = Color(0xFFFFDEA3)

    val background = Color(0xFF130F10)
    val onBackground = Color(0xFFEFE0E0)
    val surface = Color(0xFF1D1819)          // cards
    val onSurface = Color(0xFFEFE0E0)
    val surfaceVariant = Color(0xFF2B2324)
    val onSurfaceVariant = Color(0xFFCDB9BA)
    val surfaceDim = Color(0xFF130F10)
    val surfaceBright = Color(0xFF3A3132)
    val containerLowest = Color(0xFF0E0B0C)
    val containerLow = Color(0xFF181314)
    val container = Color(0xFF221C1D)
    val containerHigh = Color(0xFF2D2526)
    val containerHighest = Color(0xFF383031)
    val outline = Color(0xFF9C8688)
    val outlineVariant = Color(0xFF4D4041)

    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)

    val inverseSurface = Color(0xFFEFE0E0)
    val inverseOnSurface = Color(0xFF382E2F)
    val inversePrimary = Color(0xFFB3162F)
}

// ---- Preto (AMOLED) ----------------------------------------------------------------------
object BlackPalette {
    val primaryContainer = Color(0xFF3A0A16)
    val onPrimaryContainer = Color(0xFFFFDADE)
    val secondary = Color(0xFFE0B8BD)
    val onSecondary = Color(0xFF3B2226)
    val secondaryContainer = Color(0xFF211819)
    val onSecondaryContainer = Color(0xFFFFD9DD)
    val tertiary = Color(0xFFF1BF4D)
    val onTertiary = Color(0xFF3F2E00)
    val tertiaryContainer = Color(0xFF3A2A00)
    val onTertiaryContainer = Color(0xFFFFDEA3)

    val background = Color(0xFF000000)
    val onBackground = Color(0xFFEDE0E0)
    val surface = Color(0xFF0B090A)          // cards: near-black, just a hair above #000
    val onSurface = Color(0xFFEDE0E0)
    val surfaceVariant = Color(0xFF151213)
    val onSurfaceVariant = Color(0xFFC4B1B2)
    val surfaceDim = Color(0xFF000000)
    val surfaceBright = Color(0xFF1F1A1B)
    val containerLowest = Color(0xFF000000)
    val containerLow = Color(0xFF050404)
    val container = Color(0xFF0C0A0B)
    val containerHigh = Color(0xFF131011)
    val containerHighest = Color(0xFF1B1718)
    val outline = Color(0xFF6A5A5C)
    val outlineVariant = Color(0xFF241D1E)

    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF5C0007)
    val onErrorContainer = Color(0xFFFFDAD6)

    val inverseSurface = Color(0xFFEDE0E0)
    val inverseOnSurface = Color(0xFF2B2223)
    val inversePrimary = Color(0xFFB3162F)
}
