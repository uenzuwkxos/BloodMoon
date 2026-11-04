package com.bloodmoon.app.service

import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.bloodmoon.app.R
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.data.model.ExclusionKind
import com.bloodmoon.app.data.model.FilterConfig
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.data.repository.FilterState
import com.bloodmoon.app.root.RootHelper
import com.bloodmoon.app.shizuku.ShizukuHelper
import com.bloodmoon.app.ui.MainActivity
import com.bloodmoon.app.util.AccessMode
import com.bloodmoon.app.util.AccessibilityServiceHelper
import com.bloodmoon.app.util.DiagnosticLog
import com.bloodmoon.app.util.FilterMath
import com.bloodmoon.app.util.ForegroundAppTracker
import com.bloodmoon.app.util.GrayscaleController
import com.bloodmoon.app.util.GrantResult
import com.bloodmoon.app.util.SystemColorAdjustment
import com.bloodmoon.app.util.UsageAccessHelper
import com.bloodmoon.app.widget.BloodMoonWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that draws the filter (one or two overlay windows) and applies the optional
 * system adjustments (grayscale, extra brightness reduction, Root filter).
 *
 * Regras importantes (cada uma corrige um bug real):
 *
 * 1. EVERY entry into onStartCommand with an action calls startForeground() first. Since the app
 * starts the service with startForegroundService(), Android requires that call within 5s -
 * including for ACTION_STOP. Before, a STOP on a service that had just been recreated (e.g. the TV
 * killed the process, the app was reopened and the user tapped the button) never called
 * startForeground() and took the app down with ForegroundServiceDidNotStartInTimeException; since
 * the saved state remained ACTIVE, the crash repeated on every tap.
 *
 * 2. A restart by the system (START_STICKY, intent == null) now resumes the filter according to the
 * saved state, instead of only observing the settings with no overlay and no notification.
 *
 * 3. "Double filter" really is two identical overlay windows, one over the other.
 *
 * 4. A watchdog recreates the overlay if the window disappears/detaches without the service knowing.
 *
 * 5. Grayscale is reasserted via ContentObserver + periodic check: on Android 16 something in the
 * system turns the daltonizer off again when the app leaves the screen.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class FilterOverlayService : Service() {

    private class OverlayWindow(
        val view: View,
        val windowManager: WindowManager,
        val isAccessibilityOverlay: Boolean,
        val addedAt: Long
    )

    private lateinit var windowManager: WindowManager
    private lateinit var repository: BloodMoonRepository

    private val overlayWindows = mutableListOf<OverlayWindow>()
    private var overlayAnimator: ValueAnimator? = null
    private var animatingTargets: List<Int>? = null

    private var extraDimApplied = false
    private var systemColorAdjustmentApplied = false
    private var grayscaleApplied = false
    private var systemFilterApplied = false
    private var systemFilterAppliedViaRoot = true

    private var pausedForExclusion = false

    // Per-app exclusions (each feature has its own list) and the foreground app.
    private var excludedFilterApps: Set<String> = emptySet()
    private var excludedGrayscaleApps: Set<String> = emptySet()
    private var foregroundPackage: String? = null
    private var grayscalePausedByApp = false
    private var systemFilterPausedByApp = false

    // Last known state, used by the watchdog to recreate the overlay.
    private var lastState: FilterState = FilterState.OFF
    private var lastConfig: FilterConfig? = null
    private var lastSettings: AppSettings? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observeJob: Job? = null
    private var exclusionMonitorJob: Job? = null
    private var watchdogJob: Job? = null

    // Grayscale: reinforcement against the system turning the daltonizer off.
    private var grayscaleWanted = false
    private var grayscaleObserver: ContentObserver? = null
    private var grayscaleVerifyJob: Job? = null
    private var grayscaleReassertJob: Job? = null

    private var fadeDurationMs: Long = DEFAULT_FADE_DURATION_MS

    companion object {
        const val CHANNEL_ID = "blood_moon_service_channel"
        const val DEFAULT_FADE_DURATION_MS = 900L
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_COLOR_ACTIVE = 0xFFD50000.toInt()
        const val NOTIFICATION_COLOR_PAUSED = 0xFFFF9800.toInt()

        const val ACTION_START = "com.bloodmoon.app.action.START"
        const val ACTION_PAUSE = "com.bloodmoon.app.action.PAUSE"
        const val ACTION_RESUME = "com.bloodmoon.app.action.RESUME"
        const val ACTION_STOP = "com.bloodmoon.app.action.STOP"
        const val ACTION_INTENSITY_UP = "com.bloodmoon.app.action.INTENSITY_UP"
        const val ACTION_INTENSITY_DOWN = "com.bloodmoon.app.action.INTENSITY_DOWN"
        const val ACTION_TONE_UP = "com.bloodmoon.app.action.TONE_UP"
        const val ACTION_TONE_DOWN = "com.bloodmoon.app.action.TONE_DOWN"
        private const val NOTIFICATION_INTENSITY_STEP = 5

        private const val WATCHDOG_INTERVAL_MS = 4000L
        private const val WINDOW_ATTACH_GRACE_MS = 3000L
        private const val GRAYSCALE_VERIFY_INTERVAL_MS = 3000L

        /** true while an instance of the service exists in this process (it goes away if the process dies). */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) = send(context, ACTION_START)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        fun canDrawOverlay(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, FilterOverlayService::class.java).apply { this.action = action }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        DiagnosticLog.log(this, "service created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = BloodMoonRepository(applicationContext)
        createNotificationChannel()
        displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    private var displayManager: DisplayManager? = null

    // Rotation, foldable opening/closing, TV switching resolution/frame rate: readjusts the overlay
    // size and checks that it is still alive (without waiting for the watchdog cycle).
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != android.view.Display.DEFAULT_DISPLAY) return
            // onDisplayChanged also fires for brightness/refresh rate (dozens of times): only logs
            // when the overlay size actually had to change (rotation etc.).
            if (relayoutOverlays()) {
                DiagnosticLog.log(this@FilterOverlayService, "display changed: overlay readjusted")
            }
            ensureOverlayAlive()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        relayoutOverlays()
    }

    private var commandGeneration = 0

    /** startForeground() that never takes the service down (e.g. a background restart on Android 12+). */
    private fun enterForeground(state: FilterState, permissionMissing: Boolean = false): Boolean {
        return try {
            startForeground(NOTIFICATION_ID, buildNotification(state, permissionMissing))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val generation = ++commandGeneration
        val action = intent?.action

        if (action == null) {
            // Restart by the system after the process was killed (common on TVs with little RAM).
            DiagnosticLog.log(this, "restarted by the system (null intent) - resuming filter")
            resumeAfterSystemRestart(generation)
            return START_STICKY
        }

        when (action) {
            ACTION_START, ACTION_RESUME -> {
                if (!canShowOverlay()) {
                    enterForeground(FilterState.OFF, permissionMissing = true)
                    serviceScope.launch { repository.setFilterState(FilterState.OFF) }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                enterForeground(FilterState.ACTIVE)
                serviceScope.launch { repository.setFilterState(FilterState.ACTIVE) }
                startExclusionMonitor()
                observeSettings()
                startWatchdog()
            }
            ACTION_PAUSE -> {
                enterForeground(FilterState.PAUSED)
                serviceScope.launch { repository.setFilterState(FilterState.PAUSED) }
                stopExclusionMonitor()
                removeOverlay()
            }
            ACTION_STOP -> {
                // Mandatory even to stop: startForegroundService() requires startForeground().
                enterForeground(FilterState.OFF)
                serviceScope.launch { repository.setFilterState(FilterState.OFF) }
                stopExclusionMonitor()
                watchdogJob?.cancel()
                restoreExtraDimIfNeeded()
                restoreSystemColorAdjustmentIfNeeded()
                restoreGrayscaleIfNeeded()
                restoreSystemFilterIfNeeded()
                removeOverlay(animate = true) {
                    if (generation == commandGeneration) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_INTENSITY_UP, ACTION_INTENSITY_DOWN -> {
                // Notification - / + buttons: adjust the intensity; the settings observer reapplies
                // the overlay and updates the notification by itself.
                enterForeground(if (lastState == FilterState.OFF) FilterState.ACTIVE else lastState)
                val delta = if (action == ACTION_INTENSITY_UP) NOTIFICATION_INTENSITY_STEP else -NOTIFICATION_INTENSITY_STEP
                serviceScope.launch {
                    val config = repository.filterConfigFlow.first()
                    repository.updateFilterConfig(
                        config.copy(colorIntensity = (config.colorIntensity + delta).coerceIn(0, 100))
                    )
                }
            }
            ACTION_TONE_UP, ACTION_TONE_DOWN -> {
                enterForeground(if (lastState == FilterState.OFF) FilterState.ACTIVE else lastState)
                val delta = if (action == ACTION_TONE_UP) NOTIFICATION_INTENSITY_STEP else -NOTIFICATION_INTENSITY_STEP
                serviceScope.launch {
                    val config = repository.filterConfigFlow.first()
                    repository.updateFilterConfig(
                        config.copy(toneIntensity = (config.toneIntensity + delta).coerceIn(0, 100))
                    )
                }
            }
            else -> {
                enterForeground(FilterState.OFF)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun resumeAfterSystemRestart(generation: Int) {
        serviceScope.launch {
            val state = repository.filterStateFlow.first()
            if (generation != commandGeneration) return@launch // chegou um comando de verdade nesse meio tempo
            when (state) {
                FilterState.ACTIVE -> {
                    if (!canShowOverlay()) {
                        repository.setFilterState(FilterState.OFF)
                        stopSelf()
                        return@launch
                    }
                    enterForeground(FilterState.ACTIVE)
                    startExclusionMonitor()
                    observeSettings()
                    startWatchdog()
                }
                FilterState.PAUSED -> {
                    enterForeground(FilterState.PAUSED)
                    observeSettings()
                }
                FilterState.OFF -> stopSelf()
            }
        }
    }

    private fun observeSettings() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            combine(
                repository.filterStateFlow,
                repository.filterConfigFlow,
                repository.appSettingsFlow
            ) { state, config, settings -> Triple(state, config, settings) }
                .debounce(120)
                .collect { (state, config, settings) ->
                    lastState = state
                    lastConfig = config
                    lastSettings = settings
                    fadeDurationMs = settings.fadeDurationMs.toLong().coerceIn(150L, 5000L)
                    if (state == FilterState.ACTIVE) {
                        if (settings.useAccessibilityOverlay && !AccessibilityServiceHelper.isEnabled(applicationContext)) {
                            // Root, Shizuku or ADB: turns the service on by itself (requests the
                            // WRITE_SECURE_SETTINGS permission if missing). With no access at all,
                            // it stays on the regular overlay, with no error - the home screen asks
                            // the user.
                            withContext(Dispatchers.IO) {
                                AccessibilityServiceHelper.ensureEnabledPrivileged(applicationContext, settings)
                            }
                        }
                        updateNotification(FilterState.ACTIVE)
                        if (!pausedForExclusion) {
                            applyFilterConfig(config, settings)
                        }
                        applyExtraDim(settings)
                        applySystemColorAdjustment(settings)
                        applyGrayscale(settings)
                        applySystemFilter(settings, config)
                    } else {
                        updateNotification(state)
                        removeOverlay()
                        restoreExtraDimIfNeeded()
                        restoreSystemColorAdjustmentIfNeeded()
                        restoreGrayscaleIfNeeded()
                        restoreSystemFilterIfNeeded()
                    }
                }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Excluded apps monitor
    // ---------------------------------------------------------------------------------------

    private fun startExclusionMonitor() {
        exclusionMonitorJob?.cancel()
        pausedForExclusion = false
        grayscalePausedByApp = false
        systemFilterPausedByApp = false
        foregroundPackage = null

        // Main source: accessibility service events (instant, no polling and no Usage access
        // permission). They arrive on the main thread.
        ForegroundAppTracker.listener = { pkg -> onForegroundPackage(pkg) }

        exclusionMonitorJob = serviceScope.launch {
            launch {
                combine(
                    repository.excludedFlow(ExclusionKind.FILTER),
                    repository.excludedFlow(ExclusionKind.GRAYSCALE)
                ) { filter, gray -> filter to gray }
                    .collect { (filter, gray) ->
                        excludedFilterApps = filter
                        excludedGrayscaleApps = gray
                        applyExclusions()
                    }
            }

            // Plan B (without the accessibility service): UsageStats every second, only if there is
            // any exclusion and the permission.
            launch(Dispatchers.Default) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@launch
                while (isActive) {
                    val anyExclusion = excludedFilterApps.isNotEmpty() || excludedGrayscaleApps.isNotEmpty()
                    if (anyExclusion &&
                        BloodMoonAccessibilityService.instance == null &&
                        UsageAccessHelper.hasUsageAccess(applicationContext)
                    ) {
                        getForegroundPackage(usageStatsManager)?.let { pkg ->
                            withContext(Dispatchers.Main) { onForegroundPackage(pkg) }
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun stopExclusionMonitor() {
        ForegroundAppTracker.listener = null
        exclusionMonitorJob?.cancel()
    }

    private fun onForegroundPackage(packageName: String) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        applyExclusions()
    }

    /**
     * Turns each feature on/off according to the foreground app. Each feature has its own exclusion
     * list; an app excluded from the Filter also goes without the System Filter (it is the same
     * "filter", just drawn by the system).
     */
    private fun applyExclusions() {
        val pkg = foregroundPackage
        val overlayPaused = pkg != null && pkg in excludedFilterApps
        val grayscalePaused = pkg != null && pkg in excludedGrayscaleApps
        // System Filter = the same filter: the "Filter" exclusion applies to it too.
        val systemPaused = overlayPaused
        val config = lastConfig
        val settings = lastSettings
        val active = lastState == FilterState.ACTIVE

        if (overlayPaused != pausedForExclusion) {
            pausedForExclusion = overlayPaused
            if (overlayPaused) {
                removeOverlay()
            } else if (active && config != null && settings != null) {
                applyFilterConfig(config, settings)
            }
        }
        if (grayscalePaused != grayscalePausedByApp) {
            grayscalePausedByApp = grayscalePaused
            if (active && settings != null) applyGrayscale(settings)
        }
        if (systemPaused != systemFilterPausedByApp) {
            systemFilterPausedByApp = systemPaused
            if (active && settings != null && config != null) applySystemFilter(settings, config)
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 3000
        val events = usageStatsManager.queryEvents(startTime, endTime) ?: return null

        var currentPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentPackage = event.packageName
            }
        }
        return currentPackage
    }

    // ---------------------------------------------------------------------------------------
    // Watchdog: recreates the overlay if the window disappears without the service having asked for
    // it ---------------------------------------------------------------------------------------

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                ensureOverlayAlive()
            }
        }
    }

    private fun ensureOverlayAlive() {
        val config = lastConfig ?: return
        val settings = lastSettings ?: return
        if (lastState != FilterState.ACTIVE || pausedForExclusion || systemFilterActiveAndReady(settings)) return
        if (!canShowOverlay()) return

        val now = SystemClock.elapsedRealtime()
        val canUseA11yOverlay = accessibilityWindowManager() != null
        val broken = overlayWindows.isEmpty() ||
                overlayWindows.any { !it.view.isAttachedToWindow && now - it.addedAt > WINDOW_ATTACH_GRACE_MS } ||
                // the accessibility service became available after the regular overlay: migrate
                (canUseA11yOverlay && overlayWindows.any { !it.isAccessibilityOverlay })
        if (broken) {
            DiagnosticLog.log(
                this,
                "overlay lost/migrating: windows=${overlayWindows.size} " +
                    "attached=${overlayWindows.count { it.view.isAttachedToWindow }} a11y=$canUseA11yOverlay - recreating"
            )
            removeOverlay(animate = false)
            applyFilterConfig(config, settings)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Overlay (1 or 2 identical windows)
    // ---------------------------------------------------------------------------------------

    private fun applyFilterConfig(config: FilterConfig, settings: AppSettings) {
        if (systemFilterActiveAndReady(settings)) {
            removeOverlay()
            return
        }
        // Each window gets the color of ONE layer; with double filter there are two stacked
        // windows, which the compositor sums exactly like the two layers of FilterMath (and of the
        // preview).
        val targetColor = FilterMath.computeLayerColor(config)
        val windowCount = if (config.doubleFilterEnabled) 2 else 1
        showOverlay(targetColor, windowCount)
    }

    private fun systemFilterActiveAndReady(settings: AppSettings): Boolean {
        return settings.systemFilterEnabled && settings.useRoot
    }

    /** Possible overlay: the "display over other apps" permission OR an active accessibility service. */
    private fun canShowOverlay(): Boolean =
        canDrawOverlay(this) || accessibilityWindowManager() != null

    private fun isTv(): Boolean =
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)

    /**
     * REAL screen size now (already in the current rotation, including system bars and the camera
     * cutout). Before, the overlay used "screen + 4000px" on every device: it covered, but
     * allocated a ~5000x6400 px surface (more than 120 MB per buffer) to draw a solid color. Red
     * Moon has a comment saying it tried to adjust this and could not (the cutout always came back
     * null); the right way is WindowMetrics (API 30+) / Display.getRealSize (API 28-29), which
     * already include the cutout, together with layoutInDisplayCutoutMode and
     * FLAG_LAYOUT_NO_LIMITS.
     */
    @Suppress("DEPRECATION")
    private fun displaySize(wm: WindowManager): android.graphics.Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            android.graphics.Point(bounds.width(), bounds.height())
        } else {
            val point = android.graphics.Point()
            wm.defaultDisplay.getRealSize(point)
            point
        }
    }

    private fun buildOverlayParams(wm: WindowManager, accessibility: Boolean): WindowManager.LayoutParams {
        // On TV it stays MATCH_PARENT: the TV changes resolution/frame rate by itself (e.g. when
        // switching a video's quality) and the system readjusts the window without depending on us.
        val tv = isTv()
        val size = displaySize(wm)
        return WindowManager.LayoutParams().apply {
            width = if (tv) WindowManager.LayoutParams.MATCH_PARENT else size.x
            height = if (tv) WindowManager.LayoutParams.MATCH_PARENT else size.y
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            type = if (accessibility) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * The accessibility service's WindowManager, if it is active and the option is on.
     * TYPE_ACCESSIBILITY_OVERLAY windows (what Red Moon uses in this mode) sit above
     * TYPE_APPLICATION_OVERLAY ones, do not depend on the "Display over other apps" permission and
     * are not hidden by the TV's system/launcher - which "fights" regular overlays to give priority
     * to the players. It only works with the window created by the service itself.
     */
    private fun accessibilityWindowManager(): WindowManager? {
        if (lastSettings?.useAccessibilityOverlay == false) return null
        val service = BloodMoonAccessibilityService.instance ?: return null
        return service.getSystemService(WINDOW_SERVICE) as? WindowManager
    }

    private fun createOverlayWindow(): OverlayWindow? {
        val view = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }

        val a11yWm = accessibilityWindowManager()
        if (a11yWm != null) {
            try {
                a11yWm.addView(view, buildOverlayParams(a11yWm, accessibility = true))
                DiagnosticLog.log(this, "overlay created (accessibility)")
                return OverlayWindow(view, a11yWm, true, SystemClock.elapsedRealtime())
            } catch (e: Exception) {
                e.printStackTrace() // cai para o overlay comum logo abaixo
            }
        }
        return try {
            windowManager.addView(view, buildOverlayParams(windowManager, accessibility = false))
            DiagnosticLog.log(this, "overlay created (regular - subject to the TV's tguard/FloatWindow)")
            OverlayWindow(view, windowManager, false, SystemClock.elapsedRealtime())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Readjusts the windows to the current screen size (rotation, foldable, video mode switch). */
    private fun relayoutOverlays(): Boolean {
        if (isTv()) return false // MATCH_PARENT: o sistema cuida
        var changed = false
        overlayWindows.toList().forEach { window ->
            try {
                val params = window.view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                val size = displaySize(window.windowManager)
                if (params.width != size.x || params.height != size.y) {
                    params.width = size.x
                    params.height = size.y
                    window.windowManager.updateViewLayout(window.view, params)
                    changed = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return changed
    }

    private fun currentColorOf(window: OverlayWindow): Int =
        (window.view.background as? ColorDrawable)?.color ?: Color.TRANSPARENT

    private fun showOverlay(targetColor: Int, windowCount: Int) {
        while (overlayWindows.size < windowCount) {
            val window = createOverlayWindow() ?: break
            overlayWindows.add(window)
        }
        if (overlayWindows.isEmpty()) return

        // Windows beyond windowCount (double filter off) fade out and are removed.
        val targets = overlayWindows.mapIndexed { index, _ ->
            if (index < windowCount) targetColor else Color.TRANSPARENT
        }
        animateTo(targets, onComplete = null)
    }

    private fun removeOverlay(animate: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (overlayWindows.isEmpty()) {
            animatingTargets = null
            onComplete?.invoke()
            return
        }
        if (!animate) {
            overlayAnimator?.cancel()
            overlayAnimator = null
            animatingTargets = null
            removeAllWindowsNow()
            onComplete?.invoke()
            return
        }
        animateTo(overlayWindows.map { Color.TRANSPARENT }, onComplete)
    }

    /** Clean intermediate color: starts/ends at the same RGB with alpha 0, without passing through "black". */
    private fun fadeEndpoints(start: Int, target: Int): Pair<Int, Int> {
        var s = start
        var t = target
        if (Color.alpha(s) == 0 && Color.alpha(t) != 0) s = Color.argb(0, Color.red(t), Color.green(t), Color.blue(t))
        if (Color.alpha(t) == 0 && Color.alpha(s) != 0) t = Color.argb(0, Color.red(s), Color.green(s), Color.blue(s))
        return s to t
    }

    private fun animateTo(targets: List<Int>, onComplete: (() -> Unit)?) {
        val windows = overlayWindows.toList()
        if (windows.size != targets.size) return

        val running = overlayAnimator?.isRunning == true
        if (running && animatingTargets == targets) {
            if (onComplete != null) {
                overlayAnimator?.addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!cancelled) onComplete.invoke()
                    }
                })
            }
            return
        }

        val endpoints = windows.mapIndexed { i, w -> fadeEndpoints(currentColorOf(w), targets[i]) }
        val alreadyThere = windows.indices.all { currentColorOf(windows[it]) == targets[it] }

        overlayAnimator?.cancel()
        overlayAnimator = null

        if (alreadyThere) {
            animatingTargets = null
            finishAnimation(windows, targets)
            onComplete?.invoke()
            return
        }

        animatingTargets = targets
        val evaluator = ArgbEvaluator()
        overlayAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeDurationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                for (i in windows.indices) {
                    val (s, t) = endpoints[i]
                    windows[i].view.setBackgroundColor(evaluator.evaluate(fraction, s, t) as Int)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    animatingTargets = null
                    finishAnimation(windows, targets)
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /** Fixa a cor final e remove as janelas que terminaram transparentes. */
    private fun finishAnimation(windows: List<OverlayWindow>, targets: List<Int>) {
        for (i in windows.indices) {
            if (targets[i] == Color.TRANSPARENT) {
                removeWindow(windows[i])
            } else {
                windows[i].view.setBackgroundColor(targets[i])
            }
        }
    }

    private fun removeWindow(window: OverlayWindow) {
        try {
            window.windowManager.removeView(window.view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        overlayWindows.remove(window)
    }

    private fun removeAllWindowsNow() {
        overlayWindows.toList().forEach { removeWindow(it) }
    }

    // ---------------------------------------------------------------------------------------
    // Ajustes de sistema
    // ---------------------------------------------------------------------------------------

    private fun applyExtraDim(settings: AppSettings) {
        if (!settings.extraDimEnabled) {
            restoreExtraDimIfNeeded()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!AccessMode.isConfigured(applicationContext, settings)) {
            restoreExtraDimIfNeeded()
            return
        }

        serviceScope.launch {
            val granted = ensureWriteSecureSettingsGranted(settings)
            if (!granted) return@launch
            withContext(Dispatchers.IO) {
                runCatching {
                    Settings.Secure.putInt(contentResolver, "reduce_bright_colors_activated", 1)
                }
            }
            extraDimApplied = true
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun applySystemColorAdjustment(settings: AppSettings) {
        restoreSystemColorAdjustmentIfNeeded()
    }

    private suspend fun ensureWriteSecureSettingsGranted(settings: AppSettings): Boolean {
        if (hasWriteSecureSettings()) return true
        val result = when {
            settings.useRoot -> withContext(Dispatchers.IO) { RootHelper.grantWriteSecureSettingsPermission(applicationContext) }
            settings.useShizuku -> ShizukuHelper.grantWriteSecureSettingsPermission(applicationContext)
            else -> return false
        }
        return result is GrantResult.Granted || result is GrantResult.NeedsAppRestart
    }

    private fun applyGrayscale(settings: AppSettings) {
        if (!settings.grayscaleEnabled || settings.systemFilterEnabled || grayscalePausedByApp) {
            restoreGrayscaleIfNeeded()
            return
        }
        if (!AccessMode.isConfigured(applicationContext, settings)) {
            restoreGrayscaleIfNeeded()
            return
        }

        grayscaleWanted = true
        serviceScope.launch {
            val granted = ensureWriteSecureSettingsGranted(settings)
            if (!granted) {
                grayscaleWanted = false
                GrayscaleController.wanted = false
                return@launch
            }
            withContext(Dispatchers.IO) {
                // The accessibility service is what keeps grayscale on Android 16/TV (see
                // BloodMoonAccessibilityService); turn it on before writing the keys.
                AccessibilityServiceHelper.ensureEnabled(applicationContext)
                SystemColorAdjustment.setGrayscaleEnabled(applicationContext, true)
            }
            GrayscaleController.wanted = true
            grayscaleApplied = true
            startGrayscaleEnforcement()
        }
    }

    private fun restoreGrayscaleIfNeeded() {
        grayscaleWanted = false
        GrayscaleController.wanted = false
        stopGrayscaleEnforcement()
        if (!grayscaleApplied) return
        grayscaleApplied = false
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                if (hasWriteSecureSettings()) {
                    SystemColorAdjustment.setGrayscaleEnabled(applicationContext, false)
                }
            }
        }
    }

    /**
     * While grayscale is "wanted", any change in the daltonizer keys (ContentObserver, immediate)
     * or a check every few seconds (safety net, in case the system changes it through a path that
     * does not notify) reactivates it if it is off.
     */
    private fun startGrayscaleEnforcement() {
        if (grayscaleObserver == null) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    if (grayscaleWanted) reassertGrayscaleSoon()
                }
            }
            SystemColorAdjustment.grayscaleUris().forEach { uri ->
                runCatching { contentResolver.registerContentObserver(uri, false, observer) }
            }
            grayscaleObserver = observer
        }
        if (grayscaleVerifyJob?.isActive != true) {
            grayscaleVerifyJob = serviceScope.launch {
                while (isActive) {
                    delay(GRAYSCALE_VERIFY_INTERVAL_MS)
                    if (!grayscaleWanted) continue
                    if (!AccessibilityServiceHelper.isEnabled(applicationContext)) {
                        withContext(Dispatchers.IO) { AccessibilityServiceHelper.ensureEnabled(applicationContext) }
                    }
                    if (!SystemColorAdjustment.isGrayscaleEnabled(applicationContext)) {
                        reassertGrayscaleSoon()
                    }
                }
            }
        }
    }

    private fun reassertGrayscaleSoon() {
        grayscaleReassertJob?.cancel()
        grayscaleReassertJob = serviceScope.launch {
            delay(150)
            if (grayscaleWanted && !SystemColorAdjustment.isGrayscaleEnabled(applicationContext)) {
                withContext(Dispatchers.IO) {
                    SystemColorAdjustment.setGrayscaleEnabled(applicationContext, true)
                }
            }
        }
    }

    private fun stopGrayscaleEnforcement() {
        grayscaleVerifyJob?.cancel()
        grayscaleVerifyJob = null
        grayscaleReassertJob?.cancel()
        grayscaleReassertJob = null
        grayscaleObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        grayscaleObserver = null
    }

    private fun applySystemFilter(settings: AppSettings, config: FilterConfig) {
        if (!systemFilterActiveAndReady(settings) || settings.grayscaleEnabled || systemFilterPausedByApp) {
            restoreSystemFilterIfNeeded()
            return
        }

        serviceScope.launch {
            val factors = FilterMath.computeMultiplyFactors(config)
            val command = SystemColorAdjustment.surfaceFlingerColorMatrixCommand(factors[0], factors[1], factors[2])
            val success = withContext(Dispatchers.IO) { runSurfaceFlingerCommand(command) }
            if (success) {
                systemFilterApplied = true
                systemFilterAppliedViaRoot = settings.useRoot
            }
        }
    }

    private fun restoreSystemFilterIfNeeded() {
        if (!systemFilterApplied) return
        systemFilterApplied = false
        val command = SystemColorAdjustment.surfaceFlingerColorMatrixDisableCommand()
        val useRoot = systemFilterAppliedViaRoot
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                if (useRoot) {
                    RootHelper.executeRootCommand(command)
                } else {
                    ShizukuHelper.execShellCommand(applicationContext, command)
                }
                // The System Filter and Grayscale (daltonizer) use the SAME SurfaceFlinger color
                // matrix. This reset command zeroes the whole matrix, wiping the grayscale one that
                // was just applied (the setting stays "on", which is why it only came back when
                // toggling off and on). Turns the daltonizer back on after the reset, if grayscale
                // should be on.
                if (grayscaleWanted) {
                    SystemColorAdjustment.setGrayscaleEnabled(applicationContext, false)
                    SystemColorAdjustment.setGrayscaleEnabled(applicationContext, true)
                }
            }
        }
    }

    private suspend fun runSurfaceFlingerCommand(command: String): Boolean {
        val (success, _) = RootHelper.executeRootCommand(command)
        return success
    }

    private fun restoreExtraDimIfNeeded() {
        if (!extraDimApplied) return
        extraDimApplied = false
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (hasWriteSecureSettings()) {
                        Settings.Secure.putInt(contentResolver, "reduce_bright_colors_activated", 0)
                    }
                }
            }
        }
    }

    private fun restoreSystemColorAdjustmentIfNeeded() {
        if (!systemColorAdjustmentApplied) return
        systemColorAdjustmentApplied = false
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                if (hasWriteSecureSettings()) {
                    SystemColorAdjustment.setColorInversionEnabled(applicationContext, false)
                }
            }
        }
    }

    private fun hasWriteSecureSettings(): Boolean {
        return checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(state: FilterState, permissionMissing: Boolean = false): android.app.Notification {
        // Explicit intent (without querying the PackageManager): this function runs on the main
        // thread at the exact moment the button animates, and the query cost a few frames.
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val pendingLaunch = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, FilterOverlayService::class.java).apply { action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getForegroundService(
            this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resumeIntent = Intent(this, FilterOverlayService::class.java).apply { action = ACTION_RESUME }
        val pendingResume = PendingIntent.getForegroundService(
            this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FilterOverlayService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getForegroundService(
            this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (!permissionMissing && (state == FilterState.ACTIVE || state == FilterState.PAUSED)) {
            return buildRichNotification(state, pendingLaunch, pendingPause, pendingResume, pendingStop)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(pendingLaunch)
            .setOngoing(!permissionMissing && state != FilterState.OFF)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColorized(!permissionMissing && state == FilterState.ACTIVE)

        when {
            permissionMissing -> builder.setContentText(getString(R.string.notification_permission_missing))
            state == FilterState.ACTIVE -> builder
                .setContentText(getString(R.string.notification_active))
                .setColor(NOTIFICATION_COLOR_ACTIVE)
                .addAction(R.drawable.ic_notif_pause, getString(R.string.action_pause), pendingPause)
                .addAction(R.drawable.ic_notif_stop, getString(R.string.action_deactivate), pendingStop)
            state == FilterState.PAUSED -> builder
                .setContentText(getString(R.string.notification_paused))
                .setColor(NOTIFICATION_COLOR_PAUSED)
                .addAction(R.drawable.ic_notif_play, getString(R.string.action_resume), pendingResume)
                .addAction(R.drawable.ic_notif_stop, getString(R.string.action_deactivate), pendingStop)
            else -> {
                builder.setContentText(getString(R.string.notification_off))
            }
        }

        return builder.build()
    }

    /** Cor + recursos ligados, ex.: "Vermelho · Duplo · Cinza". */
    private fun detailLine(): String {
        val config = lastConfig ?: return getString(R.string.notification_active_hint)
        val settings = lastSettings
        val parts = mutableListOf(
            getString(
                when (config.color) {
                    com.bloodmoon.app.data.model.FilterColor.BLACK -> R.string.color_black
                    com.bloodmoon.app.data.model.FilterColor.RED -> R.string.color_red
                    com.bloodmoon.app.data.model.FilterColor.ORANGE -> R.string.color_orange
                    com.bloodmoon.app.data.model.FilterColor.YELLOW -> R.string.color_yellow
                }
            )
        )
        if (config.doubleFilterEnabled) parts += getString(R.string.widget_opt_double)
        if (settings?.grayscaleEnabled == true) parts += getString(R.string.widget_opt_grayscale)
        if (settings?.extraDimEnabled == true) parts += getString(R.string.widget_opt_dim)
        if (settings?.systemFilterEnabled == true) parts += getString(R.string.widget_opt_system)
        return parts.joinToString(" · ")
    }

    /**
     * Notification with its own look (DecoratedCustomViewStyle): collapsed shows state and details;
     * expanded also shows the intensity bar and the [Pause/Resume] [-] [+] [Turn off] buttons. Text
     * colors follow the system's light/dark theme (TextAppearance.Compat.Notification); the crimson
     * stays on the icon and the buttons.
     */
    private fun buildRichNotification(
        state: FilterState,
        pendingLaunch: PendingIntent,
        pendingPause: PendingIntent,
        pendingResume: PendingIntent,
        pendingStop: PendingIntent
    ): android.app.Notification {
        val config = lastConfig
        val intensity = config?.colorIntensity ?: 0
        val status = if (state == FilterState.ACTIVE) {
            getString(R.string.tile_subtitle_active, intensity)
        } else {
            getString(R.string.notification_paused)
        }
        val detail = detailLine()

        fun servicePending(requestCode: Int, action: String) = PendingIntent.getForegroundService(
            this, requestCode,
            Intent(this, FilterOverlayService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val collapsed = RemoteViews(packageName, R.layout.notification_collapsed).apply {
            setTextViewText(R.id.notif_status, status)
            setTextViewText(R.id.notif_detail, detail)
        }
        val expanded = RemoteViews(packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notif_status, status)
            setTextViewText(R.id.notif_detail, detail)
            val tone = config?.toneIntensity ?: 0
            setProgressBar(R.id.notif_int_bar, 100, intensity, false)
            setTextViewText(R.id.notif_int_label, getString(R.string.home_intensity) + " " + intensity + "%")
            setProgressBar(R.id.notif_tone_bar, 100, tone, false)
            setTextViewText(R.id.notif_tone_label, getString(R.string.home_tone) + " " + tone + "%")
            setTextViewText(
                R.id.notif_btn_primary,
                getString(if (state == FilterState.ACTIVE) R.string.action_pause else R.string.action_resume)
            )
            setTextViewText(R.id.notif_btn_stop, getString(R.string.action_deactivate))
            setOnClickPendingIntent(
                R.id.notif_btn_primary,
                if (state == FilterState.ACTIVE) pendingPause else pendingResume
            )
            setOnClickPendingIntent(R.id.notif_btn_stop, pendingStop)
            setOnClickPendingIntent(R.id.notif_int_minus, servicePending(4, ACTION_INTENSITY_DOWN))
            setOnClickPendingIntent(R.id.notif_int_plus, servicePending(5, ACTION_INTENSITY_UP))
            setOnClickPendingIntent(R.id.notif_tone_minus, servicePending(6, ACTION_TONE_DOWN))
            setOnClickPendingIntent(R.id.notif_tone_plus, servicePending(7, ACTION_TONE_UP))
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSubText(detail)
            .setContentIntent(pendingLaunch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(if (state == FilterState.ACTIVE) NOTIFICATION_COLOR_ACTIVE else NOTIFICATION_COLOR_PAUSED)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .build()
    }

    private fun updateNotification(state: FilterState) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(state))
        BloodMoonWidgetProvider.requestUpdate(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) DiagnosticLog.log(this, "service onTrimMemory level=$level")
    }

    override fun onDestroy() {
        DiagnosticLog.log(this, "service destroyed (state=$lastState)")
        isRunning = false
        displayManager?.unregisterDisplayListener(displayListener)
        overlayAnimator?.cancel()
        removeOverlay(animate = false)
        stopGrayscaleEnforcement()
        observeJob?.cancel()
        stopExclusionMonitor()
        watchdogJob?.cancel()
        super.onDestroy()
    }
}
