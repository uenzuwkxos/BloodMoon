package com.bloodmoon.app.util

/**
 * Bridge between the accessibility service (which receives, with no polling at all, an event on
 * every window change) and the filter service (which decides what stays on in each app: filter,
 * grayscale and system filter). Without the accessibility service, the filter service falls back to
 * periodic UsageStats queries (which require the "Usage access" permission).
 */
object ForegroundAppTracker {
    @Volatile
    var listener: ((String) -> Unit)? = null

    fun report(packageName: String) {
        listener?.invoke(packageName)
    }
}
