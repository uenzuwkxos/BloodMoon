package com.bloodmoon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.data.model.ExclusionKind
import com.bloodmoon.app.data.model.ProfileScheduleEntry
import com.bloodmoon.app.data.model.FilterConfig
import com.bloodmoon.app.data.model.FilterProfile
import com.bloodmoon.app.data.model.matchesCurrent
import com.bloodmoon.app.data.model.ScheduleConfig
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.data.repository.FilterState
import com.bloodmoon.app.receiver.ScheduleAlarmReceiver
import com.bloodmoon.app.receiver.TvAutoOffReceiver
import com.bloodmoon.app.receiver.ProfileScheduleReceiver
import com.bloodmoon.app.service.FilterOverlayService
import com.bloodmoon.app.util.TvPowerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BloodMoonRepository(application)

    init {
        // Cheap safety net: re-sync the alarm schedule with whatever's persisted every time the
        // app is opened, in case alarms were lost (some OEMs aggressively clear them under Doze).
        viewModelScope.launch {
            runCatching { ScheduleAlarmReceiver.rescheduleFromConfig(application) }
            runCatching { TvAutoOffReceiver.rescheduleFromConfig(application) }
        }
        reconcileFilterState(application)
    }

    /**
     * The saved state may say ACTIVE with no service running (the TV/system killed the process, or
     * the service went down). Without this the button stayed "on" with no filter. Here the filter
     * is resumed (or, without the overlay permission, the state goes back to OFF).
     */
    private fun reconcileFilterState(application: Application) {
        viewModelScope.launch {
            val saved = repository.filterStateFlow.first()
            if (saved == FilterState.ACTIVE && !FilterOverlayService.isRunning) {
                if (FilterOverlayService.canDrawOverlay(application)) {
                    runCatching { FilterOverlayService.start(application) }
                        .onFailure { repository.setFilterState(FilterState.OFF) }
                } else {
                    repository.setFilterState(FilterState.OFF)
                }
            }
        }
    }

    val filterState: StateFlow<FilterState> = repository.filterStateFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), FilterState.OFF
    )

    val filterConfig: StateFlow<FilterConfig> = repository.filterConfigFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), FilterConfig()
    )

    val scheduleConfig: StateFlow<ScheduleConfig> = repository.scheduleConfigFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleConfig()
    )

    val profiles: StateFlow<List<FilterProfile>> = repository.profilesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Profile schedules: time + days + profile to activate. */
    val profileSchedules: StateFlow<List<ProfileScheduleEntry>> = repository.profileSchedulesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val activeProfileId: StateFlow<String?> = repository.activeProfileIdFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val excludedPackages: StateFlow<Set<String>> = repository.excludedPackagesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet()
    )

    val grayscaleExcludedPackages: StateFlow<Set<String>> =
        repository.excludedFlow(ExclusionKind.GRAYSCALE).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet()
        )

    val appSettings: StateFlow<AppSettings> = repository.appSettingsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    /** false until the first read of the settings from disk (the home screen stays visible). */
    val settingsLoaded: StateFlow<Boolean> = repository.appSettingsFlow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // filterState reflects DataStore, which is disk-backed I/O - after calling e.g.
    // FilterOverlayService.start(), there's a real (tens-to-hundreds of ms) window before
    // filterState.value actually flips to ACTIVE. Repeatedly tapping the power button inside
    // that window used to make every tap read the same stale OFF/ACTIVE value and dispatch the
    // same action again - e.g. several ACTION_START intents stacking up back to back, each
    // restarting the service's observeSettings()/startExclusionMonitor() jobs and racing to
    // add/remove the same overlay window. That race is what crashed the app when the toggle was
    // tapped rapidly. optimisticState plus the in-flight guard below fixes it at the source: the
    // very next tap is judged against what this ViewModel just told the service to do, not
    // against a DataStore read that hasn't landed yet, and a second tap while one is still being
    // dispatched is dropped rather than queued.
    private val optimisticState = MutableStateFlow<FilterState?>(null)
    private var toggleInFlight = false

    /** What the power button should actually display - the optimistic in-flight target while a
     * toggle is being dispatched, falling back to the real (DataStore-backed) state otherwise.
     * Screens should collect this instead of [filterState] so the button reflects the tap
     * immediately instead of waiting for DataStore to catch up. */
    val displayFilterState: StateFlow<FilterState> = combine(filterState, optimisticState) { real, optimistic ->
        optimistic ?: real
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterState.OFF)

    fun toggleFilter() {
        if (toggleInFlight) return
        toggleInFlight = true
        // Same power-button semantics as before: ACTIVE turns fully off, PAUSED resumes to
        // ACTIVE, OFF starts (goes to ACTIVE) - this is a two-way toggle, not a three-state
        // cycle. Only PAUSED is a separate state the notification's own Pause action can reach.
        val current = optimisticState.value ?: filterState.value
        val next = if (current == FilterState.ACTIVE) FilterState.OFF else FilterState.ACTIVE
        optimisticState.value = next
        viewModelScope.launch {
            // startForegroundService is a Binder call; off the main thread it does not compete with
            // the frames of the button animation.
            withContext(Dispatchers.Default) {
                runCatching {
                    when (current) {
                        FilterState.ACTIVE -> FilterOverlayService.stop(getApplication())
                        FilterState.PAUSED -> FilterOverlayService.resume(getApplication())
                        FilterState.OFF -> FilterOverlayService.start(getApplication())
                    }
                }
            }
            // Release the guard once the real filterState catches up to what we just requested
            // (or after a safety timeout, in case the service never reports back - e.g. the
            // overlay-permission-missing early return path - so the button never gets stuck).
            var waited = 0
            while (filterState.value != next && waited < 2000) {
                kotlinx.coroutines.delay(50)
                waited += 50
            }
            optimisticState.value = null
            toggleInFlight = false
        }
    }

    /** Called only when a slider gesture ends (see onValueChangeFinished in HomeScreen) - not on every drag frame. */
    fun updateFilterConfig(config: FilterConfig) {
        viewModelScope.launch {
            // Editing a slider by hand detaches you from whichever profile was active, since
            // the config no longer matches what that profile describes.
            if (activeProfileId.value != null) {
                repository.setActiveProfile(null)
            }
            repository.updateFilterConfig(config)
            // Keeps the home screen widget up to date even with the filter off.
            com.bloodmoon.app.widget.BloodMoonWidgetProvider.requestUpdate(getApplication())
        }
    }

    fun updateScheduleConfig(schedule: ScheduleConfig) {
        viewModelScope.launch {
            repository.updateScheduleConfig(schedule)
            ScheduleAlarmReceiver.rescheduleFromConfig(getApplication())
            // "Enable schedule" and "Sunset and sunrise" decide whether the profile schedules apply.
            ProfileScheduleReceiver.rescheduleFromConfig(getApplication())
        }
    }

    /** "Include system apps" checkbox (Exclusions): persisted, no longer just screen state. */
    fun setIncludeSystemAppsInExclusions(include: Boolean) {
        viewModelScope.launch { repository.setIncludeSystemAppsInExclusions(include) }
    }

    fun saveProfile(profile: FilterProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            // As agendas desse perfil foram apagadas junto: rearma (ou cancela) o alarme.
            ProfileScheduleReceiver.rescheduleFromConfig(getApplication())
        }
    }

    // ---- Profile schedules: each change re-arms the next-time alarm --------------------

    fun addProfileSchedule(entry: ProfileScheduleEntry) {
        viewModelScope.launch {
            repository.setProfileSchedules(repository.profileSchedulesFlow.first() + entry)
            ProfileScheduleReceiver.rescheduleFromConfig(getApplication())
        }
    }

    fun updateProfileSchedule(entry: ProfileScheduleEntry) {
        viewModelScope.launch {
            repository.setProfileSchedules(
                repository.profileSchedulesFlow.first().map { if (it.id == entry.id) entry else it }
            )
            ProfileScheduleReceiver.rescheduleFromConfig(getApplication())
        }
    }

    fun deleteProfileSchedule(entryId: String) {
        viewModelScope.launch {
            repository.setProfileSchedules(repository.profileSchedulesFlow.first().filter { it.id != entryId })
            ProfileScheduleReceiver.rescheduleFromConfig(getApplication())
        }
    }

    fun toggleProfile(profile: FilterProfile) {
        viewModelScope.launch {
            if (activeProfileId.value == profile.id && profile.matchesCurrent(filterConfig.value, appSettings.value)) {
                // Ativar -> Desativar: turn the filter off entirely, matching what the button now says.
                repository.setActiveProfile(null)
                FilterOverlayService.stop(getApplication())
            } else {
                // Activates the profile (filter + Extra dim, Grayscale, System Filter and TV
                // features). It no longer touches the Schedule: before, it overwrote it with the
                // profile's schedule, which was always the default off.
                com.bloodmoon.app.util.ProfileApplier.apply(getApplication(), repository, profile)
                FilterOverlayService.start(getApplication())
            }
        }
    }

    fun toggleExcluded(kind: ExclusionKind, packageName: String) {
        viewModelScope.launch { repository.toggleExcluded(kind, packageName) }
    }

    fun setExcluded(kind: ExclusionKind, packages: Set<String>) {
        viewModelScope.launch { repository.setExcluded(kind, packages) }
    }

    fun toggleExcludedPackage(packageName: String) {
        viewModelScope.launch {
            repository.toggleExcludedPackage(packageName)
        }
    }

    fun setExcludedPackages(packages: Set<String>) {
        viewModelScope.launch {
            repository.setExcludedPackages(packages)
        }
    }

    /**
     * Removes from the exclusion list packages that no longer exist in the installed-apps list
     * (uninstalled/disabled). They were what made the screen show "1 selected" with no app checked.
     */
    fun pruneExcludedPackages(installedPackages: Set<String>) {
        if (installedPackages.isEmpty()) return
        viewModelScope.launch {
            for (kind in ExclusionKind.values()) {
                val current = repository.excludedFlow(kind).first()
                val kept = current.intersect(installedPackages)
                if (kept.size != current.size) repository.setExcluded(kind, kept)
            }
        }
    }

    fun clearExcludedPackages() {
        viewModelScope.launch {
            repository.clearExcludedPackages()
        }
    }

    fun updateAppSettings(settings: AppSettings) {
        viewModelScope.launch {
            val previous = appSettings.value
            repository.updateAppSettings(settings)
            com.bloodmoon.app.widget.BloodMoonWidgetProvider.requestUpdate(getApplication())

            // Newly turned on Root/Shizuku/ADB: turns the accessibility service on right away (it
            // was the gap that made only ADB cover the whole system - the other two never turned it
            // on).
            if (settings.useAccessibilityOverlay && !settings.a11yWarningDismissed &&
                (settings.useRoot || settings.useShizuku || settings.adbEnabled == true)
            ) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        com.bloodmoon.app.util.AccessibilityServiceHelper
                            .ensureEnabledPrivileged(getApplication(), settings)
                    }
                }
            }

            // "Timed power-off" alarm and sleep timer: the same effects as when the change comes
            // from a profile or a backup (see ConfigEffects).
            com.bloodmoon.app.util.ConfigEffects.applyAppSettingsChange(getApplication(), previous, settings)
        }
    }
}
