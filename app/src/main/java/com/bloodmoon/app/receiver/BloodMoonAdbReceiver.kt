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

/**
 * Lets `adb shell am broadcast -a com.bloodmoon.app.ACTION_TOGGLE` (etc.) control the filter -
 * useful for Tasker/automation. Always active; ADB access to a broadcast receiver a user has to
 * already know the exact action name for isn't a meaningful attack surface, so there's no
 * separate on/off setting for it (see SettingsScreen's "ADB" card for what ADB is actually used
 * for in this app: granting WRITE_SECURE_SETTINGS, same as Root/Shizuku).
 */
class BloodMoonAdbReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val repository = BloodMoonRepository(context)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_TOGGLE -> {
                        val currentState = repository.filterStateFlow.first()
                        if (currentState == FilterState.ACTIVE) {
                            FilterOverlayService.stop(context)
                        } else {
                            FilterOverlayService.start(context)
                        }
                    }
                    ACTION_START -> FilterOverlayService.start(context)
                    ACTION_STOP -> FilterOverlayService.stop(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.bloodmoon.app.ACTION_TOGGLE"
        const val ACTION_START = "com.bloodmoon.app.ACTION_START"
        const val ACTION_STOP = "com.bloodmoon.app.ACTION_STOP"
    }
}
