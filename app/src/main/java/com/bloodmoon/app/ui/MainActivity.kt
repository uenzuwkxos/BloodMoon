package com.bloodmoon.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bloodmoon.app.R
import kotlinx.coroutines.flow.first
import com.bloodmoon.app.util.AccessibilityServiceHelper
import com.bloodmoon.app.data.repository.BloodMoonRepository
import androidx.lifecycle.lifecycleScope
import com.bloodmoon.app.ui.screens.AppExclusionsScreen
import com.bloodmoon.app.ui.screens.HomeScreen
import com.bloodmoon.app.ui.screens.ProfilesScreen
import com.bloodmoon.app.ui.screens.ScheduleScreen
import com.bloodmoon.app.ui.screens.SettingsScreen
import com.bloodmoon.app.ui.theme.BloodMoonTheme
import com.bloodmoon.app.util.LocaleHelper
import kotlinx.coroutines.launch

enum class NavDestination {
    HOME, PROFILES, SCHEDULE, EXCLUSIONS, SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No special handling needed either way - the filter itself still works without it,
          it just means the ongoing notification stays hidden until granted. */ }

    // Dedicated launcher for ACTION_MANAGE_OVERLAY_PERMISSION, instead of a loose startActivity.
    // Reason: startActivity() does not interrupt the current Activity's lifecycle right away -
    // Android still completes MainActivity's onCreate -> onStart -> onResume BEFORE actually
    // bringing the permission screen to the front. With no control at all, this made
    // maybeRequestOverlayPermission() (called both in onCreate and onResume) fire startActivity a
    // second time during that same "passing through" onResume, stacking two permission screens even
    // though the user only tried to open the app once. An ActivityResultLauncher, combined with the
    // overlayPermissionRequestInFlight flag right below, guarantees there is at most one request in
    // progress at a time.
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionRequestInFlight = false
        // Finished the "Display over other apps" screen: now it is accessibility's turn.
        maybeRequestAccessibility()
    }

    // Only asks for accessibility once per app launch (not on every onResume), so it does not trap
    // whoever prefers not to enable it in a cycle of settings screens.
    private var accessibilityRequestedThisLaunch = false

    // true while the overlay permission screen is open (from startActivity until the launcher above
    // receives the result) - prevents onResume from firing a second request while the first is
    // still on screen.
    private var overlayPermissionRequestInFlight = false

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleHelper.readPersisted(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest opens the Activity with the splash theme (black + logo); switch to the
        // normal theme before building the UI.
        setTheme(R.style.Theme_BloodMoon)
        super.onCreate(savedInstanceState)
        // Needed so the app can draw behind the status bar/nav bar/cutout - every screen applies
        // its own insets padding correctly (statusBarsPadding, navigationBarsPadding, or the
        // safeDrawing windowInsetsPadding SideNavLayout uses for its rail).
        enableEdgeToEdge()

        maybeRequestOverlayPermission()
        maybeRequestAccessibility()
        maybeRequestNotificationPermission()

        setContent {
            val appSettings by viewModel.appSettings.collectAsState()
            val settingsLoaded by viewModel.settingsLoaded.collectAsState()

            // While the settings are not loaded from disk, shows the same splash screen (pure black
            // + logo) instead of a flash of the default theme that soon switches to the chosen one.
            if (!settingsLoaded) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_splash_art),
                        contentDescription = null,
                        modifier = androidx.compose.ui.Modifier.size(240.dp)
                    )
                }
                return@setContent
            }

            BloodMoonTheme(
                themeMode = appSettings.themeMode,
                primaryAccentHex = appSettings.primaryAccentColor
            ) {
                BloodMoonApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers the case where the user leaves this screen to grant the overlay permission
        // in system settings and comes back - re-check instead of asking again from scratch.
        maybeRequestOverlayPermission()
    }

    private fun maybeRequestOverlayPermission() {
        // No SDK_INT check - Settings.canDrawOverlays/ACTION_MANAGE_OVERLAY_PERMISSION exist since
        // API 23 (M), below this app's minSdk 28. overlayPermissionRequestInFlight avoids the
        // duplicate second screen: without this flag, the onResume of that same onCreate -> onStart
        // -> onResume batch (which happens before the permission screen actually gains focus) came
        // here again and called startActivity a second time.
        if (!overlayPermissionRequestInFlight && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            if (intent.resolveActivity(packageManager) != null) {
                overlayPermissionRequestInFlight = true
                overlayPermissionLauncher.launch(intent)
            }
        }
    }

    /**
     * Accessibility service = recommended mode (filter over the whole system, immune to whatever
     * kills regular overlays). Order: (1) already active -> nothing; (2) with Root/Shizuku/ADB the
     * app turns it on by itself; (3) with no access at all, opens the system accessibility screen -
     * as is already done with "Display over other apps". If the user does not enable it, the home
     * screen shows a notice they can dismiss (and, once dismissed, the app does not ask again).
     */
    private fun maybeRequestAccessibility() {
        if (accessibilityRequestedThisLaunch || overlayPermissionRequestInFlight) return
        accessibilityRequestedThisLaunch = true
        lifecycleScope.launch {
            val settings = BloodMoonRepository(applicationContext).appSettingsFlow.first()
            if (!settings.useAccessibilityOverlay || settings.a11yWarningDismissed) return@launch
            if (AccessibilityServiceHelper.isEnabled(this@MainActivity)) return@launch
            if (AccessibilityServiceHelper.ensureEnabledPrivileged(applicationContext, settings)) return@launch
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }

    private fun maybeRequestNotificationPermission() {
        // Android 13+ (API 33) requires this at runtime for the ongoing "filter active"
        // notification to actually show up - without it the foreground service still runs
        // fine, the notification (and its pause/stop actions) just stays invisible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * The bar position is no longer chosen by the user: BloodMoonApp decides on its own between
 * BottomNavLayout (most phones in portrait) and SideNavLayout (tablets, or any screen >= 600dp
 * wide, which also covers phones in landscape), using the same logic Google officially recommends
 * for WindowSizeClass. The "Top" option was removed for good - there is no horizontal bar layout at
 * the top anymore.
 */
@Composable
fun BloodMoonApp(viewModel: MainViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.HOME) }

    // Shared across every screen so any of them can surface a quick, non-blocking message (e.g.
    // "Grayscale requires ADB, Shizuku, or Root" when the user taps a gated toggle before it's
    // actually available) without needing their own separate host/state plumbing.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val showSnackbar: (String) -> Unit = { message ->
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }
    val navigateToSettings: () -> Unit = { currentDestination = NavDestination.SETTINGS }

    val content: @Composable () -> Unit = {
        ScreenContent(
            destination = currentDestination,
            viewModel = viewModel,
            onNavigateToSettings = navigateToSettings,
            onShowMessage = showSnackbar
        )
    }

    // FocusRequester of the navigation bar's "Home" item - used by the TV remote Back/Exit shortcut
    // right below (moveHomeFocus). Shared between BottomNavLayout and SideNavLayout: which of the
    // two is up depends only on the screen width, so only one exists at a time, but the requester
    // has to be the same in both cases.
    val homeFocusRequester = remember { FocusRequester() }

    // true while the bar's "Home" item has focus - updated by the item's own onFocusChanged (see
    // NavBarItems/SideNavLayout). This is what decides what the BackHandler below does: the first
    // Back moves focus here, the second Back (with focus already here) actually leaves the app,
    // instead of staying trapped re-focusing the same button forever.
    var homeItemFocused by remember { mutableStateOf(false) }

    val activity = LocalContext.current as? android.app.Activity

    // TV remote Back/Exit button: instead of leaving the app or going back a screen (there is no
    // screen stack here, it is a fixed tab bar), it moves the selection to the Home tab - a quick
    // shortcut requested for navigating between tabs without having to go through the whole bar. It
    // does not enter the Home tab by itself on the first press (currentDestination does not change
    // here) - it only moves focus to the button. A second press, with focus already there, really
    // leaves the app (finish()) - without this the user would be stuck with no way out using the
    // remote. It needs no extra check for an "open panel/dialog/dropdown": AlertDialog and
    // DropdownMenu (Material3) are already implemented with their own Window/Popup, which
    // intercepts the Back event BEFORE it reaches this BackHandler - closing the dialog/dropdown
    // instead of moving focus. This BackHandler is only reached when none of those is open on top,
    // which is exactly the requested behavior.
    BackHandler {
        if (homeItemFocused) {
            activity?.finish()
        } else {
            runCatching { homeFocusRequester.requestFocus() }
        }
    }

    // The bar position is no longer a manual user choice - decided automatically by the screen
    // width, the same breakpoint (600dp) that Google itself uses to tell "compact" (most phones in
    // portrait) from "medium/expanded" (tablets, and phones in landscape) in WindowSizeClass. We
    // did not bring in the material3-window-size-class dependency just for this - screenWidthDp
    // already gives us exactly the same data behind it.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useRail = screenWidthDp >= 600

    if (useRail) {
        SideNavLayout(
            currentDestination = currentDestination,
            onSelect = { currentDestination = it },
            snackbarHostState = snackbarHostState,
            homeFocusRequester = homeFocusRequester,
            onHomeFocusChanged = { homeItemFocused = it },
            content = content
        )
    } else {
        BottomNavLayout(
            currentDestination = currentDestination,
            onSelect = { currentDestination = it },
            snackbarHostState = snackbarHostState,
            homeFocusRequester = homeFocusRequester,
            onHomeFocusChanged = { homeItemFocused = it },
            content = content
        )
    }
}

