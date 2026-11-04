package com.bloodmoon.app.util

import java.util.Calendar

data class SunsetSunriseTimes(
    val sunsetHour: Int,
    val sunsetMinute: Int,
    val sunriseHour: Int,
    val sunriseMinute: Int
)

object SunsetSunriseCalculator {

    /**
     * Approximate calculation for Sunset and Sunrise times based on coordinates.
     * Defaults to standard 18:00 (Sunset) and 06:00 (Sunrise) if coordinates are zero.
     */
    fun calculate(lat: Double, lng: Double, date: Calendar = Calendar.getInstance()): SunsetSunriseTimes {
        if (lat == 0.0 && lng == 0.0) {
            return SunsetSunriseTimes(
                sunsetHour = 18,
                sunsetMinute = 0,
                sunriseHour = 6,
                sunriseMinute = 0
            )
        }

        val dayOfYear = date.get(Calendar.DAY_OF_YEAR)
        // Solar declination approximation
        val declination = 23.45 * Math.sin(Math.toRadians((360.0 / 365.0) * (dayOfYear - 81)))
        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(declination)

        val cosHourAngle = -Math.tan(latRad) * Math.tan(decRad)
        val clampedCos = cosHourAngle.coerceIn(-1.0, 1.0)
        val hourAngle = Math.toDegrees(Math.acos(clampedCos))

        val solarNoon = 12.0 - (lng / 15.0)
        val sunriseTime = solarNoon - (hourAngle / 15.0)
        val sunsetTime = solarNoon + (hourAngle / 15.0)

        val sunriseH = sunriseTime.toInt().coerceIn(0, 23)
        val sunriseM = (((sunriseTime - sunriseH) * 60).toInt()).coerceIn(0, 59)

        val sunsetH = sunsetTime.toInt().coerceIn(0, 23)
        val sunsetM = (((sunsetTime - sunsetH) * 60).toInt()).coerceIn(0, 59)

        return SunsetSunriseTimes(
            sunsetHour = sunsetH,
            sunsetMinute = sunsetM,
            sunriseHour = sunriseH,
            sunriseMinute = sunriseM
        )
    }
}
