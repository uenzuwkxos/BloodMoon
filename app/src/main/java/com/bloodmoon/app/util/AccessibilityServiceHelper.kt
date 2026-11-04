package com.bloodmoon.app.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.root.RootHelper
import com.bloodmoon.app.service.BloodMoonAccessibilityService
import com.bloodmoon.app.shizuku.ShizukuHelper

/**
 * State and activation of the app's accessibility service ([BloodMoonAccessibilityService]).
 *
 * With the WRITE_SECURE_SETTINGS permission (the same one Root/Shizuku/ADB already grant for
 * grayscale) the app turns its own service on without the user having to go to the accessibility
 * settings. Without the permission, [ensureEnabled] returns false and the user can enable it
 * manually in Settings > Accessibility.
 */
object AccessibilityServiceHelper {

    private fun component(context: Context) =
        ComponentName(context, BloodMoonAccessibilityService::class.java)

    fun isEnabled(context: Context): Boolean {
        val enabled = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Exception) {
            null
        } ?: return false
        val me = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Turns the service on via Settings.Secure (preserves the other services already active). */
    fun ensureEnabled(context: Context): Boolean {
        if (isEnabled(context)) return true
        if (!SystemColorAdjustment.hasPermission(context)) return false
        return try {
            val me = component(context).flattenToString()
            val current = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val updated = if (current.isNullOrBlank()) me else "$current:$me"
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Turns the service on using the access the user has already given. With Root or Shizuku turned
     * on in Options (and without the WRITE_SECURE_SETTINGS permission yet), it requests the
     * permission now - before, only ADB worked, because only it already comes with the permission:
     * the accessibility path never requested it. If the permission has not "taken" in this process
     * yet (the app needs to restart), it writes the setting directly through the Root/Shizuku
     * shell.
     */
    suspend fun ensureEnabledPrivileged(context: Context, settings: AppSettings): Boolean {
        if (isEnabled(context)) return true
        if (!AccessMode.isConfigured(context, settings)) return false

        if (!SystemColorAdjustment.hasPermission(context)) {
            when {
                settings.useRoot -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RootHelper.grantWriteSecureSettingsPermission(context)
                }
                settings.useShizuku -> ShizukuHelper.grantWriteSecureSettingsPermission(context)
            }
        }
        if (ensureEnabled(context)) return true

        // Plan B: the shell writes the setting (it does not depend on the app's own permission).
        val flat = component(context).flattenToString()
        val script = "cur=\$(settings get secure enabled_accessibility_services); " +
            "case \"\$cur\" in *$flat*) ;; null|\"\") settings put secure enabled_accessibility_services $flat;; " +
            "*) settings put secure enabled_accessibility_services \"\$cur:$flat\";; esac; " +
            "settings put secure accessibility_enabled 1"
        when {
            settings.useRoot -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RootHelper.executeRootCommand(script)
            }
            settings.useShizuku -> ShizukuHelper.execShellCommand(context, script)
        }
        return isEnabled(context)
    }
}
