package com.bloodmoon.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.data.model.AppThemeMode
import com.bloodmoon.app.data.model.FilterColor
import com.bloodmoon.app.data.model.ExclusionKind
import com.bloodmoon.app.data.model.ProfileScheduleEntry
import com.bloodmoon.app.util.ConfigEffects
import com.bloodmoon.app.receiver.ScheduleAlarmReceiver
import com.bloodmoon.app.receiver.ProfileScheduleReceiver
import com.bloodmoon.app.data.model.FilterConfig
import com.bloodmoon.app.data.model.FilterProfile
import com.bloodmoon.app.data.model.ScheduleConfig
import com.bloodmoon.app.util.LocaleHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blood_moon_prefs")

enum class FilterState {
    OFF, ACTIVE, PAUSED
}

data class ExportData(
    // 1 = original version; 2 = + Grayscale exclusions, profile schedules, TV features in profiles;
    // 3 = + end time in profile schedules and "Include system apps" in the exclusions (new fields
    // have defaults, so backups 1 and 2 still import).
    val exportVersion: Int = 3,
    val filterConfig: FilterConfig,
    val scheduleConfig: ScheduleConfig,
    val profiles: List<FilterProfile>,
    val excludedPackages: List<String>,
    val appSettings: AppSettings,
    // Added after backup version 1: null in old backups (Gson ignores the default).
    val grayscaleExcludedPackages: List<String>? = null,
    val profileSchedules: List<ProfileScheduleEntry>? = null
)

class BloodMoonRepository(context: Context) {

    // Always bind to the application context. Screens/services may construct this repository
    // with an Activity or Service context; if we captured that context directly we'd risk
    // leaking it and, worse, DataStore is only safe to open once per file per process - always
    // going through the same application-scoped Context keeps every caller reading/writing the
    // exact same underlying store.
    private val appContext: Context = context.applicationContext
    private val gson = Gson()

