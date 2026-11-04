package com.bloodmoon.app.ui.screens

import android.os.Build
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bloodmoon.app.R
import com.bloodmoon.app.util.dpadFocus
import com.bloodmoon.app.util.formatDurationMinutes
import com.bloodmoon.app.data.model.AppSettings
import com.bloodmoon.app.data.model.FilterColor
import com.bloodmoon.app.data.model.FilterConfig
import com.bloodmoon.app.data.repository.FilterState
import com.bloodmoon.app.root.RootHelper
import com.bloodmoon.app.shizuku.ShizukuHelper
import com.bloodmoon.app.ui.MainViewModel
import com.bloodmoon.app.util.FilterMath
import com.bloodmoon.app.util.AccessMode
import com.bloodmoon.app.util.BloodMoonDialog
import com.bloodmoon.app.util.GrantResult
import com.bloodmoon.app.util.LabeledStepper
import com.bloodmoon.app.util.Stepper
import com.bloodmoon.app.util.SystemColorAdjustment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // displayFilterState (not filterState) so the button/preview reflect a tap immediately,
    // instead of waiting for the DataStore-backed real state to catch up - see the comment on
    // MainViewModel.toggleFilter() for why that lag was letting rapid taps crash the app.
    val filterState by viewModel.displayFilterState.collectAsState()
    val filterConfig by viewModel.filterConfig.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()

    // Draft mirrors the committed config, but sliders write here first (instant, in-memory)
    // and only push to the ViewModel (and therefore to disk + the real overlay) once the user
    // lets go. This is what keeps dragging a slider from writing to DataStore and tearing down
    // the overlay window dozens of times per second.
    var draft by remember { mutableStateOf(filterConfig) }
    LaunchedEffect(filterConfig) { draft = filterConfig }

    fun commit(updated: FilterConfig) {
        draft = updated
        viewModel.updateFilterConfig(updated)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preview and power button side by side, on the same row and with the same height - the
        // preview uses exactly the same math as the real overlay (FilterMath), so it never shows
        // anything different from what the real filter will apply.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(112.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val overlayColor = Color(FilterMath.computeOverlayColor(draft))
                    // Smooth animation of the preview's own background color, so it behaves as a
                    // faithful mirror of the real overlay's fade (same feeling of gradualness),
                    // instead of jumping instantly on every slider adjustment. State WITHOUT a
                    // delegate: the value is only read inside drawBehind (draw phase), so the
                    // animation recomposes nothing - it only redraws.
                    val animatedOverlayColor = animateColorAsState(
                        targetValue = overlayColor,
                        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                        label = "previewOverlayColor"
                    )
                    // White background (the "screen") + the overlay color on top, same as the real service.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .drawBehind { drawRect(animatedOverlayColor.value) }
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Only the graphic component of the on/off button - no state title beside it, which
                // was redundant with the button's own color/icon.
                PowerToggleButton(
                    filterState = filterState,
                    onToggle = { viewModel.toggleFilter() }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_quick_adjustments),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color selector with 4 options (it was a binary Red/Yellow Switch - it does not
                // scale to more colors). Each button shows its own background color for quick
                // identification, with the selected option highlighted by a border.
                Text(
                    text = stringResource(R.string.home_filter_color),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val colorOptions = listOf(
                        FilterColor.BLACK to Color(0xFF000000),
                        FilterColor.RED to Color(0xFFFF0000),
                        FilterColor.ORANGE to Color(0xFFFF8000),
                        FilterColor.YELLOW to Color(0xFFFFFF00)
                    )
                    colorOptions.forEach { (colorOption, swatch) ->
                        val isSelected = draft.color == colorOption
                        Box(
                            modifier = Modifier
                                // Without a cap, weight(1f) + aspectRatio(1f) let each square grow
                                // up to 1/4 of the available WIDTH without limit - on
                                // horizontal/wide screens (tablet, phone on its side) this turned
                                // into huge buttons. A fixed size with a cap (64dp, or 1/4 of the
                                // width minus the spacings, whichever is smaller) solves this,
                                // keeping the buttons small on wide screens and still fitting side
                                // by side on narrow screens.
                                .weight(1f, fill = false)
                                .widthIn(max = 64.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(swatch)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .dpadFocus(RoundedCornerShape(12.dp))
                                .clickable { commit(draft.copy(color = colorOption)) }
                        )
                    }
                }

                FilterSliderRow(
                    label = stringResource(R.string.home_intensity),
                    value = draft.colorIntensity,
                    onChange = { commit(draft.copy(colorIntensity = it)) }
                )

                // Tone: 100 = the most vibrant/strong shade of the chosen color, 0 = pure black -
                // independent of Dimming (which darkens the whole screen), this changes the tone's
                // own color before any compositing.
                FilterSliderRow(
                    label = stringResource(R.string.home_tone),
                    value = draft.toneIntensity,
                    onChange = { commit(draft.copy(toneIntensity = it)) }
                )

                // Double filter: it existed in the data model but never had a control in the UI nor
                // was actually read in the final color calculation - the toggle never did anything.
                // Fixed along with the math in FilterMath.computeOverlayColor.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_double_filter_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.home_double_filter_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = draft.doubleFilterEnabled,
                        onCheckedChange = { commit(draft.copy(doubleFilterEnabled = it)) }
                    )
                }
            }
        }

        // Extra Dim, Grayscale and System Filter: moved here, no longer duplicated in Options. All
        // depend on ADB/Shizuku/Root, so they are visually dimmed (see PrivilegedToggleRow) until
        // the dependency is configured; tapping before that takes you to Options with an
        // explanation, without actually turning the adjustment on. When already configured, we try
        // to grant WRITE_SECURE_SETTINGS right away (instead of just setting the flag and letting
        // the service try later in silence) to give immediate feedback if it fails. Grayscale and
        // System Filter are mutually exclusive: both use system color-adjustment mechanisms that
        // would collide visually if on at the same time, so enabling one forces the other off.
        // System Filter only with Root/Shizuku (the key it writes,
        // accessibility_display_color_matrix, is a long string that the ADB shell cannot carry
        // reliably). There is no longer a separate "Redscale" here: it was a fixed pure-red matrix
        // with no slider, redundant with the System Filter (which, with the app's red color at
        // #FF0000, tone and intensity at 100% and dimming at 0%, produces exactly the same matrix -
        // just with real sliders instead of a single fixed state). Extra Dim ("Reduce Bright
        // Colors") only exists in the framework from Android 12 (API 31) - confirmed, it is not an
        // app bug on earlier versions. Instead of letting the user tap and nothing silently
        // happening (or worse, thinking it is the same kind of "Root/Shizuku not configured" as the
        // other toggles), the card is dimmed like the others but with a specific message saying the
        // Android version does not support it.
        val extraDimSupportedByAndroidVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val accessConfigured = AccessMode.isConfigured(context, appSettings)
        val extraDimConfigured = extraDimSupportedByAndroidVersion && accessConfigured
        val grayscaleConfigured = accessConfigured
        // Root only: confirmed that the SurfaceFlinger command the System Filter needs really
        // requires root, and Shizuku without root does not meet that (see the full comment in
        // FilterOverlayService.systemFilterActiveAndReady). Besides the technical problem, it also
        // makes no sense in use: whoever already enables Shizuku with root has root available
        // directly, without needing Shizuku as an intermediary for this specifically.
        val systemFilterConfigured = appSettings.useRoot
        // Notice (dismissible) when the accessibility service is not active: without it the filter
        // can be killed by the system/other apps and does not cover the whole system (e.g.
        // Settings), even with ADB or Shizuku. The accessibility screen was already opened at
        // startup; this card is for whoever came back without enabling it.
        val a11yEnabled = rememberAccessibilityEnabled()
        if (!a11yEnabled && appSettings.useAccessibilityOverlay && !appSettings.a11yWarningDismissed) {
            A11yWarningCard(
                onOpenSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onDismiss = { viewModel.updateAppSettings(appSettings.copy(a11yWarningDismissed = true)) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        PrivilegedTogglesCard(
            appSettings = appSettings,
            extraDimConfigured = extraDimConfigured,
            extraDimSupportedByAndroidVersion = extraDimSupportedByAndroidVersion,
            grayscaleConfigured = grayscaleConfigured,
            systemFilterConfigured = systemFilterConfigured,
            tvConfigured = accessConfigured,
            onUpdateSettings = { viewModel.updateAppSettings(it) },
            onNavigateToSettings = onNavigateToSettings,
            onExtraDimToggle = { checked ->
                if (checked && !extraDimSupportedByAndroidVersion) {
                    Toast.makeText(context, context.getString(R.string.home_extra_dim_unsupported_version), Toast.LENGTH_SHORT).show()
                } else if (checked && !extraDimConfigured) {
                    Toast.makeText(context, context.getString(R.string.home_privileged_toggle_needs_setup), Toast.LENGTH_SHORT).show()
                    onNavigateToSettings()
                } else if (!checked) {
                    viewModel.updateAppSettings(appSettings.copy(extraDimEnabled = false))
                } else {
                    scope.launch {
                        val granted = ensurePrivilegedPermission(context, appSettings)
                        if (granted) {
                            viewModel.updateAppSettings(appSettings.copy(extraDimEnabled = true))
                        } else {
                            Toast.makeText(context, context.getString(R.string.home_privileged_toggle_needs_setup), Toast.LENGTH_SHORT).show()
                            onNavigateToSettings()
                        }
                    }
                }
            },
            onGrayscaleToggle = { checked ->
                if (checked && !grayscaleConfigured) {
                    Toast.makeText(context, context.getString(R.string.home_privileged_toggle_needs_setup), Toast.LENGTH_SHORT).show()
                    onNavigateToSettings()
                } else if (!checked) {
                    viewModel.updateAppSettings(appSettings.copy(grayscaleEnabled = false))
                } else {
                    scope.launch {
                        val granted = ensurePrivilegedPermission(context, appSettings)
                        if (granted) {
                            // Turning Grayscale on always turns Filtro via Sistema off - only
                            // one of the two system color adjustments can be active at a time.
                            viewModel.updateAppSettings(
                                appSettings.copy(grayscaleEnabled = true, systemFilterEnabled = false)
                            )
                        } else {
                            Toast.makeText(context, context.getString(R.string.home_privileged_toggle_needs_setup), Toast.LENGTH_SHORT).show()
                            onNavigateToSettings()
                        }
                    }
                }
            },
            onSystemFilterToggle = { checked ->
                if (checked && !systemFilterConfigured) {
                    Toast.makeText(context, context.getString(R.string.home_system_filter_needs_root), Toast.LENGTH_SHORT).show()
                    onNavigateToSettings()
                } else if (!checked) {
                    viewModel.updateAppSettings(appSettings.copy(systemFilterEnabled = false))
                } else {
                    scope.launch {
                        val granted = ensurePrivilegedPermission(context, appSettings)
                        if (granted) {
                            // Turning Filtro via Sistema on always turns Grayscale off - see the
                            // note above the Grayscale toggle for why these can't both be on.
                            viewModel.updateAppSettings(
                                appSettings.copy(systemFilterEnabled = true, grayscaleEnabled = false)
                            )
                        } else {
                            Toast.makeText(context, context.getString(R.string.home_system_filter_needs_root), Toast.LENGTH_SHORT).show()
                            onNavigateToSettings()
                        }
                    }
                }
            }
        )
    }
}

/**
 * Tries to obtain WRITE_SECURE_SETTINGS right now via whichever privileged method is already
 * configured (Root or Shizuku), or confirms it's already granted (covers the ADB case). Shared
 * by Grayscale, Extra Dim, and Filtro via Sistema toggles so a switch never visually turns on
 * without the permission actually being usable.
 */
private suspend fun ensurePrivilegedPermission(context: android.content.Context, settings: AppSettings): Boolean {
    if (SystemColorAdjustment.hasPermission(context)) return true
    val result = when {
        settings.useRoot -> withContext(Dispatchers.IO) { RootHelper.grantWriteSecureSettingsPermission(context) }
        settings.useShizuku -> ShizukuHelper.grantWriteSecureSettingsPermission(context)
        else -> return false
    }
    return result is GrantResult.Granted
}

/**
 * Extra Dim, Grayscale and System Filter all live here now (no longer duplicated in Options) - they
 * depend on ADB/Shizuku/Root, so each row is visually dimmed (reduced opacity) until the dependency
 * is configured in Options, instead of looking "ready to use" and only silently blocking on tap.
 */
@Composable
private fun PrivilegedTogglesCard(
    appSettings: AppSettings,
    extraDimConfigured: Boolean,
    extraDimSupportedByAndroidVersion: Boolean,
    grayscaleConfigured: Boolean,
    systemFilterConfigured: Boolean,
    tvConfigured: Boolean,
    onUpdateSettings: (AppSettings) -> Unit,
    onNavigateToSettings: () -> Unit,
    onExtraDimToggle: (Boolean) -> Unit,
    onGrayscaleToggle: (Boolean) -> Unit,
    onSystemFilterToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_advanced_features),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            PrivilegedToggleRow(
                title = stringResource(R.string.settings_extra_dim),
                description = stringResource(R.string.home_extra_dim_desc),
                checked = appSettings.extraDimEnabled,
                configured = extraDimConfigured,
                onCheckedChange = onExtraDimToggle,
                unavailableDescription = if (!extraDimSupportedByAndroidVersion) {
                    stringResource(R.string.home_extra_dim_unsupported_version)
                } else {
                    null
                }
            )
            PrivilegedToggleRow(
                title = stringResource(R.string.home_grayscale_title),
                description = stringResource(R.string.home_grayscale_desc),
                checked = appSettings.grayscaleEnabled,
                configured = grayscaleConfigured,
                onCheckedChange = onGrayscaleToggle
            )
            PrivilegedToggleRow(
                title = stringResource(R.string.home_system_filter_title),
                description = stringResource(R.string.home_system_filter_desc),
                checked = appSettings.systemFilterEnabled,
                configured = systemFilterConfigured,
                onCheckedChange = onSystemFilterToggle,
                unavailableDescription = stringResource(R.string.home_system_filter_desc)
            )
            // TV features show up on any device (TV-only by origin, but whoever wants to use them
            // on a phone can).
            TvFeaturesSection(
                appSettings = appSettings,
                configured = tvConfigured,
                onUpdate = onUpdateSettings,
                onNavigateToSettings = onNavigateToSettings
            )
        }
    }
}

