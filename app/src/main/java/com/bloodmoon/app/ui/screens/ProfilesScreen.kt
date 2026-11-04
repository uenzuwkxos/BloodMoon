package com.bloodmoon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bloodmoon.app.R
import com.bloodmoon.app.util.dpadFocus
import com.bloodmoon.app.util.formatDurationMinutes
import com.bloodmoon.app.data.model.FilterColor
import com.bloodmoon.app.data.model.FilterProfile
import com.bloodmoon.app.data.model.matchesCurrent
import com.bloodmoon.app.ui.MainViewModel
import com.bloodmoon.app.ui.theme.dialogSurfaceColor
import com.bloodmoon.app.util.BloodMoonDialog
import com.bloodmoon.app.util.FilterMath
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ProfilesScreen(
    viewModel: MainViewModel
) {
    val profiles by viewModel.profiles.collectAsState()
    val currentConfig by viewModel.filterConfig.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val currentAppSettings by viewModel.appSettings.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (profiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.profiles_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    // animateItem() - used to be animateItemPlacement(tween(300)), removed from
                    // Compose Foundation (official source:
                    // developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyItemScope).
                    // The old comment here said animateItem() (which also animates fade in/out, not
                    // just repositioning) would only be available in a much later Compose version
                    // than the one the project used then - with the updated BOM, that version is
                    // already the current one, so we get the removed card's fade out for free in
                    // addition to the slide-up of the remaining ones.
                    ProfileCard(
                        modifier = Modifier.animateItem(placementSpec = tween<androidx.compose.ui.unit.IntOffset>(300)),
                        profile = profile,
                        isActive = profile.id == activeProfileId && profile.matchesCurrent(currentConfig, currentAppSettings),
                        onToggle = { viewModel.toggleProfile(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // The + button is now at the bottom, centered - no longer at the top of the screen.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.profiles_create))
            }
        }
    }

    if (showCreateDialog) {

        BloodMoonDialog(
            title = stringResource(R.string.profiles_save_current),
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                if (newProfileName.isNotBlank()) {
                    val newProfile = FilterProfile(
                        name = newProfileName,
                        filterConfig = currentConfig,
                        extraDimWasEnabled = currentAppSettings.extraDimEnabled,
                        grayscaleWasEnabled = currentAppSettings.grayscaleEnabled,
                        systemFilterWasEnabled = currentAppSettings.systemFilterEnabled,
                        tvAutoOffEnabled = currentAppSettings.tvAutoOffEnabled,
                        tvAutoOffHour = currentAppSettings.tvAutoOffHour,
                        tvAutoOffMinute = currentAppSettings.tvAutoOffMinute,
                        tvStandbyEnabled = currentAppSettings.tvStandbyEnabled,
                        tvStandbyMinutes = currentAppSettings.tvStandbyMinutes
                    )
                    viewModel.saveProfile(newProfile)
                    newProfileName = ""
                    showCreateDialog = false
                }
            }
        ) {
            // The text field "holds on to" D-pad down; we intercept it and move focus to the next
            // element in the panel (the Confirm button).
            val dialogFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Text(
                text = stringResource(R.string.profiles_enter_name),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                label = { Text(stringResource(R.string.profiles_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            dialogFocusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                            true
                        } else {
                            false
                        }
                    }
            )
        }
    }
}

@Composable
fun ProfileCard(
    modifier: Modifier = Modifier,
    profile: FilterProfile,
    isActive: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    // Real shrink-and-fade-out before the item actually leaves the list, driven manually via
    // isDeleting + animateFloatAsState. Note: with the BOM now updated, Modifier.animateItem()
    // (used above, on the LazyColumn item that wraps this composable) already animates fade-out
    // on its own - this manual isDeleting flag might be partially redundant with that now, but
    // was kept as-is since removing it wasn't verified against a real build here.
    var isDeleting by remember { mutableStateOf(false) }
    val deleteProgress by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(220, easing = LinearOutSlowInEasing),
        label = "profileDeleteProgress"
    )
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(220)
            onDelete()
        }
    }

    // The dot shows the real tone of the saved profile - the same math the Home tab preview uses
    // (FilterMath.computeOverlayColor), not just "red or yellow": if the profile has a low tone or
    // high dimming, the dot shows the corresponding darker color (possibly reaching black), exactly
    // how the screen would look with this profile active. The overlay color has its own alpha
    // channel (real overlay transparency), so we composite over white to get a representative solid
    // color - a semi-transparent dot would look "washed out" against the Card background, which is
    // not what we want to show here.
    val overlayColor = Color(FilterMath.computeOverlayColor(profile.filterConfig))
    val swatchColor = overlayColor.compositeOver(Color.White)

    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "profileCardContainer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(0.85f + 0.15f * deleteProgress)
            .alpha(deleteProgress),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            // Fixed color (not inherited from the container) - without this,
                            // Material3 automatically recomputed a readable text color from
                            // containerColor (which changes between surface/primaryContainer
                            // depending on whether the profile is active), making the name change
                            // color along with it - a visual bug reported on the Dark and Black
                            // themes, where that recomputed color looked particularly bad.
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(
                            visible = isActive,
                            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.6f),
                            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.6f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.profiles_active_badge),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Profile summary: only Color, Intensity and Tone - there is no longer
                    // "Dimming" either as a slider in the UI or as a field in the data model
                    // (dimmingIntensity was removed from FilterConfig entirely, not just renamed -
                    // the user confirmed that feature was absorbed by Tone+Intensity a while ago,
                    // and the field was still a ghost in the summary even with no slider editing
                    // it). Double Filter, Extra Dim, Grayscale and System Filter show up as badges
                    // on the bottom row, ONLY when they were enabled at the moment the profile was
                    // saved (extraDimWasEnabled/grayscaleWasEnabled/systemFilterWasEnabled - see
                    // the note in FilterProfile.kt about these being just an informational
                    // snapshot).
                    val colorLabel = when (profile.filterConfig.color) {
                        FilterColor.BLACK -> stringResource(R.string.color_black)
                        FilterColor.RED -> stringResource(R.string.color_red)
                        FilterColor.ORANGE -> stringResource(R.string.color_orange)
                        FilterColor.YELLOW -> stringResource(R.string.color_yellow)
                    }
                    Text(
                        text = stringResource(
                            R.string.profiles_summary,
                            colorLabel,
                            profile.filterConfig.colorIntensity,
                            profile.filterConfig.toneIntensity
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val activeBadges = buildList {
                        if (profile.filterConfig.doubleFilterEnabled) add(stringResource(R.string.home_double_filter_toggle))
                        if (profile.extraDimWasEnabled) add(stringResource(R.string.settings_extra_dim))
                        if (profile.grayscaleWasEnabled) add(stringResource(R.string.home_grayscale_title))
                        if (profile.systemFilterWasEnabled) add(stringResource(R.string.home_system_filter_title))
                        if (profile.tvAutoOffEnabled == true) {
                            add(
                                stringResource(R.string.settings_tv_auto_off_title) + " " +
                                    "%02d:%02d".format(profile.tvAutoOffHour ?: 0, profile.tvAutoOffMinute ?: 0)
                            )
                        }
                        if (profile.tvStandbyEnabled == true) {
                            add(
                                stringResource(R.string.settings_tv_standby_title) + " " +
                                    formatDurationMinutes(profile.tvStandbyMinutes ?: 0)
                            )
                        }
                    }
                    if (activeBadges.isNotEmpty()) {
                        Text(
                            text = activeBadges.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row {
                // Explicit colors (container + content) per state and per theme: before, the
                // "Deactivate" button used error as the background with the default text (onPrimary
                // = white), which on Dark/Black is light pink with white text - unreadable. Strong
                // red with white text on the dark themes; the theme's red on Light (which was
                // already fine).
                val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val stopContainer = if (darkTheme) Color(0xFFC62828) else MaterialTheme.colorScheme.error
                val toggleColor by animateColorAsState(
                    targetValue = if (isActive) stopContainer else MaterialTheme.colorScheme.primary,
                    animationSpec = tween(250),
                    label = "profileToggleButtonColor"
                )
                val toggleContent by animateColorAsState(
                    targetValue = if (isActive) Color.White else MaterialTheme.colorScheme.onPrimary,
                    animationSpec = tween(250),
                    label = "profileToggleButtonContent"
                )
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = toggleColor, contentColor = toggleContent),
                    modifier = Modifier.dpadFocus(RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isActive) stringResource(R.string.action_deactivate) else stringResource(R.string.action_activate))
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Trash button with its own background (a shade of red per theme) instead of just a loose icon.
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { isDeleting = true },
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (darkTheme) Color(0xFF3F1519) else Color(0xFFFFE3E0),
                        contentColor = if (darkTheme) Color(0xFFFF8A80) else Color(0xFFBA1A1A)
                    ),
                    modifier = Modifier.dpadFocus(CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    }
}
