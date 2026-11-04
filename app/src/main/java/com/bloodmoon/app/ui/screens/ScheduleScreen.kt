package com.bloodmoon.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bloodmoon.app.R
import com.bloodmoon.app.util.dpadFocus
import com.bloodmoon.app.ui.MainViewModel
import com.bloodmoon.app.ui.theme.dialogSurfaceColor
import com.bloodmoon.app.util.BloodMoonDialog
import com.bloodmoon.app.util.Stepper
import com.bloodmoon.app.util.SunsetSunriseCalculator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scheduleConfig by viewModel.scheduleConfig.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profileSchedules by viewModel.profileSchedules.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val scrollState = rememberScrollState()

    val sunsetSunriseTimes = SunsetSunriseCalculator.calculate(scheduleConfig.latitude, scheduleConfig.longitude)

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Re-checked whenever autoSunsetSunrise flips (e.g. the user comes back to this screen after
    // revoking location access from system Settings while the switch was already on) so the
    // toggle's displayed state never drifts from what's actually granted right now.
    LaunchedEffect(scheduleConfig.autoSunsetSunrise) {
        hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    // The Sunset and sunrise toggle only really turns on when the permission is confirmed as
    // granted - if the user denies it, the switch never ends up "on" (see onCheckedChange below),
    // so we no longer need any separate warning text explaining that.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            viewModel.updateScheduleConfig(scheduleConfig.copy(autoSunsetSunrise = true))
        }
    }

    var canScheduleExactAlarms by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else true
        )
    }
    LaunchedEffect(scheduleConfig.enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExactAlarms = (context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }
    }
    // Without this, the exact-alarm warning stayed visible even after the user granted the
    // permission in Android's Settings and came back: the LaunchedEffect above only re-evaluates
    // when scheduleConfig.enabled CHANGES value, so returning from Settings without touching that
    // toggle first never triggered the recheck (you had to leave and re-enter the tab for the whole
    // Composable to recompose from scratch). A recheck on ON_RESUME covers exactly that case: it is
    // the moment the user returns from the system permission screen. Same pattern already used for
    // the Usage permission in AppExclusionsScreen and for the Shizuku panel in SettingsScreen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canScheduleExactAlarms = (context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.schedule_enable),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.schedule_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = scheduleConfig.enabled,
                    onCheckedChange = { checked ->
                        viewModel.updateScheduleConfig(scheduleConfig.copy(enabled = checked))
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
                            if (!alarmManager.canScheduleExactAlarms()) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }

        // Everything below "Enable schedule" stays hidden until the user turns the switch above on
        // - and goes hidden again as soon as they turn it off, instead of continuing to show
        // controls that have no effect while scheduling is off.
        AnimatedVisibility(
            visible = scheduleConfig.enabled,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!canScheduleExactAlarms) {
                    PermissionWarningCard(
                        title = stringResource(R.string.schedule_exact_alarm_title),
                        body = stringResource(R.string.schedule_exact_alarm_body),
                        actionLabel = stringResource(R.string.schedule_exact_alarm_action),
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            )
                        }
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.schedule_sunset_sunrise),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.schedule_sunset_sunrise_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                // Only shows "on" when the location permission has actually been
                                // granted - requesting the permission does not turn the switch on
                                // by itself, only the real result of the grant (see the launcher
                                // above) does.
                                checked = scheduleConfig.autoSunsetSunrise && hasLocationPermission,
                                onCheckedChange = { checked ->
                                    if (!checked) {
                                        viewModel.updateScheduleConfig(scheduleConfig.copy(autoSunsetSunrise = false))
                                    } else if (hasLocationPermission) {
                                        viewModel.updateScheduleConfig(scheduleConfig.copy(autoSunsetSunrise = true))
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                }
                            )
                        }

                        if (scheduleConfig.autoSunsetSunrise && hasLocationPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val sunsetLabel = stringResource(R.string.schedule_sunset)
                            val sunriseLabel = stringResource(R.string.schedule_sunrise)
                            Text(
                                text = "$sunsetLabel ${"%02d:%02d".format(sunsetSunriseTimes.sunsetHour, sunsetSunriseTimes.sunsetMinute)} • $sunriseLabel ${"%02d:%02d".format(sunsetSunriseTimes.sunriseHour, sunsetSunriseTimes.sunriseMinute)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (!hasLocationPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // Unlike the toggle above (which fires the standard permission popup
                            // via locationPermissionLauncher), this button goes straight to the
                            // Settings > Apps > Blood Moon > Permissions screen - useful mainly
                            // when the user has already denied "don't ask again" and the popup
                            // simply no longer appears. android.provider.Settings is used with the
                            // qualified package (instead of another Settings import colliding) so
                            // no extra import is needed just for this.
                            TextButton(onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            }, modifier = Modifier.dpadFocus(RoundedCornerShape(50))) {
                                Text(stringResource(R.string.schedule_grant_location))
                            }
                        }
                    }
                }

                // Sunset and sunrise on hides all of this: the times come only from the location.
                // Off, the Scheduled profile (time + optional end + profile + days) takes over.
                AnimatedVisibility(
                    visible = !scheduleConfig.autoSunsetSunrise,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProfileSchedulesSection(
                            profiles = profiles,
                            entries = profileSchedules,
                            applyLatestOnBoot = appSettings.applyLatestProfileOnBoot,
                            onApplyLatestOnBootChange = {
                                viewModel.updateAppSettings(appSettings.copy(applyLatestProfileOnBoot = it))
                            },
                            onAdd = { viewModel.addProfileSchedule(it) },
                            onUpdate = { viewModel.updateProfileSchedule(it) },
                            onDelete = { viewModel.deleteProfileSchedule(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Horizontal pill (no longer a circle/bubble) to turn a day of the week on/off when tapped directly
 * - no panel, no checkbox. Each one takes an equal fraction of the row's width (Modifier.weight(1f)
 * coming from the caller), so the whole row forms a continuous 7-segment bar.
 */
@Composable
private fun DayTogglePill(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "dayPillColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "dayPillContentColor"
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp)
            )
            .dpadFocus(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun PermissionWarningCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onAction, modifier = Modifier.dpadFocus(RoundedCornerShape(50))) { Text(actionLabel) }
            }
        }
    }
}

