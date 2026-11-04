package com.bloodmoon.app.util

import android.content.Context
import com.bloodmoon.app.data.model.FilterProfile
import com.bloodmoon.app.data.repository.BloodMoonRepository
import kotlinx.coroutines.flow.first

/**
 * Applies a profile: marks it as active, writes the filter configuration and turns the advanced
 * features on/off as they were saved in it (Extra dim, Grayscale, System Filter and the TV
 * features). Used when tapping "Activate" and when a scheduled time with a profile starts. Does NOT
 * start the filter service - the caller decides.
 *
 * Old profiles (saved before the TV features became part of the profile) have these fields null,
 * which count as "off": activating the profile turns the TV features off.
 *
 * Important: the profile no longer touches the Schedule (ScheduleConfig). Before, activating a
 * profile overwrote the schedule with the profile's "scheduleConfig", which was always the default
 * off - meaning activating any profile turned off the configured schedule.
 */
object ProfileApplier {

    suspend fun apply(context: Context, repository: BloodMoonRepository, profile: FilterProfile) {
        val previous = repository.appSettingsFlow.first()
        repository.setActiveProfile(profile.id)
        repository.updateFilterConfig(profile.filterConfig)

        val updated = previous.copy(
            extraDimEnabled = profile.extraDimWasEnabled,
            grayscaleEnabled = profile.grayscaleWasEnabled,
            systemFilterEnabled = profile.systemFilterWasEnabled,
            // TV features: the profile describes the complete state. The ones it lacks (null, old
            // profiles, or saved as off) are turned OFF on activation - before they were left as
            // they were, and activating a profile without them did not turn them off. Saved
            // hour/minutes only apply when the feature is on in the profile.
            tvAutoOffEnabled = profile.tvAutoOffEnabled ?: false,
            tvAutoOffHour = if (profile.tvAutoOffEnabled == true) profile.tvAutoOffHour ?: previous.tvAutoOffHour else previous.tvAutoOffHour,
            tvAutoOffMinute = if (profile.tvAutoOffEnabled == true) profile.tvAutoOffMinute ?: previous.tvAutoOffMinute else previous.tvAutoOffMinute,
            tvStandbyEnabled = profile.tvStandbyEnabled ?: false,
            tvStandbyMinutes = if (profile.tvStandbyEnabled == true) profile.tvStandbyMinutes ?: previous.tvStandbyMinutes else previous.tvStandbyMinutes
        )
        repository.updateAppSettings(updated)
        ConfigEffects.applyAppSettingsChange(context, previous, updated)
    }
}
