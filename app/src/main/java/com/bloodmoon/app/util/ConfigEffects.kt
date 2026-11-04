package com.bloodmoon.app.util

import android.content.Context
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.receiver.TvAutoOffReceiver
import com.bloodmoon.app.widget.BloodMoonWidgetProvider

/**
 * Side effects that accompany a change in [AppSettings] - the same whether the change comes from
 * the user (Home/Options), an activated profile or an imported backup: - "Timed power-off" (TV):
 * reschedules the alarm; - "Sleep timer" (TV): applies/restores the system sleep time; - home
 * screen widget: redraws. Before, this lived only in the ViewModel, so activating a profile or
 * importing a backup changed the values without rescheduling anything.
 */
object ConfigEffects {

    suspend fun applyAppSettingsChange(context: Context, previous: AppSettings, current: AppSettings) {
        val appContext = context.applicationContext

        if (previous.tvAutoOffEnabled != current.tvAutoOffEnabled ||
            previous.tvAutoOffHour != current.tvAutoOffHour ||
            previous.tvAutoOffMinute != current.tvAutoOffMinute
        ) {
            runCatching { TvAutoOffReceiver.rescheduleFromConfig(appContext) }
        }

        if (previous.tvStandbyEnabled != current.tvStandbyEnabled ||
            previous.tvStandbyMinutes != current.tvStandbyMinutes
        ) {
            val minutes = if (current.tvStandbyEnabled) current.tvStandbyMinutes else null
            runCatching { TvPowerHelper.setSleepTimeoutMinutes(appContext, minutes) }
        }

        runCatching { BloodMoonWidgetProvider.requestUpdate(appContext) }
    }
}
