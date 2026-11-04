package com.bloodmoon.app.util

import com.bloodmoon.app.data.model.ProfileScheduleEntry
import java.util.Calendar

/** Time calculations for profile schedules (no Android, just calendar). */
object ProfileScheduleEngine {

    /** A trigger: [isStart] = applies the profile and turns the filter on; otherwise = turns the filter off. */
    data class Event(val time: Long, val isStart: Boolean, val entry: ProfileScheduleEntry)

    private fun usable(entry: ProfileScheduleEntry) =
        entry.enabled && entry.profileId.isNotBlank() && entry.daysOfWeek.isNotEmpty()

    private fun at(base: Calendar, dayOffset: Int, hour: Int, minute: Int): Calendar =
        (base.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /**
     * All triggers of the usable schedules from [fromOffset] to [toOffset] days relative to [now].
     * The day of the week applies to the START; the end is the first end time after it (same day,
     * or the next day if the end time is less than or equal to the start).
     */
    private fun events(
        entries: List<ProfileScheduleEntry>,
        now: Calendar,
        fromOffset: Int,
        toOffset: Int
    ): List<Event> {
        val result = ArrayList<Event>()
        for (entry in entries.filter(::usable)) {
            for (offset in fromOffset..toOffset) {
                val start = at(now, offset, entry.hour, entry.minute)
                if (start.get(Calendar.DAY_OF_WEEK) !in entry.daysOfWeek) continue
                result += Event(start.timeInMillis, true, entry)
                if (entry.useEndTime) {
                    var end = at(now, offset, entry.endHour, entry.endMinute)
                    if (end.timeInMillis <= start.timeInMillis) end = at(now, offset + 1, entry.endHour, entry.endMinute)
                    result += Event(end.timeInMillis, false, entry)
                }
            }
        }
        return result
    }

    /** Next trigger (in millis) across all active schedules, or null if there are none. */
    fun nextTrigger(entries: List<ProfileScheduleEntry>, now: Calendar): Long? =
        events(entries, now, -1, 8)
            .filter { it.time > now.timeInMillis }
            .minOfOrNull { it.time }

    /** Triggers (start and/or end) that fall exactly on the minute of [triggerAtMillis]. */
    fun dueAt(entries: List<ProfileScheduleEntry>, triggerAtMillis: Long): List<Event> {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        return events(entries, cal, -1, 1).filter { it.time / 60_000L == triggerAtMillis / 60_000L }
    }

    /**
     * The most recent schedule that should already have been applied (up to 7 days ago) - used on
     * boot, in case the device was off at the set time. It only counts if the last trigger was a
     * START: if the end of the window has already passed, the filter should stay off.
     */
    fun latestDue(entries: List<ProfileScheduleEntry>, now: Calendar): ProfileScheduleEntry? {
        val last = events(entries, now, -8, 0)
            .filter { it.time <= now.timeInMillis }
            .maxByOrNull { it.time }
        return last?.takeIf { it.isStart }?.entry
    }
}
