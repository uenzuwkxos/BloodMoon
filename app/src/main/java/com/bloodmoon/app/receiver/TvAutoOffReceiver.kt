package com.bloodmoon.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.util.TvPowerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The engine behind "Timed power-off" (Advanced features, TV-only) - same architectural pattern as
 * ScheduleAlarmReceiver (schedule an alarm for the next time, reschedule the following one when it
 * fires), simplified to a single fixed daily time instead of a start/end window.
 */
class TvAutoOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val repository = BloodMoonRepository(context)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = repository.appSettingsFlow.first()
                if (settings.tvAutoOffEnabled) {
                    TvPowerHelper.turnOffScreenIfOn(context, settings.useRoot)
                }
                // Sempre reagenda, mesmo se o recurso foi desativado nesse meio-tempo -
                // scheduleNext abaixo simplesmente cancela qualquer alarme pendente e retorna
                // nesse caso.
                scheduleNext(context, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPLY = "com.bloodmoon.app.action.TV_AUTO_OFF"
        private const val REQUEST_CODE = 5101

        /** Call this any time AppSettings' tvAutoOff* fields change, and once at boot. */
        suspend fun rescheduleFromConfig(context: Context) {
            try {
                val repository = BloodMoonRepository(context)
                val settings = repository.appSettingsFlow.first()
                scheduleNext(context, settings)
            } catch (e: Exception) {
                com.bloodmoon.app.util.DiagnosticLog.log(context, "Timed power-off: failed to reschedule (${e.message})")
            }
        }

        private fun scheduleNext(context: Context, settings: AppSettings) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            cancelAlarm(context, alarmManager)

            if (!settings.tvAutoOffEnabled) return

            val next = nextOccurrence(settings.tvAutoOffHour, settings.tvAutoOffMinute)
            setAlarm(context, alarmManager, next)
        }

        /** Next Calendar instant (today or tomorrow) matching hour:minute. */
        private fun nextOccurrence(hour: Int, minute: Int): Calendar {
            val now = Calendar.getInstance()
            val candidate = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate
        }

        private fun setAlarm(context: Context, alarmManager: AlarmManager, time: Calendar) {
            val intent = Intent(context, TvAutoOffReceiver::class.java).apply { action = ACTION_APPLY }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // An exception here (e.g. SecurityException from a missing exact-alarm permission on a
            // new Android) killed the app on launch and, with the option saved, it never opened
            // again - that was the Android 17 crash. Now each attempt is protected, with the next
            // one as plan B: exact -> alarm clock -> inexact. Failing to schedule never brings the
            // app down.
            val showIntent = PendingIntent.getActivity(
                context, REQUEST_CODE,
                Intent(context, com.bloodmoon.app.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val attempts = listOf<() -> Unit>(
                {
                    val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        alarmManager.canScheduleExactAlarms()
                    check(canScheduleExact) { "exact alarm unavailable" }
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, time.timeInMillis, pendingIntent
                    )
                },
                {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(time.timeInMillis, showIntent), pendingIntent
                    )
                },
                {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, time.timeInMillis, pendingIntent)
                }
            )
            for ((index, attempt) in attempts.withIndex()) {
                try {
                    attempt()
                    return
                } catch (e: Exception) {
                    com.bloodmoon.app.util.DiagnosticLog.log(
                        context, "Timed power-off: scheduling attempt ${index + 1} failed (${e.javaClass.simpleName}: ${e.message})"
                    )
                }
            }
        }

        private fun cancelAlarm(context: Context, alarmManager: AlarmManager) {
            val intent = Intent(context, TvAutoOffReceiver::class.java).apply { action = ACTION_APPLY }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
