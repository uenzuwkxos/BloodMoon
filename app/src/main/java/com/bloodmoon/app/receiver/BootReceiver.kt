package com.bloodmoon.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.data.repository.FilterState
import com.bloodmoon.app.service.FilterOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        // Time/timezone change: the alarms (schedule, TV power-off, profile schedules) end up at
        // the wrong time and need to be re-armed; the rest (filter state) only applies on boot.
        if (!isBoot && action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED) return

        // goAsync() keeps the receiver's process alive with elevated priority until finish() is
        // called - without it, Android is free to kill the process as soon as onReceive()
        // returns, which can (and, at boot, often does) happen before our coroutine finishes.
        val pendingResult = goAsync()
        val repository = BloodMoonRepository(context)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appSettings = repository.appSettingsFlow.first()
                if (isBoot) {
                val currentState = repository.filterStateFlow.first()

                if (appSettings.pauseOnReboot) {
                    // User explicitly asked for the filter to stay off after a reboot,
                    // regardless of what it was doing before - so we don't touch it, or we
                    // make sure it's recorded as OFF if it somehow still says ACTIVE.
                    if (currentState != FilterState.OFF) {
                        repository.setFilterState(FilterState.OFF)
                    }
                } else if (currentState == FilterState.ACTIVE) {
                    // Resume exactly what was running before the reboot.
                    FilterOverlayService.start(context)
                }

                // Alarms don't survive a reboot - always rebuild the schedule from scratch here.
                }
                ScheduleAlarmReceiver.rescheduleFromConfig(context)
                TvAutoOffReceiver.rescheduleFromConfig(context)
                ProfileScheduleReceiver.rescheduleFromConfig(context)

                // "Apply latest schedule on start": if the device was off at the time of a
                // schedule, applies the profile of the most recent schedule now.
                val schedule = repository.scheduleConfigFlow.first()
                if (isBoot && appSettings.applyLatestProfileOnBoot &&
                    schedule.enabled && !schedule.autoSunsetSunrise
                ) {
                    val entries = repository.profileSchedulesFlow.first()
                    val latest = com.bloodmoon.app.util.ProfileScheduleEngine
                        .latestDue(entries, java.util.Calendar.getInstance())
                    val profile = latest?.let { entry ->
                        repository.profilesFlow.first().firstOrNull { it.id == entry.profileId }
                    }
                    if (profile != null) {
                        com.bloodmoon.app.util.ProfileApplier.apply(context, repository, profile)
                        FilterOverlayService.start(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
