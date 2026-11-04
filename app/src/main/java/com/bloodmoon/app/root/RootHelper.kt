package com.bloodmoon.app.root

import android.content.Context
import android.content.pm.PackageManager
import com.bloodmoon.app.util.GrantResult
import com.topjohnwu.superuser.Shell

/**
 * All calls here can block for a while (su can prompt Magisk/KernelSU for a grant on first use -
 * that prompt is automatic, built into libsu/Magisk itself, nothing extra to implement for it),
 * so every function in this object must be called from a background dispatcher - never from the
 * main thread / directly inside a @Composable body.
 */
object RootHelper {

    /** Blocking. Call from Dispatchers.IO only. May show the Magisk/KernelSU grant prompt on first call. */
    fun isRootAvailable(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    /** Blocking. Call from Dispatchers.IO only. Returns the actual command output/error for diagnostics. */
    fun executeRootCommand(cmd: String): Pair<Boolean, String> {
        return try {
            val result = Shell.cmd(cmd).exec()
            result.isSuccess to result.out.joinToString("\n").ifBlank { result.err.joinToString("\n") }
        } catch (e: Exception) {
            false to "${e::class.java.simpleName}: ${e.message}"
        }
    }

    /**
     * Grants this app WRITE_SECURE_SETTINGS via root, the same permission ADB can grant with
     * `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`. Once granted it persists like
     * any other runtime permission - this only needs to run once, not on every filter toggle.
     */
    fun grantWriteSecureSettingsPermission(context: Context): GrantResult {
        if (hasWriteSecureSettings(context)) return GrantResult.Granted

        if (!isRootAvailable()) {
            return GrantResult.Failed("Root not available (Shell.getShell().isRoot returned false - check that Magisk/KernelSU granted access to Blood Moon)")
        }

        val (success, output) = executeRootCommand(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        )
        if (!success) {
            return GrantResult.Failed("Comando pm grant falhou: $output")
        }

        if (hasWriteSecureSettings(context)) return GrantResult.Granted

        // Same stale-permission-cache issue as the Shizuku path: the grant likely worked at the
        // PackageManager level, but this already-running process's cached permission snapshot
        // may not reflect it yet.
        return GrantResult.NeedsAppRestart
    }

    /**
     * Pushes brightness below the OS-enforced minimum. Real minimum on most devices is > 0 even
     * though Settings.System.SCREEN_BRIGHTNESS accepts 0..255; a value of 1-3 typically still
     * reads as "off" to the panel on many phones, so we do not go below [MIN_SAFE_BRIGHTNESS].
     */
    fun setBrightnessBelowFloor(percent: Int): Boolean {
        val clamped = percent.coerceIn(0, 100)
        val value = (MIN_SAFE_BRIGHTNESS + (clamped / 100f) * (NORMAL_FLOOR - MIN_SAFE_BRIGHTNESS)).toInt()
        return executeRootCommand("settings put system screen_brightness $value").first &&
                executeRootCommand("settings put system screen_brightness_mode 0").first
    }

    private fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private const val MIN_SAFE_BRIGHTNESS = 1
    private const val NORMAL_FLOOR = 10
}
