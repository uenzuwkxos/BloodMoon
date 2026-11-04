package com.bloodmoon.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bloodmoon.app.data.model.ScheduleConfig
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.service.FilterOverlayService
import com.bloodmoon.app.util.LocationHelper
import com.bloodmoon.app.util.SunsetSunriseCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The actual engine behind "Schedules" - previously this screen only wrote a ScheduleConfig to disk
 * with nothing ever reading it back to act on it. This receiver is woken by AlarmManager at the
 * next relevant start/end time, flips the filter, and reschedules the *following* alarm
 * (recalculated every time, since sunset/sunrise shifts day to day).
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val repository = BloodMoonRepository(context)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val schedule = repository.scheduleConfigFlow.first()
                // The manual schedule is gone: this alarm now only serves Sunset and sunrise.
                // Start/end with a profile live in ProfileScheduleReceiver.
                if (schedule.enabled && schedule.autoSunsetSunrise) {
                    when (intent.action) {
                        ACTION_APPLY_START -> FilterOverlayService.start(context)
                        ACTION_APPLY_END -> FilterOverlayService.stop(context)
                    }
                }
                // Always reschedule, even if the feature was disabled in the meantime - if it's
                // off, scheduleNext() below simply cancels any pending alarms and returns.
                scheduleNext(context, schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class ResolvedTimes(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int)

    companion object {
        const val ACTION_APPLY_START = "com.bloodmoon.app.action.SCHEDULE_START"
        const val ACTION_APPLY_END = "com.bloodmoon.app.action.SCHEDULE_END"

        private const val REQUEST_CODE_START = 5001
        private const val REQUEST_CODE_END = 5002

        /** Call this any time ScheduleConfig changes, and once at boot. */
        suspend fun rescheduleFromConfig(context: Context) {
            // Never propagates an exception: an alarm that fails to be scheduled (exact-alarm
            // permission denied on a new Android) must not bring the app down on launch.
            try {
                val repository = BloodMoonRepository(context)
                val schedule = repository.scheduleConfigFlow.first()
                scheduleNext(context, schedule)
            } catch (e: Exception) {
                com.bloodmoon.app.util.DiagnosticLog.log(context, "Schedules: failed to reschedule (${e.message})")
            }
        }

        private fun scheduleNext(context: Context, schedule: ScheduleConfig) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            cancelAlarm(context, alarmManager, REQUEST_CODE_START, ACTION_APPLY_START)
            cancelAlarm(context, alarmManager, REQUEST_CODE_END, ACTION_APPLY_END)

            if (!schedule.enabled || !schedule.autoSunsetSunrise) return

            val resolved = resolveTimes(context, schedule)

            // Sunset and sunrise applies every day: only the location defines the times.
            val allDays = listOf(1, 2, 3, 4, 5, 6, 7)
            val nextStart = nextOccurrence(resolved.startHour, resolved.startMinute, allDays)
            val nextEnd = nextOccurrence(resolved.endHour, resolved.endMinute, allDays)

            setAlarm(context, alarmManager, REQUEST_CODE_START, ACTION_APPLY_START, nextStart)
            setAlarm(context, alarmManager, REQUEST_CODE_END, ACTION_APPLY_END, nextEnd)
        }

        private fun resolveTimes(context: Context, schedule: ScheduleConfig): ResolvedTimes {
            if (!schedule.autoSunsetSunrise) {
                return ResolvedTimes(schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute)
            }
            // Sunset -> filter turns ON, Sunrise -> filter turns OFF. Prefer a fresh device fix
            // over whatever lat/lng happened to be saved last, when we can get one.
            val location = LocationHelper.getLastKnownLocation(context)
            val lat = location?.latitude ?: schedule.latitude
            val lng = location?.longitude ?: schedule.longitude
            val times = SunsetSunriseCalculator.calculate(lat, lng)
            return ResolvedTimes(times.sunsetHour, times.sunsetMinute, times.sunriseHour, times.sunriseMinute)
        }

        /** Next Calendar instant (today or up to 7 days out) matching hour:minute on an active weekday. */
        private fun nextOccurrence(hour: Int, minute: Int, activeDays: List<Int>): Calendar {
            val now = Calendar.getInstance()
            for (dayOffset in 0..7) {
                val candidate = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // Calendar.DAY_OF_WEEK is 1=Sunday..7=Saturday, matching ScheduleConfig's convention.
                val weekday = candidate.get(Calendar.DAY_OF_WEEK)
                if (weekday !in activeDays) continue
                if (candidate.timeInMillis <= now.timeInMillis) continue
                return candidate
            }
            // No active day found in the next week (shouldn't happen if activeDays is non-empty) -
            // fall back to exactly 24h from now so we don't leave the schedule silently dead.
            return (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        }

        private fun setAlarm(
            context: Context,
            alarmManager: AlarmManager,
            requestCode: Int,
            action: String,
            time: Calendar
        ) {
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply { this.action = action }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()

            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, time.timeInMillis, pendingIntent
                )
            } else {
                // Missing "Alarms & reminders" permission on Android 12+ - degrade to an inexact
                // alarm rather than silently doing nothing. It may fire a little late, but the
                // schedule keeps working instead of just failing quietly.
                alarmManager.set(AlarmManager.RTC_WAKEUP, time.timeInMillis, pendingIntent)
            }
        }

        private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int, action: String) {
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply { this.action = action }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