/**
 * Two Steppers (hour 0-23, minute 0-59, both with wrapAround) side by side, in place of Material3's
 * TimeInput. TimeInput uses two typeable text fields with their own internal focus/numeric keyboard
 * management - problematic on TV: the user could not automatically advance from the first field to
 * the second after typing 2 digits, could not choose which field to edit via D-pad, and the field
 * got stuck after closing the numeric keyboard. Stepper already handles D-pad robustly (it is the
 * same component used in the Home/Options controls) and does not depend on a numeric keyboard at
 * any point - hour and minute are adjusted only with the -/+ buttons, working the same on touch,
 * mouse and remote control.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    // Minute Stepper step - 5 by default (faster to hit round times), switchable to 1 (fine
    // adjustment) via the AssistChip right below the minute Stepper.
    var minuteStep by remember { mutableIntStateOf(5) }
    BloodMoonDialog(
        title = stringResource(R.string.schedule_pick_time),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(hour, minute) }
    ) {
        // Hour above Minute; adjusted by Stepper (-/+ buttons or bar, depending on the option).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.schedule_hour_label),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Stepper(
                value = hour,
                valueRange = 0..23,
                onChange = { hour = it },
                valueLabel = { "%02d".format(it) }
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.schedule_minute_label),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Stepper(
                value = minute,
                valueRange = 0..59,
                step = minuteStep,
                onChange = { minute = it },
                valueLabel = { "%02d".format(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Minute step switchable between 5 (default) and 1 (fine adjustment).
            AssistChip(
                onClick = { minuteStep = if (minuteStep == 5) 1 else 5 },
                label = { Text(stringResource(R.string.schedule_minute_step_label, minuteStep)) }, modifier = Modifier.dpadFocus(RoundedCornerShape(8.dp))
            )
        }
    }
}

private enum class EditTarget { START, END }

private data class EditingTime(
    val entry: com.bloodmoon.app.data.model.ProfileScheduleEntry,
    val target: EditTarget
)

/**
 * Scheduled profile (now the only manual mode of "Schedules"): the user sets a start time, a saved
 * profile, an optional end time (starts at 22:00, ends at 07:00) and the days of the week, and
 * saves. Each saved schedule shows the time(s) - tapping one lets you change it -, the profile
 * name, the on/off switch, the trash button, and the days (also editable). Last, "Apply latest
 * schedule on start".
 */
