package com.bloodmoon.app.util

import android.content.Context
import android.provider.Settings

/**
 * Real, system-level display adjustments - as opposed to FilterOverlayService's translucent
 * window, which by design cannot draw over the status bar, quick settings, or other system UI
 * (Android has deliberately blocked TYPE_APPLICATION_OVERLAY from covering those since Android
 * 8.0, as anti-hijacking protection - this is documented, intentional platform behavior, not a
 * bug we can work around from an app).
 *
 * These adjustments live here, all gated behind WRITE_SECURE_SETTINGS (grantable via
 * ADB/Root/Shizuku, never by a normal runtime permission prompt) and all genuinely applied by
 * the system compositor rather than an app window, so they reach every pixel on screen, status
 * bar and quick settings included:
 *
 * - Color inversion: the same "Color correction" accessibility feature visible in
 *   Settings > Accessibility > Color and motion. Flips the whole screen's colors.
 * - Grayscale: the platform's built-in Daltonizer set to simulate monochromacy - confirmed by
 *   reading github.com/C10udburst/Grayscaler's source, which uses this exact mechanism.
 *
 * Filtro via Sistema is NOT in that list - it's a different mechanism entirely, applying the
 * app's actual filter (color, intensity, tone, dimming) directly to SurfaceFlinger via the
 * `service call SurfaceFlinger 1015` transaction (see surfaceFlingerColorMatrixCommand below),
 * not through WRITE_SECURE_SETTINGS/Settings.Secure at all. A prior version of this tried
 * writing accessibility_display_color_matrix directly via Settings.Secure, expecting the
 * platform to pick it up the same way it applies Grayscale/inversion - that never worked: reading
 * ColorDisplayService.java (real AOSP source), that key isn't observed as a free-form input from
 * third parties. The service itself watches accessibility_display_daltonizer_enabled/
 * accessibility_display_inversion_enabled, builds ITS OWN matrix internally from those states,
 * and only then calls DisplayTransformManager.setColorMatrix() - which is what actually fires
 * transaction 1015. Writing the Settings.Secure key directly skips all of that; there's no
 * generic ContentObserver waiting for an arbitrary matrix there. Only Root/Shizuku (real shell
 * access) can fire that transaction directly.
 */
object SystemColorAdjustment {

    // Raw Settings.Secure keys - intentionally not referencing the (often @hide, version
    // dependent) SDK constants, since a raw string key is guaranteed to compile regardless of
    // which framework stubs a given compileSdk ships with.
    private const val KEY_COLOR_INVERSION_ENABLED = "accessibility_display_inversion_enabled"
    private const val KEY_REDUCE_BRIGHT_COLORS_ENABLED = "reduce_bright_colors_activated"

