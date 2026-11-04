package com.bloodmoon.app.util

import android.content.Context
import com.bloodmoon.app.data.model.AppSettings

/**
 * "Do I have elevated access?" - the single definition used by the whole app (Home, service, widget).
 *
 * Root and Shizuku count while they are turned on in Options. ADB is just the WRITE_SECURE_SETTINGS
 * permission granted once from the computer; it stays in the system until the app is uninstalled
 * and the app cannot revoke it. That is why ADB also has a logical switch
 * ([AppSettings.adbEnabled]): when off, the permission is still there, but the app ignores it - and
 * the advanced features show up as unavailable again, as the user expects.
 */
object AccessMode {
    fun adbActive(context: Context, settings: AppSettings): Boolean =
        settings.adbEnabled != false && SystemColorAdjustment.hasPermission(context)

    fun isConfigured(context: Context, settings: AppSettings): Boolean =
        settings.useRoot || settings.useShizuku || adbActive(context, settings)
}
