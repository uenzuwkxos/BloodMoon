package com.bloodmoon.app.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bloodmoon.app.R
import com.bloodmoon.app.data.model.FilterConfig
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.data.repository.FilterState
import com.bloodmoon.app.ui.MainActivity
import com.bloodmoon.app.util.AccessibilityServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Blood Moon quick settings tile.
 *
 * - Own icon (the brand's crescent moon) instead of the colored app icon, which the system tinted
 * oddly; - subtitle with the state and intensity ("Active · 60%", "Paused", "Off"); - live updates
 * while the panel is open (the filter can change via the notification, widget or app) - before, it
 * only read the state once; - a tap respects the three states (active -> turns off, paused ->
 * resumes, off -> turns on) and updates the tile immediately, without waiting for the service;
 * without permission to draw the overlay, it opens the app instead of failing silently; - a long
 * press opens the app (QS_TILE_PREFERENCES, see the manifest).
 */
class BloodMoonTileService : TileService() {

    private var listenScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        listenScope = scope
        val repository = BloodMoonRepository(applicationContext)
        scope.launch {
            combine(repository.filterStateFlow, repository.filterConfigFlow) { state, config ->
                state to config
            }.collect { (state, config) -> render(state, config) }
        }
    }

    override fun onStopListening() {
        listenScope?.cancel()
        listenScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            val repository = BloodMoonRepository(applicationContext)
            val state = repository.filterStateFlow.first()
            val config = repository.filterConfigFlow.first()

            val canShow = FilterOverlayService.canDrawOverlay(applicationContext) ||
                AccessibilityServiceHelper.isEnabled(applicationContext)
            if (state == FilterState.OFF && !canShow) {
                // No way to draw the overlay: takes the user to the app (where the permission is requested).
                openApp()
                scope.cancel()
                return@launch
            }

            when (state) {
                FilterState.ACTIVE -> {
                    render(FilterState.OFF, config)
                    FilterOverlayService.stop(applicationContext)
                }
                FilterState.PAUSED -> {
                    render(FilterState.ACTIVE, config)
                    FilterOverlayService.resume(applicationContext)
                }
                FilterState.OFF -> {
                    render(FilterState.ACTIVE, config)
                    FilterOverlayService.start(applicationContext)
                }
            }
            scope.cancel()
        }
    }

    private fun render(state: FilterState, config: FilterConfig) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_moon)
        tile.label = getString(R.string.app_name)
        tile.state = if (state == FilterState.ACTIVE) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                FilterState.ACTIVE -> getString(R.string.tile_subtitle_active, config.colorIntensity)
                FilterState.PAUSED -> getString(R.string.tile_subtitle_paused)
                FilterState.OFF -> getString(R.string.tile_subtitle_off)
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
