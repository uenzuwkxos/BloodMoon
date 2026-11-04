package com.bloodmoon.app.data.model

data class ScheduleConfig(
    val enabled: Boolean = false,
    val autoSunsetSunrise: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Sunday..7=Saturday
)
