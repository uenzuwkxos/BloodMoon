package com.bloodmoon.app.util

import android.graphics.Color
import com.bloodmoon.app.data.model.FilterColor
import com.bloodmoon.app.data.model.FilterConfig

/**
 * Single source of truth for turning a [FilterConfig] into the color that gets painted on
 * screen - shared by [com.bloodmoon.app.service.FilterOverlayService] (the real system overlay)
 * and the live preview in HomeScreen, so the preview never lies about what the real filter does.
 *
 * The color tint can reach full opacity (100%) on its own via [FilterConfig.colorIntensity].
 * Real contrast/color-channel adjustment that reaches beyond what a translucent overlay can do
 * lives in SystemColorAdjustment.kt instead.
 */
object FilterMath {

    // Used to be COLOR_WHITE (#FFFFFF) - replaced by pure black on request: instead of "vivid white
    // darkening to black as the tone goes down" (which is what the other 3 colors do, and which
    // made no sense for white, since it already IS the absence of tint), Black uses the tone as a
    // TRANSPARENCY control - 0% is transparent (no visible filter), 100% is solid black. See the
    // note in computeToneColor/computeOverlayColor on how this is handled as a special case.
    private val COLOR_BLACK_FILTER = Color.rgb(0, 0, 0)
    private val COLOR_RED = Color.rgb(255, 0, 0)
    // Explicit request: #FFFF00 (pure yellow), replacing the previous #FFC800 (which had already
    // been corrected from the original "mustard" #FF9800, but was still not a pure yellow).
    private val COLOR_YELLOW = Color.rgb(255, 255, 0)
    private val COLOR_ORANGE = Color.rgb(255, 128, 0)

    /** The "100% vivid" color of each option, before tone/dimming are applied. */
    fun baseColorFor(color: FilterColor): Int = when (color) {
        FilterColor.BLACK -> COLOR_BLACK_FILTER
        FilterColor.RED -> COLOR_RED
        FilterColor.ORANGE -> COLOR_ORANGE
        FilterColor.YELLOW -> COLOR_YELLOW
    }

    private data class Layer(val color: Int, val alpha: Float)

    /**
     * Darkens the base color toward pure black as [toneIntensity] goes from 100 down to 0 - 100
     * is the most vivid/strong version of the color, 0 is pure black. This is a plain linear RGB
     * scale-toward-black, so it changes what color the tint layer *is* rather than how much of
     * it shows through (that's [FilterConfig.colorIntensity]'s job instead).
     *
     * Not used for [FilterColor.BLACK]: its base color is already pure black, so scaling RGB
     * toward black would be a no-op regardless of toneIntensity - see computeOverlayColor, which
     * treats tone as the tint layer's alpha instead for that one color.
     */
    fun computeToneColor(baseColor: Int, toneIntensity: Int): Int {
        val t = (toneIntensity / 100f).coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(baseColor) * t).toInt().coerceIn(0, 255),
            (Color.green(baseColor) * t).toInt().coerceIn(0, 255),
            (Color.blue(baseColor) * t).toInt().coerceIn(0, 255)
        )
    }

        private fun tintLayer(config: FilterConfig): Layer {
        val baseHue = baseColorFor(config.color)
        return if (config.color == FilterColor.BLACK) {
            // Black: the tone becomes opacity (the base color is already black).
            Layer(
                baseHue,
                ((config.toneIntensity / 100f) * (config.colorIntensity / 100f)).coerceIn(0f, 1f)
            )
        } else {
            Layer(
                computeToneColor(baseHue, config.toneIntensity),
                (config.colorIntensity / 100f).coerceIn(0f, 1f)
            )
        }
    }

    private fun toArgb(color: Int, alpha: Float): Int = Color.argb(
        Math.round(alpha.coerceIn(0f, 1f) * 255f).coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    /**
     * Color (ARGB) of ONE filter layer - it is what each of the service's overlay windows draws.
     * With Double filter on, the service creates two identical windows, one over the other, and the
     * system compositor does the sum by itself.
     */
    fun computeLayerColor(config: FilterConfig): Int {
        val layer = tintLayer(config)
        return toArgb(layer.color, layer.alpha)
    }

    /**
     * ARGB color of the result of the stacked layers (1, or 2 with Double filter) - equivalent to
     * what the service's windows produce together. Used by the Home screen preview.
     */
    fun computeOverlayColor(config: FilterConfig): Int {
        val layer = tintLayer(config)
        val layers = if (config.doubleFilterEnabled) listOf(layer, layer) else listOf(layer)
        val (finalColor, finalAlpha) = flatten(layers)
        return toArgb(finalColor, finalAlpha)
    }

    /** "Source over" composition of the layers, from bottom to top, into a single (color, alpha) pair. */
/** Standard "source over" compositing, flattening layers bottom-to-top into one (color, alpha) pair. */
    private fun flatten(layers: List<Layer>): Pair<Int, Float> {
        var outA = 0f
        var outR = 0f
        var outG = 0f
        var outB = 0f
        for (layer in layers) {
            val srcA = layer.alpha.coerceIn(0f, 1f)
            if (srcA <= 0f) continue
            val srcR = Color.red(layer.color) / 255f
            val srcG = Color.green(layer.color) / 255f
            val srcB = Color.blue(layer.color) / 255f

            val newA = srcA + outA * (1f - srcA)
            if (newA <= 0f) {
                outA = 0f
                continue
            }
            outR = (srcR * srcA + outR * outA * (1f - srcA)) / newA
            outG = (srcG * srcA + outG * outA * (1f - srcA)) / newA
            outB = (srcB * srcA + outB * outA * (1f - srcA)) / newA
            outA = newA
        }
        val color = Color.rgb(
            (outR * 255).toInt().coerceIn(0, 255),
            (outG * 255).toInt().coerceIn(0, 255),
            (outB * 255).toInt().coerceIn(0, 255)
        )
        return color to outA
    }

    /**
     * Per-channel (R, G, B) multiplication factors for the SurfaceFlinger filter. Each layer
     * multiplies the screen by (1 - alpha + alpha * channel); with Double filter there are two
     * identical layers, i.e. the factor squared (same result as the two overlay windows).
     */
    fun computeMultiplyFactors(config: FilterConfig): FloatArray {
        val layer = tintLayer(config)
        val a = layer.alpha.coerceIn(0f, 1f)
        fun channel(c: Int): Float {
            val single = a * (c / 255f) + (1f - a)
            return if (config.doubleFilterEnabled) single * single else single
        }
        return floatArrayOf(
            channel(Color.red(layer.color)),
            channel(Color.green(layer.color)),
            channel(Color.blue(layer.color))
        )
    }
}
