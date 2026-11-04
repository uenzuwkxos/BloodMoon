package com.bloodmoon.app.data.model

import java.util.UUID

/**
 * A profile schedule: "at this time, on these days, activate this profile". Part of "Schedules"
 * (only applies with "Enable schedule" on and Sunset and sunrise off).
 *
 * At the set time the profile is applied and the filter is turned on. If [useEndTime] is on, at the
 * end time ([endHour]:[endMinute]) the filter is turned off - if it is less than or equal to the
 * start time, it means the next day (e.g. starts at 22:00, ends at 07:00).
 *
 * [daysOfWeek] uses java.util.Calendar ids (1 = Sunday ... 7 = Saturday). All fields have default
 * values so Gson can read old backups (without the end-time fields).
 */
data class ProfileScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 22,
    val minute: Int = 0,
    val profileId: String = "",
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val enabled: Boolean = true,
    val useEndTime: Boolean = false,
    val endHour: Int = 7,
    val endMinute: Int = 0
)
