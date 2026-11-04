package com.bloodmoon.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.bloodmoon.app.util.ForegroundAppTracker
import com.bloodmoon.app.util.GrayscaleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal accessibility service (it does not read screen content: canRetrieveWindowContent = false).
 *
 * Why it exists: on Android 16 and on TVs, writing the daltonizer keys (grayscale) from a regular
 * service made the system turn them off right afterwards, while Grayscaler
 * (https://github.com/C10udburst/Grayscaler), which writes exactly the same keys, keeps grayscale
 * on. The difference between the two apps is that it runs as an active accessibility service - so
 * this service does the same: while it is active, the app has the same status and, on every window
 * change, grayscale is reasserted if something turned it off.
 *
 * It is also used as a last resort to turn the TV screen off (the global "lock screen" action) when
 * there is no Root/Shizuku to send a power key.
 */
class BloodMoonAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var startupJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        com.bloodmoon.app.util.DiagnosticLog.log(this, "accessibility connected")
        startupJob = scope.launch {
            GrayscaleController.refreshFromStorage(applicationContext)
            withContext(Dispatchers.Main) { GrayscaleController.reassertIfWanted(applicationContext) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        GrayscaleController.reassertIfWanted(applicationContext)
        reportForegroundApp(event)
    }

    private val inputMethodPackages: Set<String> by lazy {
        runCatching {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Reports the foreground app to the filter service (per-app exclusions). Transient windows
     * (status bar/panel, keyboard, system dialogs) are ignored so the exclusion does not "flicker";
     * the app's own events only count if they come from the main screen (the overlay window also
     * generates events with our package).
     */
    private fun reportForegroundApp(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg == "android" || pkg in inputMethodPackages) return
        if (pkg == packageName && event.className?.toString()?.endsWith("MainActivity") != true) return
        ForegroundAppTracker.report(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        com.bloodmoon.app.util.DiagnosticLog.log(this, "accessibility disconnected")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: BloodMoonAccessibilityService? = null
            private set

        /** Locks the screen (turns the display off). false if the service is not active/supported. */
        fun lockScreen(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