    // Grayscale uses the platform's built-in Daltonizer (color-blindness simulation/correction
    // feature) rather than any overlay or new permission. Setting daltonizer_enabled=1 with mode
    // 0 (DALTONIZER_SIMULATE_MONOCHROMACY) makes DisplayAdjustmentUtils (part of
    // AccessibilityManagerService, framework-internal) build a full-desaturation matrix and push
    // it to SurfaceFlinger - the exact same mechanism used by the Grayscaler app
    // (github.com/C10udburst/Grayscaler), confirmed by reading its source directly. Both keys
    // are plain ints, settable the same way as color inversion above - so this needs nothing
    // beyond the WRITE_SECURE_SETTINGS permission this app already gates Root/Shizuku/ADB on.
    private const val KEY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val KEY_DALTONIZER_MODE = "accessibility_display_daltonizer"
    private const val DALTONIZER_SIMULATE_MONOCHROMACY = 0

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isColorInversionEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, KEY_COLOR_INVERSION_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true on success. Requires [hasPermission] to already be true. */
    fun setColorInversionEnabled(context: Context, enabled: Boolean): Boolean {
        if (!hasPermission(context)) return false
        return try {
            Settings.Secure.putInt(context.contentResolver, KEY_COLOR_INVERSION_ENABLED, if (enabled) 1 else 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isExtraDimEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, KEY_REDUCE_BRIGHT_COLORS_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun setExtraDimEnabled(context: Context, enabled: Boolean): Boolean {
        if (!hasPermission(context)) return false
        return try {
            Settings.Secure.putInt(context.contentResolver, KEY_REDUCE_BRIGHT_COLORS_ENABLED, if (enabled) 1 else 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** URIs of the daltonizer keys, to observe changes made by the system. */
    fun grayscaleUris(): List<android.net.Uri> = listOf(
        Settings.Secure.getUriFor(KEY_DALTONIZER_ENABLED),
        Settings.Secure.getUriFor(KEY_DALTONIZER_MODE)
    )

    fun isGrayscaleEnabled(context: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(context.contentResolver, KEY_DALTONIZER_ENABLED, 0) == 1
            val mode = Settings.Secure.getInt(context.contentResolver, KEY_DALTONIZER_MODE, -1)
            enabled && mode == DALTONIZER_SIMULATE_MONOCHROMACY
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Grayscale, via ADB, Root, or Shizuku - all three can write plain int Settings.Secure keys.
     * Returns true on success. Requires [hasPermission] to already be true.
     */
    fun setGrayscaleEnabled(context: Context, enabled: Boolean): Boolean {
        if (!hasPermission(context)) return false
        return try {
            if (enabled) {
                Settings.Secure.putInt(context.contentResolver, KEY_DALTONIZER_MODE, DALTONIZER_SIMULATE_MONOCHROMACY)
                Settings.Secure.putInt(context.contentResolver, KEY_DALTONIZER_ENABLED, 1)
            } else {
                Settings.Secure.putInt(context.contentResolver, KEY_DALTONIZER_ENABLED, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Command `service call SurfaceFlinger 1015` (Binder transaction
     * SURFACE_FLINGER_TRANSACTION_COLOR_MATRIX, confirmed by reading the real AOSP source -
     * DisplayTransformManager.java, frameworks/base) to apply the channel multiplication matrix
     * equivalent to the app's main filter (tint + dimming).
     *
     * Why not Settings.Secure/accessibility_display_color_matrix (the previous approach, which had
     * no effect at all): that key is NOT observed as an arbitrary third-party input. Reading the
     * real AOSP ColorDisplayService.java, it is the service itself that observes
     * accessibility_display_daltonizer_enabled/accessibility_display_inversion_enabled, BUILDS the
     * matrix internally from those states, and only then calls
     * DisplayTransformManager.setColorMatrix() - which is what actually triggers Binder transaction
     * 1015. Writing straight to the Settings.Secure key triggers none of that: there is no generic
     * ContentObserver waiting for an arbitrary matrix there. The only way to apply a custom matrix
     * is to fire transaction 1015 directly - exactly what Red Moon does in Root mode (confirmed in
     * the repository's issue #150: LibreShift/red-moon).
     *
     * Confirmed syntax (tested, not just the old i32 format from 2017): `i32 1` turns the matrix
     * on, followed by 16 floats (`f <value>` each, no comma) in the same column-major order already
     * used throughout this class. `i32 0` (no floats) disables it and restores the identity matrix.
     */
    fun surfaceFlingerColorMatrixCommand(rFactor: Float, gFactor: Float, bFactor: Float): String {
        fun fmt(f: Float) = String.format(java.util.Locale.US, "%.4f", f.coerceIn(0f, 1f))
        val values = listOf(
            fmt(rFactor), "0", "0", "0",
            "0", fmt(gFactor), "0", "0",
            "0", "0", fmt(bFactor), "0",
            "0", "0", "0", "1"
        )
        val floatArgs = values.joinToString(" ") { "f $it" }
        return "service call SurfaceFlinger 1015 i32 1 $floatArgs"
    }

    /** Command to disable the compositor's matrix and go back to normal. */
    fun surfaceFlingerColorMatrixDisableCommand(): String {
        return "service call SurfaceFlinger 1015 i32 0"
    }
}
