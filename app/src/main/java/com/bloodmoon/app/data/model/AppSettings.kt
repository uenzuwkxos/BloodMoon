package com.bloodmoon.app.data.model

enum class AppThemeMode {
    // DARK is the old PURE_BLACK (which in practice used a very dark gray, not pure black, on
    // elevated surfaces - Color.kt DarkSurface/DarkSurfaceVariant). It is now the "Dark theme".
    // PURE_BLACK is now a new fourth mode with true absolute black on every surface, with no
    // intermediate grays.
    SYSTEM, LIGHT, DARK, PURE_BLACK
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val primaryAccentColor: String = "#C41E3A", // Dark Crimson Red (the only accent, for now)
    val languageCode: String = "system",        // "system", "pt", "en", "es", "fr", "de", "zh", "ja", "ko", "hi", "ar", "ru", "id", "it", "nl", "pl"
    val pauseOnReboot: Boolean = true,
    val useShizuku: Boolean = false,
    val useRoot: Boolean = false,
    val extraDimEnabled: Boolean = false,       // requires useShizuku or useRoot to take effect
    val systemColorAdjustmentEnabled: Boolean = false, // real system-level filter, requires Root/Shizuku
    // Grayscale reuses the same WRITE_SECURE_SETTINGS gate as extraDimEnabled/
    // systemColorAdjustmentEnabled above, and is reachable via ADB too (see
    // SystemColorAdjustment.isGrayscaleEnabled/setGrayscaleEnabled - both underlying keys are
    // plain ints).
    val grayscaleEnabled: Boolean = false,
    // Main filter (red/yellow tint + dimming) applied by the system compositor (SurfaceFlinger, via
    // accessibility_display_color_matrix) instead of an app overlay - Root/Shizuku only (see
    // SystemColorAdjustment.surfaceFlingerColorMatrixCommand). It coexists with Grayscale (a
    // different setting key), but turns the regular overlay off while active - see the note in
    // FilterOverlayService on why the two are never on at the same time. There used to be a
    // separate "Redscale" here (a fixed pure-red matrix, no slider) - it was removed as redundant:
    // with the app's red color at #FF0000 (FilterMath.COLOR_RED), the System Filter with tint and
    // intensity at 100% and dimming at 0% already produces exactly the same matrix the fixed
    // Redscale did, just with real sliders instead of a single fixed state.
    val systemFilterEnabled: Boolean = false,
    // The navigation bar position is no longer a user choice (it used to be navBarPosition here,
    // Top/Left/Bottom) - it is decided automatically by the screen width in BloodMoonApp
    // (MainActivity.kt): wide screens (tablets, phones in landscape) use a NavigationRail on the
    // left, everything else uses the regular bottom bar. "Top" no longer exists. How long the
    // overlay takes to fade in/out when the filter is toggled, paused, resumed, or when a slider
    // value is committed. User-adjustable because a too-short fade combined with several rapid
    // config updates (e.g. applying a profile) used to visibly snap instead of gliding - letting
    // the user pick a longer, more gradual value avoids that entirely.
    val fadeDurationMs: Int = 900,
    // Android TV-only features (see util/DeviceType.kt) - "Timed power-off" and "Automatic
    // standby", both under Advanced features. They only make sense on a TV because there the device
    // is really turned off by those commands - on a phone/tablet the screen would just go dark
    // while the system keeps running in the background, so the UI does not even offer these toggles
    // outside TVs (see HomeScreen/SettingsScreen). tvAutoOffHour/tvAutoOffMinute reuse the same
    // fixed-time concept (hour/minute) already used in ScheduleConfig (ScheduleScreen), except that
    // here it is a single time, not a start/end window - "turn off at 23:00", not "between 22:00
    // and 06:00".
    val tvAutoOffEnabled: Boolean = false,
    val tvAutoOffHour: Int = 23,
    val tvAutoOffMinute: Int = 0,
    // Automatic standby: inactivity time (minutes) until the TV really goes to sleep - confirmed by
    // manual testing (`adb shell settings put secure sleep_timeout`) to be this key
    // (Settings.Secure.SLEEP_TIMEOUT), not Settings.System.SCREEN_OFF_TIMEOUT (which only turns the
    // screen off/starts the daydream without actually shutting down - see the full comment in
    // util/TvPowerHelper.kt). tvStandbyEnabled=false writes nothing custom to this key - it leaves
    // the system's default value as is, without imposing a standby timeout the user did not ask
    // for.
    val tvStandbyEnabled: Boolean = false,
    val tvStandbyMinutes: Int = 30,
    // Overlay created by the accessibility service (like Red Moon): more stable on TVs, which
    // hide/kill regular overlays. It turns the service on by itself when WRITE_SECURE_SETTINGS is
    // available.
    val useAccessibilityOverlay: Boolean = true,
    // "Enable accessibility" notice dismissed by the user (it stays hidden until they change their mind in Options).
    val a11yWarningDismissed: Boolean = false,
    // ADB "on" for the app: null = not chosen yet (whatever permission already exists applies).
    // false = the user turned ADB off in Options: the WRITE_SECURE_SETTINGS permission stays in the
    // system (only uninstalling the app revokes it), but the app stops counting it as available
    // access.
    val adbEnabled: Boolean? = null,
    // Profile schedules: on boot, applies the most recent schedule the device missed. On by
    // default.
    val applyLatestProfileOnBoot: Boolean = true,
    // "Include system apps" checkbox on the Exclusions tab. It used to be only local screen state
    // (remember) and went back to off every time the user left the tab/the app.
    val includeSystemAppsInExclusions: Boolean = false
)