/**
 * TV features (before in Options, now inside Advanced features): Timed power-off and Automatic
 * standby. They depend on Root/Shizuku/ADB; without that the row is dimmed and the tap only
 * explains what is missing, like the other advanced features.
 */
@Composable
private fun TvFeaturesSection(
    appSettings: AppSettings,
    configured: Boolean,
    onUpdate: (AppSettings) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    var showTimeDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text = stringResource(R.string.settings_tv_power_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    // Same as the other advanced features: says what is missing and takes you to Options.
    fun needsSetup() {
        Toast.makeText(context, context.getString(R.string.home_privileged_toggle_needs_setup), Toast.LENGTH_SHORT).show()
        onNavigateToSettings()
    }

    PrivilegedToggleRow(
        title = stringResource(R.string.settings_tv_auto_off_title),
        description = stringResource(R.string.settings_tv_auto_off_desc),
        checked = appSettings.tvAutoOffEnabled,
        configured = configured,
        onCheckedChange = { checked ->
            when {
                !configured -> needsSetup()
                checked -> showTimeDialog = true
                else -> onUpdate(appSettings.copy(tvAutoOffEnabled = false))
            }
        }
    )
    if (appSettings.tvAutoOffEnabled && configured) {
        androidx.compose.material3.FilledTonalButton(
            onClick = { showTimeDialog = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
        ) {
            Text(
                stringResource(
                    R.string.settings_tv_auto_off_change_time_with,
                    "%02d:%02d".format(appSettings.tvAutoOffHour, appSettings.tvAutoOffMinute)
                )
            )
        }
    }

    PrivilegedToggleRow(
        title = stringResource(R.string.settings_tv_standby_title),
        description = stringResource(R.string.settings_tv_standby_desc),
        checked = appSettings.tvStandbyEnabled,
        configured = configured,
        onCheckedChange = { checked ->
            if (!configured) needsSetup() else onUpdate(appSettings.copy(tvStandbyEnabled = checked))
        }
    )
    if (appSettings.tvStandbyEnabled && configured) {
        Stepper(
            value = appSettings.tvStandbyMinutes,
            valueRange = 5..180,
            step = 5,
            onChange = { onUpdate(appSettings.copy(tvStandbyMinutes = it)) },
            valueLabel = { formatDurationMinutes(it) }
        )
    }

    if (showTimeDialog) {
        TvAutoOffTimeDialog(
            initialHour = appSettings.tvAutoOffHour,
            initialMinute = appSettings.tvAutoOffMinute,
            onDismiss = { showTimeDialog = false },
            onConfirm = { hour, minute ->
                onUpdate(
                    appSettings.copy(tvAutoOffEnabled = true, tvAutoOffHour = hour, tvAutoOffMinute = minute)
                )
                showTimeDialog = false
            }
        )
    }
}

@Composable
private fun TvAutoOffTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    BloodMoonDialog(
        title = stringResource(R.string.settings_tv_auto_off_title),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(hour, minute) }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.schedule_hour_label), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Stepper(
                value = hour,
                valueRange = 0..23,
                onChange = { hour = it },
                valueLabel = { "%02d".format(it) }
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.schedule_minute_label), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Stepper(
                value = minute,
                valueRange = 0..59,
                step = 5,
                onChange = { minute = it },
                valueLabel = { "%02d".format(it) }
            )
        }
    }
}