    companion object {
        val FILTER_STATE_KEY = stringPreferencesKey("filter_state")
        val PREVIOUS_STATE_KEY = stringPreferencesKey("previous_state")

        // Filter Config Keys. Used to be DIMMING_INTENSITY ("dimming_intensity") - removed for
        // good. That feature had not had a slider in the UI for a while (it was absorbed by Tone +
        // Intensity), but the field/key was still alive in the model and in persistence, always at
        // the default value, with no real way for the user to change it - even leaking into the
        // saved profile summary as if it were a real setting. An old install with this key saved
        // simply ignores it now. Also used to be RED_INTENSITY/YELLOW_INTENSITY/IS_RED_MODE (two
        // separate intensity sliders + a binary boolean) - simplified to a single intensity
        // (COLOR_INTENSITY) and a 4-color enum (FILTER_COLOR) now that Black/Orange also exist: one
        // intensity value per color does not scale and most users only use one color at a time
        // anyway. Migration: if the install only has the old keys, the intensity value migrates
        // from RED_INTENSITY (if isRedMode) or YELLOW_INTENSITY (otherwise), and the color migrates
        // from IS_RED_MODE - see filterConfigFlow below.
        val COLOR_INTENSITY = intPreferencesKey("color_intensity")
        val FILTER_COLOR = stringPreferencesKey("filter_color")
        @Deprecated("Migrated to COLOR_INTENSITY - kept only to read old installs")
        val RED_INTENSITY = intPreferencesKey("red_intensity")
        @Deprecated("Migrated to COLOR_INTENSITY - kept only to read old installs")
        val YELLOW_INTENSITY = intPreferencesKey("yellow_intensity")
        @Deprecated("Migrated to FILTER_COLOR - kept only to read old installs")
        val IS_RED_MODE = booleanPreferencesKey("is_red_mode")
        val TONE_INTENSITY = intPreferencesKey("tone_intensity")
        // Used to be only a field in the data model, with no persistence key at all - the "Double
        // filter" toggle never survived an app restart. Fixed along the way.
        val DOUBLE_FILTER_ENABLED = booleanPreferencesKey("double_filter_enabled")

        // Schedule Config Keys
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val AUTO_SUNSET_SUNRISE = booleanPreferencesKey("auto_sunset_sunrise")
        val START_HOUR = intPreferencesKey("start_hour")
        val START_MINUTE = intPreferencesKey("start_minute")
        val END_HOUR = intPreferencesKey("end_hour")
        val END_MINUTE = intPreferencesKey("end_minute")
        val LATITUDE = stringPreferencesKey("latitude")
        val LONGITUDE = stringPreferencesKey("longitude")
        val DAYS_OF_WEEK = stringPreferencesKey("days_of_week")

        // Profiles & Exclusions
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val PROFILE_SCHEDULES_JSON = stringPreferencesKey("profile_schedules_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
        val GRAYSCALE_EXCLUDED_PACKAGES = stringSetPreferencesKey("grayscale_excluded_packages")

        // Settings Keys
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIMARY_ACCENT_COLOR = stringPreferencesKey("primary_accent_color")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val PAUSE_ON_REBOOT = booleanPreferencesKey("pause_on_reboot")
        val USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
        val USE_ROOT = booleanPreferencesKey("use_root")
        val EXTRA_DIM_ENABLED = booleanPreferencesKey("extra_dim_enabled")
        val SYSTEM_COLOR_ADJUSTMENT_ENABLED = booleanPreferencesKey("system_color_adjustment_enabled")
        val GRAYSCALE_ENABLED = booleanPreferencesKey("grayscale_enabled")
        // Used to be REDSCALE_ENABLED ("redscale_enabled") - removed for good (it is not a
        // temporarily paused feature, it is redundant and replaced by SYSTEM_FILTER_ENABLED, which
        // covers the same case with real sliders instead of a fixed state). An old install with
        // this key saved as true simply ignores it now - DataStore does not fail when it finds a
        // key the code no longer reads.
        val SYSTEM_FILTER_ENABLED = booleanPreferencesKey("system_filter_enabled")
        // Used to be NAV_BAR_POSITION (Top/Left/Bottom chosen by the user) - removed, the position
        // is now decided automatically by the screen width (see BloodMoonApp in MainActivity.kt).
        // An old install with this key saved simply ignores it.
        val FADE_DURATION_MS = intPreferencesKey("fade_duration_ms")
        // TV-only features (see util/DeviceType.kt and util/TvPowerHelper.kt) - "Timed power-off"
        // and "Automatic standby", both under Advanced features.
        val TV_AUTO_OFF_ENABLED = booleanPreferencesKey("tv_auto_off_enabled")
        val TV_AUTO_OFF_HOUR = intPreferencesKey("tv_auto_off_hour")
        val TV_AUTO_OFF_MINUTE = intPreferencesKey("tv_auto_off_minute")
        val TV_STANDBY_ENABLED = booleanPreferencesKey("tv_standby_enabled")
        val TV_STANDBY_MINUTES = intPreferencesKey("tv_standby_minutes")
        val USE_ACCESSIBILITY_OVERLAY = booleanPreferencesKey("use_accessibility_overlay")
        val A11Y_WARNING_DISMISSED = booleanPreferencesKey("a11y_warning_dismissed")
        val ADB_ENABLED = booleanPreferencesKey("adb_enabled")
        val APPLY_LATEST_PROFILE_ON_BOOT = booleanPreferencesKey("apply_latest_profile_on_boot")
        val INCLUDE_SYSTEM_APPS_IN_EXCLUSIONS = booleanPreferencesKey("include_system_apps_in_exclusions")

        /**
         * Normalizes the days of the week read from JSON. If the generic signature of List<Int> is
         * lost (R8 without a keep rule), Gson returns Double (1.0) disguised as Int and any
         * sorted()/in blows up with ClassCastException or never matches. Converting here, no
         * suspicious list reaches the UI or the schedule engine.
         */
        fun sanitizeDays(days: List<*>?): List<Int> =
            (days ?: emptyList<Any?>()).mapNotNull { (it as? Number)?.toInt() }
                .filter { it in 1..7 }.distinct().sorted()

        fun sanitize(entry: ProfileScheduleEntry): ProfileScheduleEntry =
            entry.copy(daysOfWeek = sanitizeDays(entry.daysOfWeek))
    }

    val filterStateFlow: Flow<FilterState> = appContext.dataStore.data.map { prefs ->
        val name = prefs[FILTER_STATE_KEY] ?: FilterState.OFF.name
        runCatching { FilterState.valueOf(name) }.getOrDefault(FilterState.OFF)
    }.distinctUntilChanged()

    val filterConfigFlow: Flow<FilterConfig> = appContext.dataStore.data.map { prefs ->
        val migratedColor = prefs[FILTER_COLOR]?.let {
            runCatching { FilterColor.valueOf(it) }.getOrNull()
        } ?: run {
            // No FILTER_COLOR saved yet - an old install (or a new one, it does not matter: the
            // default here matches the default of IS_RED_MODE=true anyway) migrating from
            // IS_RED_MODE.
            @Suppress("DEPRECATION")
            if (prefs[IS_RED_MODE] ?: true) FilterColor.RED else FilterColor.YELLOW
        }
        val migratedIntensity = prefs[COLOR_INTENSITY] ?: run {
            // Same logic: no COLOR_INTENSITY yet, migrates from the old slider corresponding to the
            // color just resolved above.
            @Suppress("DEPRECATION")
            if (migratedColor == FilterColor.RED) prefs[RED_INTENSITY] ?: 50 else prefs[YELLOW_INTENSITY] ?: 50
        }
        FilterConfig(
            color = migratedColor,
            colorIntensity = migratedIntensity,
            toneIntensity = prefs[TONE_INTENSITY] ?: 100,
            doubleFilterEnabled = prefs[DOUBLE_FILTER_ENABLED] ?: false
        )
    }.distinctUntilChanged()

    val scheduleConfigFlow: Flow<ScheduleConfig> = appContext.dataStore.data.map { prefs ->
        val daysJson = prefs[DAYS_OF_WEEK] ?: "[1,2,3,4,5,6,7]"
        val type = object : TypeToken<List<Int>>() {}.type
        val days: List<Int> = runCatching { sanitizeDays(gson.fromJson<List<Int>>(daysJson, type)) }
            .getOrDefault(listOf(1, 2, 3, 4, 5, 6, 7))

        ScheduleConfig(
            enabled = prefs[SCHEDULE_ENABLED] ?: false,
            autoSunsetSunrise = prefs[AUTO_SUNSET_SUNRISE] ?: false,
            startHour = prefs[START_HOUR] ?: 22,
            startMinute = prefs[START_MINUTE] ?: 0,
            endHour = prefs[END_HOUR] ?: 7,
            endMinute = prefs[END_MINUTE] ?: 0,
            latitude = (prefs[LATITUDE] ?: "0.0").toDoubleOrNull() ?: 0.0,
            longitude = (prefs[LONGITUDE] ?: "0.0").toDoubleOrNull() ?: 0.0,
            daysOfWeek = days
        )
    }.distinctUntilChanged()

    val profilesFlow: Flow<List<FilterProfile>> = appContext.dataStore.data.map { prefs ->
        val json = prefs[PROFILES_JSON] ?: "[]"
        val type = object : TypeToken<List<FilterProfile>>() {}.type
        runCatching { gson.fromJson<List<FilterProfile>>(json, type) }.getOrDefault(emptyList())
    }.distinctUntilChanged()

    val activeProfileIdFlow: Flow<String?> = appContext.dataStore.data.map { prefs ->
        prefs[ACTIVE_PROFILE_ID]
    }.distinctUntilChanged()

    val excludedPackagesFlow: Flow<Set<String>> = appContext.dataStore.data.map { prefs ->
        prefs[EXCLUDED_PACKAGES] ?: emptySet()
    }.distinctUntilChanged()

    val appSettingsFlow: Flow<AppSettings> = appContext.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { AppThemeMode.valueOf(prefs[THEME_MODE] ?: AppThemeMode.SYSTEM.name) }.getOrDefault(AppThemeMode.SYSTEM),
            primaryAccentColor = prefs[PRIMARY_ACCENT_COLOR] ?: "#C41E3A",
            languageCode = prefs[LANGUAGE_CODE] ?: "system",
            pauseOnReboot = prefs[PAUSE_ON_REBOOT] ?: true,
            useShizuku = prefs[USE_SHIZUKU] ?: false,
            useRoot = prefs[USE_ROOT] ?: false,
            extraDimEnabled = prefs[EXTRA_DIM_ENABLED] ?: false,
            systemColorAdjustmentEnabled = prefs[SYSTEM_COLOR_ADJUSTMENT_ENABLED] ?: false,
            grayscaleEnabled = prefs[GRAYSCALE_ENABLED] ?: false,
            systemFilterEnabled = prefs[SYSTEM_FILTER_ENABLED] ?: false,
            fadeDurationMs = prefs[FADE_DURATION_MS] ?: 900,
            tvAutoOffEnabled = prefs[TV_AUTO_OFF_ENABLED] ?: false,
            tvAutoOffHour = prefs[TV_AUTO_OFF_HOUR] ?: 23,
            tvAutoOffMinute = prefs[TV_AUTO_OFF_MINUTE] ?: 0,
            tvStandbyEnabled = prefs[TV_STANDBY_ENABLED] ?: false,
            tvStandbyMinutes = prefs[TV_STANDBY_MINUTES] ?: 30,
            useAccessibilityOverlay = prefs[USE_ACCESSIBILITY_OVERLAY] ?: true,
            a11yWarningDismissed = prefs[A11Y_WARNING_DISMISSED] ?: false,
            adbEnabled = prefs[ADB_ENABLED],
            applyLatestProfileOnBoot = prefs[APPLY_LATEST_PROFILE_ON_BOOT] ?: true,
            includeSystemAppsInExclusions = prefs[INCLUDE_SYSTEM_APPS_IN_EXCLUSIONS] ?: false
        )
    }.distinctUntilChanged()

    suspend fun setFilterState(state: FilterState) {
        appContext.dataStore.edit { prefs ->
            val currentState = prefs[FILTER_STATE_KEY] ?: FilterState.OFF.name
            prefs[PREVIOUS_STATE_KEY] = currentState
            prefs[FILTER_STATE_KEY] = state.name
        }
    }

    suspend fun updateFilterConfig(config: FilterConfig) {
        appContext.dataStore.edit { prefs ->
            prefs[FILTER_COLOR] = config.color.name
            prefs[COLOR_INTENSITY] = config.colorIntensity
            prefs[TONE_INTENSITY] = config.toneIntensity
            prefs[DOUBLE_FILTER_ENABLED] = config.doubleFilterEnabled
            // RED_INTENSITY/YELLOW_INTENSITY/IS_RED_MODE are no longer written (only read, for
            // migration, in filterConfigFlow) - once this app writes FILTER_COLOR/COLOR_INTENSITY
            // for the first time, the migration read never enters the old branch again, since
            // prefs[FILTER_COLOR] now exists.
        }
    }

    suspend fun updateScheduleConfig(schedule: ScheduleConfig) {
        appContext.dataStore.edit { prefs ->
            prefs[SCHEDULE_ENABLED] = schedule.enabled
            prefs[AUTO_SUNSET_SUNRISE] = schedule.autoSunsetSunrise
            prefs[START_HOUR] = schedule.startHour
            prefs[START_MINUTE] = schedule.startMinute
            prefs[END_HOUR] = schedule.endHour
            prefs[END_MINUTE] = schedule.endMinute
            prefs[LATITUDE] = schedule.latitude.toString()
            prefs[LONGITUDE] = schedule.longitude.toString()
            prefs[DAYS_OF_WEEK] = gson.toJson(schedule.daysOfWeek)
        }
    }

    suspend fun saveProfile(profile: FilterProfile) {
        val current = profilesFlow.first().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        appContext.dataStore.edit { prefs ->
            prefs[PROFILES_JSON] = gson.toJson(current)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        val current = profilesFlow.first().filter { it.id != profileId }
        appContext.dataStore.edit { prefs ->
            prefs[PROFILES_JSON] = gson.toJson(current)
            if (prefs[ACTIVE_PROFILE_ID] == profileId) {
                prefs.remove(ACTIVE_PROFILE_ID)
            }
            // Schedules that would activate the deleted profile have nothing left to activate.
            val schedulesJson = prefs[PROFILE_SCHEDULES_JSON]
            if (!schedulesJson.isNullOrBlank()) {
                val kept = runCatching {
                    gson.fromJson<List<ProfileScheduleEntry>>(
                        schedulesJson, object : com.google.gson.reflect.TypeToken<List<ProfileScheduleEntry>>() {}.type
                    )?.filter { it.profileId != profileId }
                }.getOrNull()
                if (kept != null) prefs[PROFILE_SCHEDULES_JSON] = gson.toJson(kept)
            }
        }
    }

    suspend fun setActiveProfile(profileId: String?) {
        appContext.dataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(ACTIVE_PROFILE_ID)
            } else {
                prefs[ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    private fun keyFor(kind: ExclusionKind) = when (kind) {
        ExclusionKind.FILTER -> EXCLUDED_PACKAGES
        ExclusionKind.GRAYSCALE -> GRAYSCALE_EXCLUDED_PACKAGES
    }

    /** Apps excluded from each feature (filter, grayscale, system filter). */
    fun excludedFlow(kind: ExclusionKind): Flow<Set<String>> {
        val key = keyFor(kind)
        return appContext.dataStore.data.map { prefs -> prefs[key] ?: emptySet() }
    }

    // ---------------------------------------------------------------------------------------
    // Profile schedules ("at this time, on these days, activate this profile")
    // ---------------------------------------------------------------------------------------

    val profileSchedulesFlow: Flow<List<ProfileScheduleEntry>> =
        appContext.dataStore.data.map { prefs ->
            val json = prefs[PROFILE_SCHEDULES_JSON]
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    (gson.fromJson<List<ProfileScheduleEntry>>(
                        json, object : com.google.gson.reflect.TypeToken<List<ProfileScheduleEntry>>() {}.type
                    ) ?: emptyList()).map { sanitize(it) }
                }.getOrDefault(emptyList())
            }
        }

    suspend fun setProfileSchedules(entries: List<ProfileScheduleEntry>) {
        appContext.dataStore.edit { prefs -> prefs[PROFILE_SCHEDULES_JSON] = gson.toJson(entries) }
    }

    suspend fun toggleExcluded(kind: ExclusionKind, packageName: String) {
        val key = keyFor(kind)
        appContext.dataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).toMutableSet()
            if (!current.add(packageName)) current.remove(packageName)
            prefs[key] = current
        }
    }

    suspend fun setExcluded(kind: ExclusionKind, packages: Set<String>) {
        val key = keyFor(kind)
        appContext.dataStore.edit { prefs -> prefs[key] = packages }
    }

    suspend fun toggleExcludedPackage(packageName: String) {
        val current = excludedPackagesFlow.first().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        appContext.dataStore.edit { prefs ->
            prefs[EXCLUDED_PACKAGES] = current
        }
    }

    /** Replaces the whole excluded-packages set at once - used by "Select all"/"Deselect all". */
    suspend fun setExcludedPackages(packages: Set<String>) {
        appContext.dataStore.edit { prefs ->
            prefs[EXCLUDED_PACKAGES] = packages
        }
    }

    suspend fun clearExcludedPackages() {
        appContext.dataStore.edit { prefs ->
            prefs[EXCLUDED_PACKAGES] = emptySet()
        }
    }

    suspend fun updateAppSettings(settings: AppSettings) {
        // Mirrored synchronously so MainActivity.attachBaseContext() can read the language
        // choice before DataStore/Compose exist yet. See LocaleHelper for why.
        LocaleHelper.persist(appContext, settings.languageCode)
        appContext.dataStore.edit { prefs ->
            prefs[THEME_MODE] = settings.themeMode.name
            prefs[PRIMARY_ACCENT_COLOR] = settings.primaryAccentColor
            prefs[LANGUAGE_CODE] = settings.languageCode
            prefs[PAUSE_ON_REBOOT] = settings.pauseOnReboot
            prefs[USE_SHIZUKU] = settings.useShizuku
            prefs[USE_ROOT] = settings.useRoot
            prefs[EXTRA_DIM_ENABLED] = settings.extraDimEnabled
            prefs[SYSTEM_COLOR_ADJUSTMENT_ENABLED] = settings.systemColorAdjustmentEnabled
            prefs[GRAYSCALE_ENABLED] = settings.grayscaleEnabled
            prefs[SYSTEM_FILTER_ENABLED] = settings.systemFilterEnabled
            prefs[FADE_DURATION_MS] = settings.fadeDurationMs
            prefs[TV_AUTO_OFF_ENABLED] = settings.tvAutoOffEnabled
            prefs[TV_AUTO_OFF_HOUR] = settings.tvAutoOffHour
            prefs[TV_AUTO_OFF_MINUTE] = settings.tvAutoOffMinute
            prefs[TV_STANDBY_ENABLED] = settings.tvStandbyEnabled
            prefs[TV_STANDBY_MINUTES] = settings.tvStandbyMinutes
            prefs[USE_ACCESSIBILITY_OVERLAY] = settings.useAccessibilityOverlay
            prefs[A11Y_WARNING_DISMISSED] = settings.a11yWarningDismissed
            settings.adbEnabled?.let { prefs[ADB_ENABLED] = it }
            prefs[APPLY_LATEST_PROFILE_ON_BOOT] = settings.applyLatestProfileOnBoot
            prefs[INCLUDE_SYSTEM_APPS_IN_EXCLUSIONS] = settings.includeSystemAppsInExclusions
        }
    }

    /** Writes only the "Include system apps" checkbox (Exclusions), without rewriting the other options. */
    suspend fun setIncludeSystemAppsInExclusions(include: Boolean) {
        appContext.dataStore.edit { prefs -> prefs[INCLUDE_SYSTEM_APPS_IN_EXCLUSIONS] = include }
    }

    suspend fun exportConfigJson(): String {
        val export = ExportData(
            filterConfig = filterConfigFlow.first(),
            scheduleConfig = scheduleConfigFlow.first(),
            profiles = profilesFlow.first(),
            excludedPackages = excludedPackagesFlow.first().toList(),
            appSettings = appSettingsFlow.first(),
            grayscaleExcludedPackages = excludedFlow(ExclusionKind.GRAYSCALE).first().toList(),
            profileSchedules = profileSchedulesFlow.first()
        )
        return gson.toJson(export)
    }

    /**
     * Returns null on success, or a human-readable reason on failure so the UI can show the
     * user *why* the import failed instead of a generic error.
     */
    suspend fun importConfigJson(json: String): String? {
        return try {
            val export = gson.fromJson(json, ExportData::class.java)
                ?: return "JSON vazio ou em formato inesperado"

            // Gson uses reflection and ignores Kotlin's static nullability: if the JSON lacks a
            // field, the value can end up null at runtime even though the type says otherwise.
            // @Suppress silences the compiler's "always false" warning - the check itself is real
            // and is the only defense against an incomplete/malformed backup.
            @Suppress("SENSELESS_COMPARISON")
            val missingFields = export.filterConfig == null ||
                export.scheduleConfig == null ||
                export.appSettings == null
            if (missingFields) {
                return "Incomplete JSON: required backup fields are missing"
            }

            // Same reason as the @Suppress("SENSELESS_COMPARISON") above: Gson deserializes via
            // reflection and does NOT respect the nullability declared in Kotlin - a backup JSON
            // without the "profiles"/"excludedPackages" field fills in null here even though these
            // properties are declared non-nullable. The compiler, looking only at the static type,
            // has no way of knowing that and flags the ?: as always redundant (USELESS_ELVIS) - it
            // is still the only real defense against an incomplete backup.
            @Suppress("USELESS_ELVIS")
            val profiles: List<FilterProfile> = export.profiles ?: emptyList()
            @Suppress("USELESS_ELVIS")
            val excludedPackages: List<String> = export.excludedPackages ?: emptyList()

            val previousSettings = appSettingsFlow.first()

            updateFilterConfig(export.filterConfig)
            updateScheduleConfig(export.scheduleConfig)
            appContext.dataStore.edit { prefs ->
                prefs[PROFILES_JSON] = gson.toJson(profiles)
                prefs[EXCLUDED_PACKAGES] = excludedPackages.toSet()
                prefs[GRAYSCALE_EXCLUDED_PACKAGES] = (export.grayscaleExcludedPackages ?: emptyList()).toSet()
                // Only schedules whose profile exists in the backup are included.
                val profileIds = profiles.map { it.id }.toSet()
                val importedSchedules = (export.profileSchedules ?: emptyList())
                    .filter { it.profileId in profileIds }
                    .map { sanitize(it) }
                prefs[PROFILE_SCHEDULES_JSON] = gson.toJson(importedSchedules)
                // The "active" profile is this device's state, not part of the backup.
                prefs.remove(ACTIVE_PROFILE_ID)
            }

            // Root, Shizuku and ADB describe what THIS device has; a backup made on another device
            // cannot turn them on here (the advanced features would show up as "available" with no
            // access at all). The dismissed accessibility notice also belongs to the device.
            val importedSettings = export.appSettings.copy(
                useRoot = previousSettings.useRoot,
                useShizuku = previousSettings.useShizuku,
                adbEnabled = previousSettings.adbEnabled,
                a11yWarningDismissed = previousSettings.a11yWarningDismissed
            )
            updateAppSettings(importedSettings)

            // Reschedules alarms, applies the sleep timer and updates the widget: before, the
            // backup only wrote the values and none of that was redone until the app restarted.
            ConfigEffects.applyAppSettingsChange(appContext, previousSettings, importedSettings)
            runCatching { ScheduleAlarmReceiver.rescheduleFromConfig(appContext) }
            runCatching { ProfileScheduleReceiver.rescheduleFromConfig(appContext) }
            null
        } catch (e: Exception) {
            e.message ?: e::class.java.simpleName
        }
    }
}
