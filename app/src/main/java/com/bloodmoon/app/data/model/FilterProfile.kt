package com.bloodmoon.app.data.model

import java.util.UUID

data class FilterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filterConfig: FilterConfig = FilterConfig(),
    val scheduleConfig: ScheduleConfig = ScheduleConfig(),
    // Extra Dim, Grayscale and System Filter are GLOBAL app settings (they live in AppSettings, not
    // here) - they remain controlled only by the toggles on the Home tab, and switching profiles
    // NEVER really turns any of them on/off. These three fields are just a snapshot of what their
    // state was at the moment the user saved the profile, shown in the saved summary (so it shows
    // Extra dim/Grayscale/System Filter only when they were enabled at save time) - purely
    // informational, with no effect when the profile is applied.
    val extraDimWasEnabled: Boolean = false,
    val grayscaleWasEnabled: Boolean = false,
    val systemFilterWasEnabled: Boolean = false,
    // TV features saved in the profile. Null in old profiles (saved before they existed here): they
    // count as off - activating the profile turns the TV features off.
    val tvAutoOffEnabled: Boolean? = null,
    val tvAutoOffHour: Int? = null,
    val tvAutoOffMinute: Int? = null,
    val tvStandbyEnabled: Boolean? = null,
    val tvStandbyMinutes: Int? = null
)

/**
 * Does the profile still describe exactly what is in use right now? When a profile is activated,
 * the app writes its filter configuration and turns Extra dim, Grayscale and System Filter on/off
 * as saved; if the user changes any of those afterwards (on the Home tab), the filter stays on and
 * follows the changes, but the profile stops showing as active - visual feedback only, to avoid
 * confusion.
 */
fun FilterProfile.matchesCurrent(config: FilterConfig, settings: AppSettings): Boolean =
    filterConfig == config &&
        extraDimWasEnabled == settings.extraDimEnabled &&
        grayscaleWasEnabled == settings.grayscaleEnabled &&
        systemFilterWasEnabled == settings.systemFilterEnabled &&
        // TV features the profile does not store (null, old profiles) count as OFF: the profile
        // describes the complete state, and anyone who lacks a feature gets it turned off when
        // activated.
        (tvAutoOffEnabled ?: false) == settings.tvAutoOffEnabled &&
        (tvAutoOffEnabled != true ||
            (tvAutoOffHour == settings.tvAutoOffHour && tvAutoOffMinute == settings.tvAutoOffMinute)) &&
        (tvStandbyEnabled ?: false) == settings.tvStandbyEnabled &&
        (tvStandbyEnabled != true || tvStandbyMinutes == settings.tvStandbyMinutes)
