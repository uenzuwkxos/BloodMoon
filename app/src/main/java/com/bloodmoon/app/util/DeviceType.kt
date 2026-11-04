package com.bloodmoon.app.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Detects whether the app is running on an Android TV. Used both by FilterOverlayService (to decide
 * the size of the overlay window - see the comment in ensureOverlayViewExists) and by the "Timed
 * power-off"/"Automatic standby" UI in Options, which only make sense on a TV (a TV is really
 * turned off by those commands; a phone/tablet only turns the screen off and keeps running - that
 * is why both features are explicitly announced as TV-only).
 *
 * FEATURE_LEANBACK is the official signal recommended by the Android documentation for "this is a
 * TV" - preferred over UiModeManager because it is the most current documented way.
 */
object DeviceType {
    fun isTv(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