/** The previous, already-correct behavior: Scaffold handles system bar insets for us automatically. */
// FocusProperties.exit (used in the content Box below, to stop the D-pad from leaking to the
// navigation bar) is @ExperimentalComposeUiApi - an explicit opt-in required by the compiler.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun BottomNavLayout(
    currentDestination: NavDestination,
    onSelect: (NavDestination) -> Unit,
    snackbarHostState: SnackbarHostState,
    homeFocusRequester: FocusRequester,
    onHomeFocusChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavBarItems(currentDestination, onSelect, homeFocusRequester, onHomeFocusChanged)
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data -> BloodMoonSnackbar(data) }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                // The bottom navigation bar should only be reachable by pressing "Back" (see
                // BackHandler in BloodMoonApp) - not by navigating with the D-pad to the bottom
                // edge of the content and "leaking" into it. Without this, the D-pad could escape
                // from the content to the bar normally, which was also the real cause of the
                // "increment/decrement gets stuck at 0%/100%" bug: on reaching a limit, the Stepper
                // tried to move focus onward in the same direction, that movement leaked to a bar
                // item, and that accidentally triggered the "focus on the Home tab" logic (which
                // reacts to any bar item gaining focus, not just a real Back press). History: this
                // is the officially documented technique (focusProperties/exit), and it does work
                // here - confirmed. An attempt to replace it with onPreviewKeyEvent+moveFocus (due
                // to an incorrect suspicion of a bug with LazyColumn) was reverted: that swap
                // brought no real advantage and the original reason for the suspicion was not
                // confirmed (Exclusions and Options also use LazyColumn and always worked fine with
                // focusProperties/exit). The real problem, isolated later, was another one:
                // Profiles and Schedules use AlertDialog, which renders in a Window separate from
                // the main tree - no fix applied here, in this Box, would ever reach the content of
                // an open AlertDialog. That case now has its own local fix, inside each AlertDialog
                // (see ProfilesScreen and ScheduleScreen). Down is the only possible leak direction
                // here (the bar sits below the content) - the other directions (Up/Left/Right) keep
                // the default Compose behavior, with no restriction.
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * Vertical NavigationRail instead of a horizontal bar - used on wide screens (tablets, or phones in
 * landscape), decided automatically by BloodMoonApp. Always on the left (the option to choose
 * side/position manually was removed - the "Top" bar also no longer exists).
 *
 * Vertically centered when it fits: the stable Material3 NavigationRail has no arrangement
 * parameter of its own (that only arrived in the experimental WideNavigationRail) - the real way to
 * center is to wrap the items in an inner Column with fillMaxHeight() + Arrangement.spacedBy(_,
 * Alignment.CenterVertically), which is the pattern even the documentation uses for this case.
 *
 * windowInsetsPadding(WindowInsets.safeDrawing) on the rail fixes a real reported bug: with no
 * insets handling here, the side bar drew under the status bar (and, potentially, behind a side
 * cutout/notch in landscape) - unlike BottomNavLayout, which gets this for free via Scaffold.
 * safeDrawing (not just statusBars) also covers the system navigation bar when it is on the side
 * (gestures/3-button on the opposite edge) and the notch.
 *
 * The content Box (to the right of the rail) also needs its own windowInsetsPadding for the same
 * reason: BottomNavLayout gets insets handling for free through the padding that Scaffold computes
 * and applies automatically, but here the Row/Box are assembled by hand, so nothing covered this
 * Box before - the screens (HomeScreen, SettingsScreen etc.) drew straight under the status bar,
 * which in some contexts (cards pinned at the top, headers) actually cut off or overlapped UI. We
 * cover Top (status bar) and End (the side opposite the rail, where there may be a notch/cutout in
 * landscape) - not Start, because that side already belongs to the rail, and not Bottom, because no
 * screen here has fixed content stuck to the bottom edge that depends on it (and it avoids
 * duplicate padding with the navigationBarsPadding() that the SnackbarHost already applies below).
 */
