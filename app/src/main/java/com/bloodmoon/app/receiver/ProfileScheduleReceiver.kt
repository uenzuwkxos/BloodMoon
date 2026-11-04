package com.bloodmoon.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.service.FilterOverlayService
import com.bloodmoon.app.util.DiagnosticLog
import com.bloodmoon.app.util.ProfileApplier
import com.bloodmoon.app.util.ProfileScheduleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fires the profile schedules. A single alarm is always armed for the NEXT time across all active
 * schedules; when it fires, it applies the profile of the schedules at that time, turns the filter
 * on and arms the next alarm.
 */
class ProfileScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = BloodMoonRepository(appContext)
                val schedule = repository.scheduleConfigFlow.first()
                // Only applies with "Enable schedule" on and without Sunset/sunrise (which hides
                // and replaces the schedules).
                if (schedule.enabled && !schedule.autoSunsetSunrise) {
                    val entries = repository.profileSchedulesFlow.first()
                    val due = ProfileScheduleEngine.dueAt(entries, triggerAt)
                    // If two schedules start in the same minute, the last one in the list wins.
                    // Start and end in the same minute: the start wins (the filter stays on).
                    val start = due.lastOrNull { it.isStart }
                    if (start != null) {
                        val profile = repository.profilesFlow.first().firstOrNull { it.id == start.entry.profileId }
                        if (profile != null) {
                            ProfileApplier.apply(appContext, repository, profile)
                            FilterOverlayService.start(appContext)
                            DiagnosticLog.log(appContext, "profile schedule applied: ${profile.name}")
                        }
                    } else if (due.any { !it.isStart }) {
                        FilterOverlayService.stop(appContext)
                        DiagnosticLog.log(appContext, "profile schedule: end time reached, filter turned off")
                    }
                }
            } catch (e: Exception) {
                DiagnosticLog.log(appContext, "profile schedule failed: ${e.message}")
            } finally {
                try {
                    rescheduleFromConfig(appContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.bloodmoon.app.action.FIRE_PROFILE_SCHEDULE"
        private const val EXTRA_TRIGGER_AT = "trigger_at"
        private const val REQUEST_CODE = 7101

        private fun pendingIntent(context: Context, triggerAt: Long): PendingIntent {
            val intent = Intent(context, ProfileScheduleReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_TRIGGER_AT, triggerAt)
            }
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Re-arms (or cancels) the alarm according to the saved schedules. Never throws. */
        suspend fun rescheduleFromConfig(context: Context) {
            try {
                val appContext = context.applicationContext
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val repository = BloodMoonRepository(appContext)
                val schedule = repository.scheduleConfigFlow.first()
                val entries = if (schedule.enabled && !schedule.autoSunsetSunrise) {
                    repository.profileSchedulesFlow.first()
                } else {
                    emptyList()
                }
                val next = ProfileScheduleEngine.nextTrigger(entries, Calendar.getInstance())

                if (next == null) {
                    alarmManager.cancel(pendingIntent(appContext, 0L))
                    return
                }
                val pending = pendingIntent(appContext, next)
                val showIntent = PendingIntent.getActivity(
                    appContext, REQUEST_CODE,
                    Intent(appContext, com.bloodmoon.app.ui.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Exact -> alarm clock -> inexact (same chain as "Timed power-off"): failing to
                // schedule never brings the app down.
                val attempts = listOf<() -> Unit>(
                    {
                        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                            "exact alarm unavailable"
                        }
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
                    },
                    { alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(next, showIntent), pending) },
                    { alarmManager.set(AlarmManager.RTC_WAKEUP, next, pending) }
                )
                for ((index, attempt) in attempts.withIndex()) {
                    try {
                        attempt()
                        return
                    } catch (e: Exception) {
                        DiagnosticLog.log(appContext, "profile schedule: scheduling attempt ${index + 1} failed (${e.message})")
                    }
                }
            } catch (e: Exception) {
                DiagnosticLog.log(context, "profile schedule: failed to reschedule (${e.message})")
            }
        }
    }
}