@Composable
private fun ProfileSchedulesSection(
    profiles: List<com.bloodmoon.app.data.model.FilterProfile>,
    entries: List<com.bloodmoon.app.data.model.ProfileScheduleEntry>,
    applyLatestOnBoot: Boolean,
    onApplyLatestOnBootChange: (Boolean) -> Unit,
    onAdd: (com.bloodmoon.app.data.model.ProfileScheduleEntry) -> Unit,
    onUpdate: (com.bloodmoon.app.data.model.ProfileScheduleEntry) -> Unit,
    onDelete: (String) -> Unit
) {
    var newHour by remember { mutableIntStateOf(22) }
    var newMinute by remember { mutableIntStateOf(0) }
    var newUseEnd by remember { mutableStateOf(false) }
    var newEndHour by remember { mutableIntStateOf(7) }
    var newEndMinute by remember { mutableIntStateOf(0) }
    var newProfileId by remember { mutableStateOf<String?>(null) }
    var showNewTimePicker by remember { mutableStateOf(false) }
    var showNewEndPicker by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditingTime?>(null) }

    // A chosen profile that was deleted no longer counts.
    val selectedProfile = profiles.firstOrNull { it.id == newProfileId }
    val canAdd = selectedProfile != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.schedule_profiles_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.schedule_profiles_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_profiles_no_profiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Toggles first: end time and apply the latest schedule.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.schedule_profiles_use_end),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = newUseEnd, onCheckedChange = { newUseEnd = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.schedule_profiles_apply_on_boot),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.schedule_profiles_apply_on_boot_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = applyLatestOnBoot, onCheckedChange = onApplyLatestOnBootChange)
                }

                // Start time, (if enabled) end time to the right of it, and profile.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { showNewTimePicker = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp))
                    ) {
                        Text("%02d:%02d".format(newHour, newMinute), fontWeight = FontWeight.Bold)
                    }
                    if (newUseEnd) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showNewEndPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp))
                        ) {
                            Text("%02d:%02d".format(newEndHour, newEndMinute), fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { profileMenuExpanded = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocus(RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = selectedProfile?.name ?: stringResource(R.string.schedule_profiles_pick),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false }
                        ) {
                            profiles.forEach { profile ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        newProfileId = profile.id
                                        profileMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // No day selector here: the schedule is born with all days and the user adjusts the
                // days on the saved schedule (below).
                Button(
                    onClick = {
                        val profileId = selectedProfile?.id ?: return@Button
                        onAdd(
                            com.bloodmoon.app.data.model.ProfileScheduleEntry(
                                hour = newHour,
                                minute = newMinute,
                                profileId = profileId,
                                daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                                useEndTime = newUseEnd,
                                endHour = newEndHour,
                                endMinute = newEndMinute
                            )
                        )
                    },
                    enabled = canAdd,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocus(RoundedCornerShape(14.dp))
                ) {
                    Text(stringResource(R.string.schedule_profiles_add))
                }
            }
        }
    }

    if (entries.isEmpty()) {
        if (profiles.isNotEmpty()) {
            Text(
                text = stringResource(R.string.schedule_profiles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        entries.forEach { entry ->
            val profileName = profiles.firstOrNull { it.id == entry.profileId }?.name
                ?: stringResource(R.string.schedule_profiles_removed)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Tapping a time opens the picker to change it.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "%02d:%02d".format(entry.hour, entry.minute),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .dpadFocus(RoundedCornerShape(10.dp))
                                        .clickable { editing = EditingTime(entry, EditTarget.START) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                if (entry.useEndTime) {
                                    Text(
                                        text = "→",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "%02d:%02d".format(entry.endHour, entry.endMinute),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .dpadFocus(RoundedCornerShape(10.dp))
                                            .clickable { editing = EditingTime(entry, EditTarget.END) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = profileName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { onUpdate(entry.copy(enabled = it)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = { onDelete(entry.id) },
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                                    androidx.compose.ui.graphics.Color(0xFF3F1519)
                                } else {
                                    androidx.compose.ui.graphics.Color(0xFFFFE3E0)
                                },
                                contentColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                                    androidx.compose.ui.graphics.Color(0xFFFF8A80)
                                } else {
                                    androidx.compose.ui.graphics.Color(0xFFBA1A1A)
                                }
                            ),
                            modifier = Modifier.dpadFocus(androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    }
                    ScheduleDaysRow(
                        selectedDays = entry.daysOfWeek.toSet(),
                        compact = true,
                        onToggle = { day ->
                            val days = entry.daysOfWeek.toSet().let { if (day in it) it - day else it + day }
                            // With no days the schedule would never fire: keep at least one.
                            if (days.isNotEmpty()) onUpdate(entry.copy(daysOfWeek = days.sorted()))
                        }
                    )
                }
            }
        }
    }

    if (showNewTimePicker) {
        TimePickerDialog(
            initialHour = newHour,
            initialMinute = newMinute,
            onDismiss = { showNewTimePicker = false },
            onConfirm = { h, m ->
                newHour = h
                newMinute = m
                showNewTimePicker = false
            }
        )
    }

    if (showNewEndPicker) {
        TimePickerDialog(
            initialHour = newEndHour,
            initialMinute = newEndMinute,
            onDismiss = { showNewEndPicker = false },
            onConfirm = { h, m ->
                newEndHour = h
                newEndMinute = m
                showNewEndPicker = false
            }
        )
    }

    editing?.let { edit ->
        val entry = edit.entry
        val isStart = edit.target == EditTarget.START
        TimePickerDialog(
            initialHour = if (isStart) entry.hour else entry.endHour,
            initialMinute = if (isStart) entry.minute else entry.endMinute,
            onDismiss = { editing = null },
            onConfirm = { h, m ->
                onUpdate(
                    if (isStart) entry.copy(hour = h, minute = m)
                    else entry.copy(endHour = h, endMinute = m)
                )
                editing = null
            }
        )
    }
}

/** Monday -> Sunday row of day pills; [compact] = smaller version for the saved schedules. */
@Composable
private fun ScheduleDaysRow(
    selectedDays: Set<Int>,
    compact: Boolean,
    onToggle: (Int) -> Unit
) {
    val dayNames = stringArrayResource(R.array.day_abbreviations)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        // day_abbreviations is in Calendar order (Sunday = index 0, id 1).
        listOf(1, 2, 3, 4, 5, 6, 0).forEach { index ->
            val dayId = index + 1
            DayTogglePill(
                modifier = Modifier.weight(1f),
                label = dayNames[index],
                selected = dayId in selectedDays,
                onClick = { onToggle(dayId) }
            )
        }
    }
}
