package com.bloodmoon.app

import android.app.Application
import com.bloodmoon.app.util.AppIconCache
import com.bloodmoon.app.util.DiagnosticLog
import rikka.shizuku.Shizuku

class BloodMoonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.log(this, "process started pid=${android.os.Process.myPid()}")

        // Unhandled failures (e.g. an exception while touching the overlay window) are kept in the
        // diagnostic log before the process dies, and then go on to the default handler.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticLog.log(this, "CRASH in ${thread.name}: ${throwable.stackTraceToString().take(1500)}")
            previous?.uncaughtException(thread, throwable)
        }

        // Shizuku's binder arrives asynchronously through a ContentProvider - if a permission
        // request is attempted before it arrives, Shizuku.pingBinder() reads as false and the
        // request is silently skipped with no feedback. Registering this listener at process
        // start (rather than only checking on-demand from a screen) means the binder is very
        // likely already connected by the time the user opens Settings and taps the switch.
        Shizuku.addBinderReceivedListenerSticky {
            // No action needed here - this callback's only job is to make sure Shizuku's binder
            // connection process has actually started as early as possible in the app's lifecycle.
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI hidden or worse: release what the app only keeps in order to draw the screen.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) AppIconCache.clear()
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) DiagnosticLog.log(this, "onTrimMemory level=$level")
    }
}
