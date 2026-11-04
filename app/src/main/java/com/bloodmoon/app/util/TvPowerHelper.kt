package com.bloodmoon.app.util

import android.content.Context
import android.provider.Settings

/**
 * Automatic standby (inactivity time until the TV really goes to sleep) and "Timed power-off"
 * (turns the screen off at the set time, TV-only - see util/DeviceType.kt).
 *
 * ## Automatic standby: why Settings.Secure.SLEEP_TIMEOUT, not SCREEN_OFF_TIMEOUT
 *
 * There are two candidate system keys for "time until the screen turns off": -
 * Settings.System.SCREEN_OFF_TIMEOUT: inactivity time until the screen goes dark/starts the
 * daydream - it does NOT actually shut the device down, it is just the regular screen timeout. -
 * Settings.Secure.SLEEP_TIMEOUT (the official constant is "sleep_timeout", hidden/@hide in the
 * public API, but writable via Settings.Secure.putInt normally with WRITE_SECURE_SETTINGS - the
 * same pattern already used by SystemColorAdjustment for other @hide keys): a ceiling on how long
 * the device may stay "dreaming" (daydream/screensaver mode) before it really goes to sleep for
 * good. Documented in the AOSP source itself as "usually should be greater than SCREEN_OFF_TIMEOUT,
 * otherwise the device falls asleep before it gets a chance to dream".
 *
 * Manually tested by the user on two different Android 11 TVs: SLEEP_TIMEOUT really turns the TV
 * off at the configured time; SCREEN_OFF_TIMEOUT had no effect at all. This is consistent with
 * Android 11's own behavior on those TVs: "screen off" and "TV off" are the same event in that
 * system - there is no equivalent here of what a phone does (screen goes dark, system keeps running
 * behind it). SCREEN_OFF_TIMEOUT may work on newer TVs that make this distinction, but that was not
 * tested nor is it the case for the available hardware - only SLEEP_TIMEOUT is used here.
 *
 * ## Timed power-off: why simulate the power button
 *
 * There is no unprivileged way (not even with WRITE_SECURE_SETTINGS) to shut the device down
 * programmatically without being a system app - the real way used by apps such as TV launchers is
 * to simulate the physical power button itself (`input keyevent 26`, KEYCODE_POWER), which is the
 * same event the remote control sends. This is the same end result that SLEEP_TIMEOUT produces on
 * inactivity (see above) - there is no "softer" version that only turns the screen off while
 * leaving the rest running; on this generation of Android TV, turning off and shutting down are the
 * same event. It is a TOGGLE, not an absolute "turn off": sent while the screen is already off, it
 * turns it back ON - which is why turnOffScreenIfOn below checks the current state first (via
 * PowerManager.isInteractive(), the official unprivileged API for that) and only sends the keyevent
 * if the screen is actually on.
 */
object TvPowerHelper {
    private const val KEY_SLEEP_TIMEOUT = "sleep_timeout"

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun getSleepTimeoutMinutes(context: Context): Int? {
        return try {
            val ms = Settings.Secure.getInt(context.contentResolver, KEY_SLEEP_TIMEOUT, -1)
            if (ms <= 0) null else ms / 60000
        } catch (_: Exception) {
            null
        }
    }

    /** [minutes] null turns standby off (goes back to SLEEP_TIMEOUT = -1, "never"), like the system default. */
    fun setSleepTimeoutMinutes(context: Context, minutes: Int?): Boolean {
        if (!hasPermission(context)) return false
        return try {
            val ms = if (minutes == null) -1 else minutes * 60000
            Settings.Secure.putInt(context.contentResolver, KEY_SLEEP_TIMEOUT, ms)
            true
        } catch (_: Exception) {
            false
        }
    }
    /**
     * Turns the TV screen off if it is on. Tries, in order: 1. Root/Shizuku: SLEEP key (223) - it
     * only sleeps, never "turns on" a dark screen like POWER (26), which is a toggle; 2.
     * Accessibility service (global "lock screen" action) - works with ADB only, without Root or
     * Shizuku (the shell is not available in that case); 3. Root/Shizuku: POWER key (26), for TVs
     * that ignore SLEEP. After each attempt it checks whether the screen really went off before
     * moving on to the next. Before, only step 3 existed and only with Root/Shizuku - with ADB
     * nothing happened.
     */
    suspend fun turnOffScreenIfOn(context: Context, useRoot: Boolean): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            ?: return false
        if (!powerManager.isInteractive) return true // already off - nothing to do, not a failure

        suspend fun screenIsOff(): Boolean {
            kotlinx.coroutines.delay(1000)
            return !powerManager.isInteractive
        }

        suspend fun shell(command: String): Boolean {
            return if (useRoot) {
                val (success, _) = com.bloodmoon.app.root.RootHelper.executeRootCommand(command)
                success
            } else {
                val result = com.bloodmoon.app.shizuku.ShizukuHelper.execShellCommand(context, command)
                result !is ShellResult.Failed
            }
        }

        // 1) SLEEP via shell
        if (shell("input keyevent 223") && screenIsOff()) return true

        // 2) Accessibility (turns the service on by itself if WRITE_SECURE_SETTINGS exists)
        AccessibilityServiceHelper.ensureEnabled(context)
        var waited = 0
        while (com.bloodmoon.app.service.BloodMoonAccessibilityService.instance == null && waited < 3000) {
            kotlinx.coroutines.delay(250)
            waited += 250
        }
        if (com.bloodmoon.app.service.BloodMoonAccessibilityService.lockScreen() && screenIsOff()) return true

        // 3) POWER via shell
        return shell("input keyevent 26") && screenIsOff()
    }
}
