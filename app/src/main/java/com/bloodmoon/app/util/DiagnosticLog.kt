package com.bloodmoon.app.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight diagnostic log (a text file of at most ~48 KB in filesDir) to find out why the filter
 * vanishes "out of nowhere" on the TV. It keeps service events (start, end, restart by the system,
 * memory pressure, overlay lost/recreated, display change, unhandled failures) and, in the report,
 * adds the OFFICIAL history of why Android ended the process
 * (ActivityManager.getHistoricalProcessExitReasons, API 30+): LOW_MEMORY, SIGNALED, CRASH, FREEZER
 * etc. It is the information that tells whether it was memory, a crash or the TV's system killing
 * the app.
 */
object DiagnosticLog {
    private const val FILE_NAME = "diagnostic.log"
    private const val CLEARED_FILE_NAME = "diagnostic_cleared_at"
    private const val MAX_BYTES = 48_000
    private val lock = Any()

    private fun timestamp(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    fun log(context: Context, message: String) {
        runCatching {
            synchronized(lock) {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                if (file.length() > MAX_BYTES) {
                    val tail = file.readText().takeLast(MAX_BYTES / 2)
                    file.writeText(tail.substringAfter('\n', tail))
                }
                file.appendText("${timestamp()} $message\n")
            }
        }
    }

    /** When the user last cleared the log (0 = never). */
    private fun clearedAt(context: Context): Long = runCatching {
        File(context.applicationContext.filesDir, CLEARED_FILE_NAME).takeIf { it.exists() }
            ?.readText()?.trim()?.toLong()
    }.getOrNull() ?: 0L

    /**
     * Deletes the event log and also "zeroes" the process exits: Android's history cannot be
     * deleted by the app, so we store the moment of the clearing and the report then shows only
     * what happened after it.
     */
    fun clear(context: Context) {
        runCatching {
            synchronized(lock) {
                val dir = context.applicationContext.filesDir
                File(dir, FILE_NAME).delete()
                File(dir, CLEARED_FILE_NAME).writeText(System.currentTimeMillis().toString())
            }
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        else -> "UNKNOWN($reason)"
    }

    /** Official history of process exits (Android 11+). */
    private fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "(exit reasons: Android 11+)"
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val cleared = clearedAt(context)
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .filter { it.timestamp > cleared }
            if (exits.isEmpty()) {
                "(no process exits recorded)"
            } else {
                exits.joinToString("\n") { info ->
                    "${timestamp(info.timestamp)} ${reasonName(info.reason)} " +
                        "proc=${info.processName} importance=${info.importance} " +
                        "rss=${info.rss / 1024}MB pss=${info.pss / 1024}MB ${info.description ?: ""}"
                }
            }
        }.getOrElse { "(error reading exit reasons: ${it.message})" }
    }

    fun buildReport(context: Context): String {
        val runtime = Runtime.getRuntime()
        val header = buildString {
            appendLine("Blood Moon ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) - ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Heap: ${(runtime.totalMemory() - runtime.freeMemory()) / 1048576}MB used / max ${runtime.maxMemory() / 1048576}MB")
            appendLine("Service running: ${com.bloodmoon.app.service.FilterOverlayService.isRunning}")
            appendLine("Accessibility enabled: ${AccessibilityServiceHelper.isEnabled(context)} (connected: ${com.bloodmoon.app.service.BloodMoonAccessibilityService.instance != null})")
            appendLine("Overlay permission: ${android.provider.Settings.canDrawOverlays(context)}")
        }
        val log = runCatching {
            File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()?.takeLast(6000)
        }.getOrNull().orEmpty().ifBlank { "(no events)" }
        return "$header\n== Process exits ==\n${exitReasons(context)}\n\n== Events ==\n$log"
    }
}
