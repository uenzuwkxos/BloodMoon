package com.bloodmoon.app.util

import android.content.Context
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.data.repository.FilterState
import kotlinx.coroutines.flow.first

/**
 * Grayscale state shared between the filter service (which decides when it should be on) and the
 * accessibility service (which reasserts it on every window change).
 */
object GrayscaleController {

    /** true while grayscale should be on (filter active + option checked). */
    @Volatile
    var wanted: Boolean = false

    /** Religa a escala de cinza se ela deve estar ligada e o sistema a desligou. */
    fun reassertIfWanted(context: Context): Boolean {
        if (!wanted) return false
        if (!SystemColorAdjustment.hasPermission(context)) return false
        if (SystemColorAdjustment.isGrayscaleEnabled(context)) return false
        return SystemColorAdjustment.setGrayscaleEnabled(context, true)
    }

    /**
     * Rebuilds [wanted] from what is saved - the accessibility service can come up before the
     * filter service after the process was recreated by the system.
     */
    suspend fun refreshFromStorage(context: Context) {
        val repository = BloodMoonRepository(context)
        val state = repository.filterStateFlow.first()
        val settings = repository.appSettingsFlow.first()
        if (state == FilterState.ACTIVE && settings.grayscaleEnabled && !settings.systemFilterEnabled) {
            wanted = true
        }
    }
}
