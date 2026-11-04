package com.bloodmoon.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.bloodmoon.app.R
import com.bloodmoon.app.data.model.FilterColor
import com.bloodmoon.app.data.repository.BloodMoonRepository
import com.bloodmoon.app.data.repository.FilterState
import com.bloodmoon.app.service.FilterOverlayService
import com.bloodmoon.app.ui.MainActivity
import com.bloodmoon.app.util.SystemColorAdjustment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Blood Moon widget: power button, 4 colors, intensity (-/+ and bar) and shortcuts for Double,
 * Gray, Extra dim and System Filter - the essentials of the Home tab without opening the app.
 *
 * Each button is an explicit PendingIntent to this provider (actions below), except power, which
 * goes straight to the filter service. Each button uses its own requestCode: PendingIntents that
 * differ only in extras would be treated as the same and one would overwrite the other.
 *
 * The widget adapts to its height: small shows only the header; medium, header + colors +
 * intensity; large, everything.
 */
class BloodMoonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        requestUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET_COLOR, ACTION_INTENSITY, ACTION_TONE, ACTION_OPTION -> handleAction(context, intent)
            else -> super.onReceive(context, intent)
        }
    }

    private fun handleAction(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = BloodMoonRepository(appContext)
                val config = repository.filterConfigFlow.first()
                when (intent.action) {
                    ACTION_SET_COLOR -> {
                        val color = runCatching {
                            FilterColor.valueOf(intent.getStringExtra(EXTRA_COLOR).orEmpty())
                        }.getOrNull() ?: return@launch
                        repository.updateFilterConfig(config.copy(color = color))
                        // Choosing a color with the filter off also turns it on.
                        if (repository.filterStateFlow.first() == FilterState.OFF) {
                            runCatching { FilterOverlayService.start(appContext) }
                        }
                    }
                    ACTION_INTENSITY -> {
                        val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                        val next = (config.colorIntensity + delta).coerceIn(0, 100)
                        repository.updateFilterConfig(config.copy(colorIntensity = next))
                    }
                    ACTION_TONE -> {
                        val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                        val next = (config.toneIntensity + delta).coerceIn(0, 100)
                        repository.updateFilterConfig(config.copy(toneIntensity = next))
                    }
                    ACTION_OPTION -> {
                        val settings = repository.appSettingsFlow.first()
                        val hasAccess = com.bloodmoon.app.util.AccessMode.isConfigured(appContext, settings)
                        when (intent.getStringExtra(EXTRA_OPTION)) {
                            OPTION_DOUBLE ->
                                repository.updateFilterConfig(config.copy(doubleFilterEnabled = !config.doubleFilterEnabled))
                            OPTION_GRAYSCALE ->
                                if (hasAccess) repository.updateAppSettings(settings.copy(grayscaleEnabled = !settings.grayscaleEnabled))
                                else openApp(appContext)
                            OPTION_DIM ->
                                if (hasAccess) repository.updateAppSettings(settings.copy(extraDimEnabled = !settings.extraDimEnabled))
                                else openApp(appContext)
                            OPTION_SYSTEM ->
                                // Only Root makes the System Filter work.
                                if (settings.useRoot) repository.updateAppSettings(settings.copy(systemFilterEnabled = !settings.systemFilterEnabled))
                                else openApp(appContext)
                        }
                    }
                }
                requestUpdate(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_SET_COLOR = "com.bloodmoon.app.widget.SET_COLOR"
        private const val ACTION_INTENSITY = "com.bloodmoon.app.widget.INTENSITY"
        private const val ACTION_TONE = "com.bloodmoon.app.widget.TONE"
        private const val ACTION_OPTION = "com.bloodmoon.app.widget.OPTION"
        private const val EXTRA_COLOR = "color"
        private const val EXTRA_DELTA = "delta"
        private const val EXTRA_OPTION = "option"
        private const val OPTION_DOUBLE = "double"
        private const val OPTION_GRAYSCALE = "grayscale"
        private const val OPTION_DIM = "dim"
        private const val OPTION_SYSTEM = "system"
        private const val INTENSITY_STEP = 5

        private fun openApp(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        private fun broadcast(context: Context, requestCode: Int, action: String, configure: Intent.() -> Unit): PendingIntent {
            val intent = Intent(context, BloodMoonWidgetProvider::class.java).apply {
                this.action = action
                configure()
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val repository = BloodMoonRepository(context)
            val state = repository.filterStateFlow.first()
            val config = repository.filterConfigFlow.first()
            val settings = repository.appSettingsFlow.first()

            val views = RemoteViews(context.packageName, R.layout.widget_blood_moon)

            // ---- header -------------------------------------------------------------
            val statusText = when (state) {
                FilterState.ACTIVE -> context.getString(R.string.tile_subtitle_active, config.colorIntensity)
                FilterState.PAUSED -> context.getString(R.string.tile_subtitle_paused)
                FilterState.OFF -> context.getString(R.string.tile_subtitle_off)
            }
            views.setTextViewText(R.id.widget_status_text, statusText)
            views.setInt(
                R.id.widget_status_text, "setBackgroundResource",
                when (state) {
                    FilterState.ACTIVE -> R.drawable.widget_pill_on
                    FilterState.PAUSED -> R.drawable.widget_pill_paused
                    FilterState.OFF -> R.drawable.widget_pill_off
                }
            )
            views.setInt(
                R.id.widget_logo, "setColorFilter",
                if (state == FilterState.ACTIVE) 0xFFFF4D63.toInt() else 0xFFB79399.toInt()
            )
            views.setInt(
                R.id.widget_toggle_button, "setBackgroundResource",
                when (state) {
                    FilterState.ACTIVE -> R.drawable.widget_toggle_button_active
                    FilterState.PAUSED -> R.drawable.widget_toggle_button_paused
                    FilterState.OFF -> R.drawable.widget_toggle_button_off
                }
            )

            val powerAction = when (state) {
                FilterState.ACTIVE -> FilterOverlayService.ACTION_STOP
                FilterState.PAUSED -> FilterOverlayService.ACTION_RESUME
                FilterState.OFF -> FilterOverlayService.ACTION_START
            }
            views.setOnClickPendingIntent(
                R.id.widget_toggle_button,
                PendingIntent.getForegroundService(
                    context, 1,
                    Intent(context, FilterOverlayService::class.java).apply { action = powerAction },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_open_area,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            // ---- cores -----------------------------------------------------------------
            val dots = listOf(
                Triple(R.id.widget_color_black, FilterColor.BLACK, 0xFF1E1E1E.toInt()),
                Triple(R.id.widget_color_red, FilterColor.RED, 0xFFFF2D2D.toInt()),
                Triple(R.id.widget_color_orange, FilterColor.ORANGE, 0xFFFF8A00.toInt()),
                Triple(R.id.widget_color_yellow, FilterColor.YELLOW, 0xFFFFE600.toInt())
            )
            dots.forEachIndexed { index, (viewId, color, tint) ->
                views.setInt(viewId, "setColorFilter", tint)
                views.setInt(
                    viewId, "setBackgroundResource",
                    if (config.color == color) R.drawable.widget_dot_ring_on else R.drawable.widget_dot_ring_off
                )
                views.setOnClickPendingIntent(
                    viewId,
                    broadcast(context, 10 + index, ACTION_SET_COLOR) { putExtra(EXTRA_COLOR, color.name) }
                )
            }

            // ---- intensidade e tonalidade ----------------------------------------------
            views.setProgressBar(R.id.widget_int_bar, 100, config.colorIntensity, false)
            views.setTextViewText(
                R.id.widget_int_label,
                context.getString(R.string.home_intensity) + "\n" + config.colorIntensity + "%"
            )
            views.setOnClickPendingIntent(
                R.id.widget_int_minus,
                broadcast(context, 20, ACTION_INTENSITY) { putExtra(EXTRA_DELTA, -INTENSITY_STEP) }
            )
            views.setOnClickPendingIntent(
                R.id.widget_int_plus,
                broadcast(context, 21, ACTION_INTENSITY) { putExtra(EXTRA_DELTA, INTENSITY_STEP) }
            )
            views.setProgressBar(R.id.widget_tone_bar, 100, config.toneIntensity, false)
            views.setTextViewText(
                R.id.widget_tone_label,
                context.getString(R.string.home_tone) + "\n" + config.toneIntensity + "%"
            )
            views.setOnClickPendingIntent(
                R.id.widget_tone_minus,
                broadcast(context, 22, ACTION_TONE) { putExtra(EXTRA_DELTA, -INTENSITY_STEP) }
            )
            views.setOnClickPendingIntent(
                R.id.widget_tone_plus,
                broadcast(context, 23, ACTION_TONE) { putExtra(EXTRA_DELTA, INTENSITY_STEP) }
            )

            // ---- atalhos ---------------------------------------------------------------
            fun pill(viewId: Int, on: Boolean, requestCode: Int, option: String) {
                views.setInt(
                    viewId, "setBackgroundResource",
                    if (on) R.drawable.widget_pill_on else R.drawable.widget_pill_off
                )
                views.setOnClickPendingIntent(
                    viewId,
                    broadcast(context, requestCode, ACTION_OPTION) { putExtra(EXTRA_OPTION, option) }
                )
            }
            pill(R.id.widget_opt_double, config.doubleFilterEnabled, 30, OPTION_DOUBLE)
            pill(R.id.widget_opt_grayscale, settings.grayscaleEnabled, 31, OPTION_GRAYSCALE)
            pill(R.id.widget_opt_dim, settings.extraDimEnabled, 32, OPTION_DIM)
            pill(R.id.widget_opt_system, settings.systemFilterEnabled, 33, OPTION_SYSTEM)

            // ---- height adaptation: shows only the rows that fit ----------------------
            val heightDp = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
            fun visible(show: Boolean) = if (show) View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.widget_row_colors, visible(heightDp >= 100))
            views.setViewVisibility(R.id.widget_row_int, visible(heightDp >= 100))
            views.setViewVisibility(R.id.widget_row_tone, visible(heightDp >= 150))
            views.setViewVisibility(R.id.widget_row_toggles, visible(heightDp >= 200))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun requestUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, BloodMoonWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.Default).launch {
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }
    }
}
