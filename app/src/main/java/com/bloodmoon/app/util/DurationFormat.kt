package com.bloodmoon.app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bloodmoon.app.R

/**
 * Minutes in a readable format: 45 -> "45 min", 60 -> "1h", 90 -> "1h 30min", 175 -> "2h 55min".
 * Every 60 minutes adds an hour (it used to show only minutes: "150 min").
 */
@Composable
fun formatDurationMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}