@Composable
private fun PrivilegedToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    configured: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    unavailableDescription: String? = null
) {
    // Dimmed (not fully disabled - the tap still works, it just takes you to Options with an
    // explanation) until the dependency (ADB/Shizuku/Root) is ready. The switch itself always uses
    // "enabled = true" - Modifier.alpha's opacity is what gives the visual "not available yet"
    // feedback, keeping the tap functional to guide the user to Options.
    val rowAlpha = if (configured) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .dpadFocus(RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = if (configured) {
                    description
                } else {
                    unavailableDescription ?: stringResource(R.string.home_privileged_toggle_needs_setup)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Round on/off button, with a bounce on touch and a soft pulse while active.
 *
 * Smoothness: every continuous animation (color, touch scale, pulse) is read ONLY in the
 * layout/draw phases (graphicsLayer { } and drawBehind { }), never in composition. Before, the
 * animated color lived in the scope of the whole screen and the scale was read in composition, so
 * each 350ms frame recomposed the whole screen (cards, sliders, preview) - that was the "little
 * stutter" when turning on. Now recomposition happens only once per state change.
 */
@Composable
private fun PowerToggleButton(
    filterState: FilterState,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val targetColor = when (filterState) {
        FilterState.ACTIVE -> colors.primary
        FilterState.PAUSED -> Color(0xFFFF9800)
        FilterState.OFF -> colors.surfaceContainerHighest
    }
    // No "by": the State is passed along and only read inside the draw lambdas.
    val buttonColor = animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "powerButtonColor"
    )
    val iconColor = animateColorAsState(
        targetValue = if (targetColor.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "powerButtonIconColor"
    )

    var pressed by remember { mutableStateOf(false) }
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "powerButtonPressScale"
    )

    // Continuous "breathing" while active; smoothly returns to 1 when deactivated.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(filterState) {
        if (filterState == FilterState.ACTIVE) {
            while (true) {
                pulse.animateTo(1.04f, tween(1200, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
            }
        } else {
            pulse.animateTo(1f, tween(250, easing = LinearOutSlowInEasing))
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val scale = pressScale.value * pulse.value
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .drawBehind { drawCircle(buttonColor.value) }
            .dpadFocus(CircleShape)
            .clickable {
                pressed = true
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (filterState == FilterState.PAUSED) Icons.Default.Pause else Icons.Default.PowerSettingsNew,
            contentDescription = stringResource(R.string.home_toggle_filter),
            tint = iconColor.value,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun FilterSliderRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    LabeledStepper(
        label = label,
        value = value,
        valueRange = 0..100,
        step = 5,
        onChange = onChange,
        valueLabel = { "$it%" }
    )
}

@Composable
private fun rememberAccessibilityEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(com.bloodmoon.app.util.AccessibilityServiceHelper.isEnabled(context)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                enabled = com.bloodmoon.app.util.AccessibilityServiceHelper.isEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

@Composable
private fun A11yWarningCard(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_stable_mode_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.home_stable_mode_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            androidx.compose.material3.Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp))
            ) {
                Text(stringResource(R.string.home_stable_mode_action))
            }
            androidx.compose.material3.FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp))
            ) {
                Text(stringResource(R.string.home_stable_mode_dismiss))
            }
        }
    }
}
