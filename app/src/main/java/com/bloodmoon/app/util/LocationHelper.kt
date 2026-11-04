package com.bloodmoon.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Uses the platform's native LocationManager instead of Google Play Services' FusedLocation -
 * this app targets older/lightweight devices (including Android TV boxes stuck on 9/11) that
 * frequently don't have Play Services at all. We only need a coarse, occasional fix for sunset/
 * sunrise math, not high-accuracy live tracking, so the plain LocationManager is the right tool.
 */
object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Best-effort last known location from any available provider. Returns null if permission
     * is missing, no provider has a fix yet, or location services are off - callers should fall
     * back to the manual/default schedule in that case, never crash.
     */
    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = try {
            locationManager.getProviders(true)
        } catch (_: Exception) {
            return null
        }

        var best: Location? = null
        for (provider in providers) {
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } ?: continue

            if (best == null || location.time > (best?.time ?: 0)) {
                best = location
            }
        }
        return best
    }
}