// FocusProperties.exit (same reason as the @OptIn in BottomNavLayout) is @ExperimentalComposeUiApi.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SideNavLayout(
    currentDestination: NavDestination,
    onSelect: (NavDestination) -> Unit,
    snackbarHostState: SnackbarHostState,
    homeFocusRequester: FocusRequester,
    onHomeFocusChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavigationRail(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                navItems.forEach { item ->
                    val label = stringResource(item.labelRes)
                    val itemModifier = if (item.destination == NavDestination.HOME) {
                        Modifier
                            .focusRequester(homeFocusRequester)
                            .onFocusChanged { onHomeFocusChanged(it.isFocused) }
                    } else {
                        Modifier
                    }
                    NavigationRailItem(
                        selected = currentDestination == item.destination,
                        onClick = { onSelect(item.destination) },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
                        modifier = itemModifier
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                // Same approach/history as BottomNavLayout (see the full comment there), only in
                // the Left direction - here the rail is to the left of the content, not below it.
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Left) FocusRequester.Cancel else FocusRequester.Default
                    }
                }
        ) {
            content()
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) { data -> BloodMoonSnackbar(data) }
        }
    }
}

private data class NavItem(
    val destination: NavDestination,
    val icon: ImageVector,
    val labelRes: Int
)

private val navItems = listOf(
    NavItem(NavDestination.HOME, Icons.Default.Home, R.string.nav_home),
    NavItem(NavDestination.PROFILES, Icons.Default.ColorLens, R.string.nav_profiles),
    NavItem(NavDestination.SCHEDULE, Icons.Default.Schedule, R.string.nav_schedule),
    NavItem(NavDestination.EXCLUSIONS, Icons.Default.Apps, R.string.nav_exclusions),
    NavItem(NavDestination.SETTINGS, Icons.Default.Settings, R.string.nav_settings)
)

