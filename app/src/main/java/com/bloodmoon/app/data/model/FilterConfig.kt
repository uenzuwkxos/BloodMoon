package com.bloodmoon.app.data.model

/**
 * The four color options of the main filter, in this order because it is the display order in the
 * UI (Black, Red, Orange, Yellow) - see FilterMath.baseColorFor for the actual colors.
 *
 * BLACK is a special case: the other three have [FilterConfig.toneIntensity] controlling how
 * vivid/dark the color ITSELF is (RGB scaling down to pure black as it goes down), but that makes
 * no sense for a color that already IS black - there is nowhere left to darken. For BLACK, tone
 * controls TRANSPARENCY instead: 0% is fully transparent (no visible filter), 100% is solid black.
 * See FilterMath.computeOverlayColor for the exact implementation.
 */
enum class FilterColor {
    BLACK,  // #000000 - tone controls transparency, not RGB (see the note above)
    RED,    // #FF0000
    ORANGE, // #FF8000
    YELLOW  // #FFFF00
}

/**
 * All intensities are 0..100.
 *
 * The color tint's [colorIntensity] can reach full opacity (100%) on its own - there is no
 * artificial cap. There used to be a second, separate "dimming" slider here (a plain black
 * layer blended on top of the tint, independent of color), but it was folded into tone/intensity
 * a while back and the UI has had no dimming control for some time now - [toneIntensity] already
 * reaches pure black at 0 for every color (transparency for BLACK specifically - see the note on
 * that enum value), so a second, separate "how black" axis was redundant. Any lingering
 * `dimmingIntensity` reference in old code/docs describes a field that no longer exists.
 *
 * There is no "contrast" or per-channel blue/green reduction here anymore: a translucent
 * overlay window can only ever fade the screen *toward* a color, which mathematically can only
 * ever reduce contrast, never increase it past whatever the system's own display calibration
 * already provides - so faking it here was cosmetic at best, misleading at worst. Real,
 * system-level color adjustment (the kind that actually reaches the status bar and quick
 * settings, and that can genuinely alter contrast) lives in SystemColorAdjustment.kt instead,
 * gated behind Root/Shizuku since it requires WRITE_SECURE_SETTINGS.
 *
 * [toneIntensity] controls how vivid vs. dark the base color itself is - 100 is the most
 * vibrant/strong version of the chosen color, and it linearly darkens toward pure black as it
 * goes down to 0 (except for [FilterColor.BLACK], where it controls transparency instead - see
 * the note on that enum value). See FilterMath.computeToneColor/computeOverlayColor for the
 * exact math shared with the live preview.
 *
 * [doubleFilterEnabled] ("Filtro duplo") composites the tint layer TWICE in a row instead of
 * once - same color, same intensity, stacked - which raises the effective final opacity via
 * standard "source over" math (1-(1-a)²  instead of just a) without needing a second, separate
 * slider. E.g. at 50% intensity, doubling takes the final alpha from 0.5 to 0.75 - a
 * meaningfully stronger tint, reachable without pushing the single intensity slider itself past
 * 100.
 */
data class FilterConfig(
    val color: FilterColor = FilterColor.RED,
    // Used to be separate redIntensity/yellowIntensity (one saved value per color, only one used at
    // a time) - simplified to a single slider now that there are 4 colors: one intensity value per
    // color does not scale well and most users only use one color at a time anyway.
    val colorIntensity: Int = 50,          // strength of the tint, whichever color is selected
    val toneIntensity: Int = 100,          // 100 = most vivid color, 0 = pure black (see BLACK note above)
    val doubleFilterEnabled: Boolean = false // duplica a camada de tonalidade - ver nota acima
)
