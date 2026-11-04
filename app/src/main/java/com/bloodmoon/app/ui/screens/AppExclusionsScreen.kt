package com.bloodmoon.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bloodmoon.app.R
import com.bloodmoon.app.util.dpadFocus
import com.bloodmoon.app.util.AppIconCache
import com.bloodmoon.app.data.model.AppExclusion
import com.bloodmoon.app.util.AccessibilityServiceHelper
import com.bloodmoon.app.data.model.ExclusionKind
import com.bloodmoon.app.ui.MainViewModel
import com.bloodmoon.app.util.UsageAccessHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppExclusionsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val excludedPackages by viewModel.excludedPackages.collectAsState()
    val grayscaleExcluded by viewModel.grayscaleExcludedPackages.collectAsState()
    // FocusRequester of the Select/Deselect All button - used by the search field's
    // onPreviewKeyEvent right below to fix the focus getting stuck in the field on TV (see the full
    // comment there, and the same pattern in ProfilesScreen).
    val selectAllButtonFocusRequester = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<AppExclusion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasUsageAccess by remember { mutableStateOf(UsageAccessHelper.hasUsageAccess(context)) }
    // The accessibility service also reports the foreground app (without the Usage access
    // permission), so either of the two enables per-app exclusions.
    var a11yEnabled by remember { mutableStateOf(AccessibilityServiceHelper.isEnabled(context)) }

    // Re-evaluates whenever the screen returns to the foreground (e.g. the user granted OR revoked
    // the Usage permission in Settings and came back) - before, this was only checked once inside
    // the LaunchedEffect(Unit) below, so revoking the permission and coming back to this screen did
    // not hide the list/search again, and granting the permission did not make them appear without
    // reopening the whole screen. Same recheck pattern already used for the Shizuku panel in
    // SettingsScreen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = UsageAccessHelper.hasUsageAccess(context)
                a11yEnabled = AccessibilityServiceHelper.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        hasUsageAccess = UsageAccessHelper.hasUsageAccess(context)

        // Phase 1: only names and packages - fast, without touching icons. The list shows up on
        // screen almost instantly instead of waiting for all ~100+ icon resolutions to finish
        // first, which is what gave that heavy-app feeling when opening this screen.
        val apps = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            // Thanks to the <queries> LAUNCHER declaration in the manifest, this now returns
            // real, user-facing apps instead of the near-empty list Android 11+ gives out by
            // default without it.
            val launcherApps = pm.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
                0
            )

            val seen = LinkedHashSet<String>()
            val list = mutableListOf<AppExclusion>()

            for (resolveInfo in launcherApps) {
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
                val pkg = appInfo.packageName
                if (!seen.add(pkg)) continue
                if (pkg == context.packageName) continue // excluding ourselves is meaningless

                // System app = preinstalled and never updated. Preinstalled ones the user updated
                // (Chrome, YouTube...) count as user apps.
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
                list.add(AppExclusion(packageName = pkg, appName = label, isSystemApp = isSystem))
            }

            list.sortedBy { it.appName.lowercase() }
        }
        installedApps = apps
        isLoading = false
        // Removes exclusions of uninstalled/disabled apps (they caused a phantom "1 selected").
        viewModel.pruneExcludedPackages(apps.map { it.packageName }.toSet())
    }

    // Icons are loaded on demand per row (see AppIconCache); when leaving the screen, the cache is
    // emptied so it does not retain bitmaps while the app is in the background.
    DisposableEffect(Unit) {
        onDispose { AppIconCache.clear() }
    }

    // Only recomputes when the list, the search or the selection actually change - before, it ran
    // again on every recomposition of the screen. Selected apps go to the top (keeping alphabetical
    // order within each group), so what the user has already checked is always visible without
    // scrolling the whole list.
    val installedPackageNames = remember(installedApps) { installedApps.map { it.packageName }.toSet() }
    val visibleSelectedCount = remember(installedPackageNames, excludedPackages, grayscaleExcluded) {
        if (installedPackageNames.isEmpty()) 0
        else (excludedPackages + grayscaleExcluded).count { it in installedPackageNames }
    }

    // Persisted in AppSettings: before, it was a local remember and went back to off when leaving
    // the tab or the app.
    val appSettingsState by viewModel.appSettings.collectAsState()
    val includeSystemApps = appSettingsState.includeSystemAppsInExclusions

    val filteredApps = remember(installedApps, searchQuery, excludedPackages, grayscaleExcluded, includeSystemApps) {
        installedApps
            .filter { includeSystemApps || !it.isSystemApp }
            .filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<AppExclusion> {
                    excludedPackages.contains(it.packageName) ||
                        grayscaleExcluded.contains(it.packageName)
                }
                    .thenBy { it.appName.lowercase() }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (!hasUsageAccess && !a11yEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.exclusions_usage_access_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.exclusions_usage_access_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { UsageAccessHelper.openUsageAccessSettings(context) }, modifier = Modifier.dpadFocus(RoundedCornerShape(50))) {
                            Text(stringResource(R.string.exclusions_usage_access_action))
                        }
                    }
                }
            }
            // Search and app list stay hidden until the Usage permission is granted - it makes no
            // sense to let the user search/check apps to exclude when the feature that depends on
            // that permission (the foreground app monitor, see
            // FilterOverlayService.startExclusionMonitor) will not work anyway. It goes hidden
            // again if the permission is revoked later (see the DisposableEffect above) - it is not
            // an "only the first time" state. Explicit if/else instead of an early return inside
            // the Column lambda, to keep the flow clear.
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    // Fixes the focus getting stuck in the search field on TV - see the full
                    // comment on the equivalent OutlinedTextField in ProfilesScreen about why this
                    // is needed and why this is the officially documented Android fix.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            runCatching { selectAllButtonFocusRequester.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
                placeholder = { Text(stringResource(R.string.exclusions_search_placeholder)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.exclusions_selected_count, visibleSelectedCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                // Outside a Card/Surface the text does not inherit a content color (it came out
                // black on Dark and Black): explicit color over the screen background.
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .dpadFocus(RoundedCornerShape(12.dp))
                    .clickable { viewModel.setIncludeSystemAppsInExclusions(!includeSystemApps) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = includeSystemApps,
                    onCheckedChange = { viewModel.setIncludeSystemAppsInExclusions(it) }
                )
                Text(
                    text = stringResource(R.string.exclusions_include_system),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // "Select all" applies only to the visible apps (search and "system apps" applied), per
            // feature, and becomes "Deselect all" when all the visible ones are already checked.
            val visibleNames = filteredApps.map { it.packageName }.toSet()
            val allFilter = visibleNames.isNotEmpty() && visibleNames.all { it in excludedPackages }
            val allGrayscale = visibleNames.isNotEmpty() && visibleNames.all { it in grayscaleExcluded }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        viewModel.setExcluded(
                            ExclusionKind.FILTER,
                            if (allFilter) excludedPackages - visibleNames else excludedPackages + visibleNames
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .dpadFocus(RoundedCornerShape(14.dp))
                        .focusRequester(selectAllButtonFocusRequester)
                ) {
                    Text(
                        text = stringResource(
                            if (allFilter) R.string.exclusions_unmark_all_filter else R.string.exclusions_mark_all_filter
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        viewModel.setExcluded(
                            ExclusionKind.GRAYSCALE,
                            if (allGrayscale) grayscaleExcluded - visibleNames else grayscaleExcluded + visibleNames
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .dpadFocus(RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = stringResource(
                            if (allGrayscale) R.string.exclusions_unmark_all_grayscale else R.string.exclusions_mark_all_grayscale
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }


            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filteredApps.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.exclusions_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        // Extracted into its own @Composable function with stable primitive
                        // parameters (Boolean, Drawable?) - so Compose can skip recomposing the
                        // rows whose isChecked did not change, instead of recomposing the ~100+
                        // whole rows every time ANY checkbox is tapped. animateItem() animates the
                        // selected item moving up to the top - it used to be
                        // animateItemPlacement(tween(250)), removed from Compose Foundation and
                        // replaced by this one (official source:
                        // developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyItemScope).
                        // placementSpec uses IntOffset (it is the item's position that animates),
                        // not Float like the opacity tween used before - hence
                        // tween<IntOffset>(250) instead of a plain tween(250) (which the compiler
                        // could not infer by itself).
                        ExclusionRow(
                            modifier = Modifier.animateItem(placementSpec = tween<androidx.compose.ui.unit.IntOffset>(250)),
                            app = app,
                            filterExcluded = excludedPackages.contains(app.packageName),
                            grayscaleExcluded = grayscaleExcluded.contains(app.packageName),
                            onToggle = { kind -> viewModel.toggleExcluded(kind, app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExclusionRow(
    modifier: Modifier = Modifier,
    app: AppExclusion,
    filterExcluded: Boolean,
    grayscaleExcluded: Boolean,
    onToggle: (ExclusionKind) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val iconPx = with(density) { 40.dp.roundToPx() }
                val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                    initialValue = AppIconCache.get(app.packageName),
                    key1 = app.packageName
                ) {
                    value = AppIconCache.load(context, app.packageName, iconPx)
                }
                val loadedIcon = icon
                if (loadedIcon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = loadedIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            // Rounded square, not circular - explicitly requested for this screen.
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Um chip por recurso: marcado = neste app esse recurso fica DESLIGADO.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExclusionChip(
                label = stringResource(R.string.exclusions_chip_filter),
                selected = filterExcluded,
                onClick = { onToggle(ExclusionKind.FILTER) },
                modifier = Modifier.weight(1f)
            )
            ExclusionChip(
                label = stringResource(R.string.exclusions_chip_grayscale),
                selected = grayscaleExcluded,
                onClick = { onToggle(ExclusionKind.GRAYSCALE) },
                modifier = Modifier.weight(1f)
            )
        }
        }
    }
}

@Composable
private fun ExclusionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.dpadFocus(RoundedCornerShape(8.dp)),
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