@Composable
private fun RowScope.NavBarItems(
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    homeFocusRequester: FocusRequester,
    onHomeFocusChanged: (Boolean) -> Unit
) {
    navItems.forEach { item ->
        val label = stringResource(item.labelRes)
        val itemModifier = if (item.destination == NavDestination.HOME) {
            Modifier
                .focusRequester(homeFocusRequester)
                .onFocusChanged { onHomeFocusChanged(it.isFocused) }
        } else {
            Modifier
        }
        NavigationBarItem(
            selected = current == item.destination,
            onClick = { onSelect(item.destination) },
            icon = { Icon(item.icon, contentDescription = label) },
            label = { Text(label) },
            modifier = itemModifier
        )
    }
}

/**
 * Rounded card with an icon, in the same visual style as the command card inside the ADB panel
 * (SettingsScreen) - replacing Material3's plain-text default Snackbar, used before for every quick
 * message in the app (e.g. "Requires Shizuku or Root to work"). The default Snackbar and this card
 * have no API relationship: SnackbarHost already hands over a ready SnackbarData, this composable
 * only decides how to draw it - no onShowMessage(String) call site had to change.
 */
@Composable
private fun BloodMoonSnackbar(data: SnackbarData) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ScreenContent(
    destination: NavDestination,
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    when (destination) {
        NavDestination.HOME -> HomeScreen(
            viewModel = viewModel,
            onNavigateToSettings = onNavigateToSettings,
            onShowMessage = onShowMessage
        )
        NavDestination.PROFILES -> ProfilesScreen(viewModel = viewModel)
        NavDestination.SCHEDULE -> ScheduleScreen(viewModel = viewModel)
        NavDestination.EXCLUSIONS -> AppExclusionsScreen(viewModel = viewModel)
        NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel, onShowMessage = onShowMessage)
    }
}
