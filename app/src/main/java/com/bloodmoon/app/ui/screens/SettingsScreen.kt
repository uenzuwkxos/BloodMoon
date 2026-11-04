package com.bloodmoon.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.bloodmoon.app.BuildConfig
import com.bloodmoon.app.R
import com.bloodmoon.app.util.dpadFocus
import com.bloodmoon.app.data.model.AppThemeMode
import com.bloodmoon.app.root.RootHelper
import com.bloodmoon.app.shizuku.ShizukuHelper
import com.bloodmoon.app.ui.MainViewModel
import com.bloodmoon.app.ui.theme.dialogSurfaceColor
import com.bloodmoon.app.util.DeviceType
import com.bloodmoon.app.util.GrantResult
import com.bloodmoon.app.util.SectionCard
import com.bloodmoon.app.util.LabeledStepper
import com.bloodmoon.app.util.Stepper
import com.bloodmoon.app.util.AccessMode
import com.bloodmoon.app.util.BloodMoonDialog
import com.bloodmoon.app.util.DiagnosticLog
// AboutLibraries (Open Source Licenses screen) - explicit import of libraryColors: it is an
// extension function of LibraryDefaults declared in the package
// com.mikepenz.aboutlibraries.ui.compose.m3 (SharedLibraries.kt) - extension functions only resolve
// with an explicit import in scope, qualifying just the receiver type is not enough.
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SHIZUKU_REQUEST_CODE = 8241

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settings by viewModel.appSettings.collectAsState()

    val scrollState = rememberScrollState()

    var showImportDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    // Used to be showAboutDialog (an AlertDialog) - About became its own full screen (explicit
    // request), replacing all the content of this screen instead of floating over it.
    var showAboutScreen by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var showAdbPanel by remember { mutableStateOf(false) }
    var showDiagnostic by remember { mutableStateOf(false) }
    var showShizukuRequirementDialog by remember { mutableStateOf(false) }

    // Root/Shizuku availability checks can block (root especially, since it may prompt Magisk),
    // so they're computed once in the background instead of on every recomposition of this screen.
    var shizukuAvailable by remember { mutableStateOf(ShizukuHelper.isShizukuAvailable()) }
    var shizukuHasPermission by remember { mutableStateOf(ShizukuHelper.hasShizukuPermission()) }
    var rootChecked by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var writeSecureSettingsGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var grantingPermission by remember { mutableStateOf(false) }
    var grantErrorDetail by remember { mutableStateOf<String?>(null) }

    fun recheckPrivilegedState() {
        shizukuAvailable = ShizukuHelper.isShizukuAvailable()
        shizukuHasPermission = ShizukuHelper.hasShizukuPermission()
        writeSecureSettingsGranted = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        scope.launch {
            rootAvailable = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
            rootChecked = true
        }
    }

    LaunchedEffect(Unit) {
        rootAvailable = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
        rootChecked = true
    }

    // Re-checks Shizuku/Root/ADB state whenever the user comes back to this screen (e.g. after
    // toggling the permission on/off in the Magisk app, or starting Shizuku externally) - before
    // this, a switch could only ever reflect what was true at the exact moment it was tapped,
    // and stayed stuck showing stale state for the rest of the session otherwise. This is what
    // "toggled root off in Magisk, came back, toggle didn't update" was actually caused by.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                recheckPrivilegedState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Receives the result of ShizukuHelper.requestShizukuPermission() below - without this
    // registered, the permission dialog can fire but the app never finds out what the user chose.
    // Crucially, useShizuku is only ever persisted to true HERE, once the grant is actually
    // confirmed - never optimistically at the moment the toggle is tapped. That's what used to
    // let a switch turn "on" (and unlock Grayscale/Filtro via Sistema/Extra Dim below) even when Shizuku
    // was never actually available or the user denied the system dialog.
    val shizukuGrantedMsg = stringResource(R.string.settings_shizuku_granted_toast)
    val shizukuDeniedMsg = stringResource(R.string.settings_shizuku_denied_toast)
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                shizukuHasPermission = granted
                if (granted) {
                    viewModel.updateAppSettings(settings.copy(useShizuku = true))
                    Toast.makeText(context, shizukuGrantedMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, shizukuDeniedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        ShizukuHelper.addPermissionResultListener(listener)
        onDispose { ShizukuHelper.removePermissionResultListener(listener) }
    }

    val adbCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    // All languages the app translates (res/values-XX). Names in their own language, so users can
    // find theirs even if the app is in another language.
    val languages = listOf(
        "system" to stringResource(R.string.language_system),
        "pt" to stringResource(R.string.language_pt),
        "en" to stringResource(R.string.language_en),
        "es" to stringResource(R.string.language_es),
        "de" to stringResource(R.string.language_de),
        "fr" to stringResource(R.string.language_fr),
        "zh" to stringResource(R.string.language_zh),
        "ja" to stringResource(R.string.language_ja),
        "ko" to stringResource(R.string.language_ko),
        "hi" to stringResource(R.string.language_hi),
        "ar" to stringResource(R.string.language_ar),
        "ru" to stringResource(R.string.language_ru),
        "id" to stringResource(R.string.language_id),
        "it" to stringResource(R.string.language_it),
        "nl" to stringResource(R.string.language_nl),
        "pl" to stringResource(R.string.language_pl)
    )

    val backupFileName = remember {
        "blood_moon_backup_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
    }
    val exportSuccessMsg = stringResource(R.string.settings_export_success)
    val exportFailMsg = stringResource(R.string.settings_export_failed)
    val importSuccessMsg = stringResource(R.string.settings_import_success)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = viewModel.repository.exportConfigJson()
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            Toast.makeText(context, if (success) exportSuccessMsg else exportFailMsg, Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            }
            if (json == null) {
                importError = context.getString(R.string.settings_import_read_failed)
                showImportDialog = true
                return@launch
            }
            val error = viewModel.repository.importConfigJson(json)
            if (error == null) {
                Toast.makeText(context, importSuccessMsg, Toast.LENGTH_SHORT).show()
            } else {
                importError = error
                showImportDialog = true
            }
        }
    }

    if (showAboutScreen) {
        AboutScreen(onBack = { showAboutScreen = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // APPEARANCE
        SectionCard(title = stringResource(R.string.settings_appearance)) {
            // Theme: used to be 3 loose buttons side by side, replaced by a dropdown - the same
            // visual pattern as the Language selector right below, instead of two different
            // components doing the same thing (choosing 1 of N options) in two different ways on
            // the same screen.
            CenteredLabel(stringResource(R.string.settings_theme_mode))
            Spacer(modifier = Modifier.height(4.dp))
            val themeOptions = listOf(
                AppThemeMode.SYSTEM to stringResource(R.string.theme_auto),
                AppThemeMode.LIGHT to stringResource(R.string.theme_light),
                AppThemeMode.DARK to stringResource(R.string.theme_dark),
                AppThemeMode.PURE_BLACK to stringResource(R.string.theme_black)
            )
            // Box + DropdownMenu instead of ExposedDropdownMenuBox/ExposedDropdownMenu: the latter
            // pair depends on ExposedDropdownMenuBoxScope (an experimental M3 API whose
            // package/signature changed between versions), which did not resolve in this project's
            // version - "Unresolved reference 'ExposedDropdownMenu'" in the real build.
            // DropdownMenu is the stable API, without that version coupling.
            Box {
                val currentThemeLabel = themeOptions.find { it.first == settings.themeMode }?.second ?: themeOptions.first().second
                OutlinedTextField(
                    value = currentThemeLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                    colors = dropdownTriggerFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocus(RoundedCornerShape(16.dp))
                        .clickable { themeExpanded = !themeExpanded }
                )
                // A read-only field underneath captures the click - which is why the Box has a
                // second invisible, clickable overlay the full size of the field, since a readOnly
                // OutlinedTextField does not fire onClick by itself (only focus/onValueChange,
                // which does nothing here).
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .dpadFocus(RoundedCornerShape(16.dp))
                        .clickable { themeExpanded = !themeExpanded }
                )
                DropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false }
                ) {
                    themeOptions.forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                themeExpanded = false
                                if (mode != settings.themeMode) {
                                    viewModel.updateAppSettings(settings.copy(themeMode = mode))
                                }
                            }
                        )
                    }
                }
            }

        }

        // IDIOMA
        SectionCard(title = stringResource(R.string.settings_language)) {
            // Same reason as the Theme dropdown above: Box + DropdownMenu (stable) instead of
            // ExposedDropdownMenuBox/ExposedDropdownMenu (it did not resolve in the real build).
            Box {
                val currentLangLabel = languages.find { it.first == settings.languageCode }?.second ?: languages.first().second
                OutlinedTextField(
                    value = currentLangLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    colors = dropdownTriggerFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocus(RoundedCornerShape(16.dp))
                        .clickable { languageExpanded = !languageExpanded }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .dpadFocus(RoundedCornerShape(16.dp))
                        .clickable { languageExpanded = !languageExpanded }
                )
                DropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    languages.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                languageExpanded = false
                                if (code != settings.languageCode) {
                                    viewModel.updateAppSettings(settings.copy(languageCode = code))
                                    // Locale is applied in attachBaseContext(), which only runs
                                    // when the Activity is (re)created - so an explicit recreate
                                    // is required to actually see the new language take effect.
                                    activity?.recreate()
                                }
                            }
                        )
                    }
                }
            }
        }

        // COMPORTAMENTO
        SectionCard(title = stringResource(R.string.settings_behavior)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_pause_on_reboot),
                description = stringResource(R.string.settings_pause_on_reboot_desc),
                checked = settings.pauseOnReboot,
                onCheckedChange = { viewModel.updateAppSettings(settings.copy(pauseOnReboot = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSwitchRow(
                title = stringResource(R.string.settings_a11y_overlay_title),
                description = stringResource(R.string.settings_a11y_overlay_desc),
                checked = settings.useAccessibilityOverlay,
                onCheckedChange = {
                    viewModel.updateAppSettings(
                        settings.copy(
                            useAccessibilityOverlay = it,
                            // Turning it back on shows the notice again (if the service is not active).
                            a11yWarningDismissed = if (it) false else settings.a11yWarningDismissed
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            FadeDurationSlider(
                valueMs = settings.fadeDurationMs,
                onValueChange = { viewModel.updateAppSettings(settings.copy(fadeDurationMs = it)) }
            )
        }

        // ADVANCED FEATURES - Root, Shizuku and ADB grant the same system permission
        // (WRITE_SECURE_SETTINGS), so they live together here.
        SectionCard(title = stringResource(R.string.settings_advanced)) {

            SettingsSwitchRow(
                title = stringResource(R.string.settings_shizuku),
                description = when {
                    !shizukuAvailable -> stringResource(R.string.settings_shizuku_unavailable)
                    !shizukuHasPermission -> stringResource(R.string.settings_shizuku_no_permission)
                    else -> stringResource(R.string.settings_shizuku_available)
                },
                // Locked: it only shows as on if the permission has actually been granted - without
                // this the toggle lied about a state that did not really exist.
                checked = settings.useShizuku && shizukuHasPermission,
                // Same pattern as the ADB toggle right below (enabled =
                // !writeSecureSettingsGranted): once truly active, the user can no longer turn it
                // off with the toggle itself - it is grayed out/locked. This prevents the user from
                // turning it off by accident and the app falling back to the regular overlay
                // mid-use. The only way to deactivate it after that is Shizuku itself (the app
                // detects and reflects that on its own, via shizukuHasPermission re-evaluated when
                // the app returns to the foreground - see the DisposableEffect(ON_RESUME) right
                // below this declaration).
                enabled = !(settings.useShizuku && shizukuHasPermission),
                onCheckedChange = { checked ->
                    if (!checked) {
                        viewModel.updateAppSettings(settings.copy(useShizuku = false))
                    } else if (!shizukuAvailable) {
                        // It never falls into an "else" that sets useShizuku = true without
                        // checking anything - that was the real bug: if Shizuku was not available,
                        // the toggle "turned on" anyway, and that in turn unlocked Grayscale/System
                        // Filter/Extra Dim as if the permission really existed. Now, unavailable =
                        // shows the requirements dialog (Canta style).
                        showShizukuRequirementDialog = true
                    } else if (!shizukuHasPermission) {
                        ShizukuHelper.requestShizukuPermission(SHIZUKU_REQUEST_CODE)
                        Toast.makeText(context, context.getString(R.string.settings_shizuku_dialog_prompt_toast), Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_root),
                description = when {
                    !rootChecked -> stringResource(R.string.settings_root_checking)
                    rootAvailable -> stringResource(R.string.settings_root_available)
                    else -> stringResource(R.string.settings_root_unavailable)
                },
                // Likewise: it only turns on if root access has actually been confirmed by Magisk/KernelSU.
                checked = settings.useRoot && rootAvailable,
                enabled = rootChecked,
                onCheckedChange = { checked ->
                    scope.launch {
                        if (checked) {
                            val confirmed = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
                            rootAvailable = confirmed
                            if (confirmed) {
                                viewModel.updateAppSettings(settings.copy(useRoot = true))
                                Toast.makeText(context, context.getString(R.string.settings_root_granted_toast), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.settings_root_denied_toast), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.updateAppSettings(settings.copy(useRoot = false))
                        }
                    }
                }
            )

            // ADB = the WRITE_SECURE_SETTINGS permission given once from the computer; it stays in
            // the system until the app is uninstalled and the app cannot revoke it. The switch is
            // logical: when off, the app ignores the permission (and the Advanced features show up
            // as unavailable on Home again, unless Root/Shizuku are on).
            val adbActive = AccessMode.adbActive(context, settings)
            SettingsSwitchRow(
                title = stringResource(R.string.settings_adb),
                description = when {
                    adbActive -> stringResource(R.string.settings_adb_ready)
                    writeSecureSettingsGranted && settings.adbEnabled == false ->
                        stringResource(R.string.settings_adb_disabled)
                    else -> stringResource(R.string.settings_adb_not_ready)
                },
                checked = adbActive,
                onCheckedChange = { checked ->
                    when {
                        !checked -> viewModel.updateAppSettings(settings.copy(adbEnabled = false))
                        writeSecureSettingsGranted -> viewModel.updateAppSettings(settings.copy(adbEnabled = true))
                        else -> showAdbPanel = true
                    }
                }
            )
            // Extra Dim, Grayscale and System Filter were moved to the Home tab (no longer
            // duplicated here) - there they show dimmed until one of the dependencies below is
            // ready. The grant-permission button below stays here: it is useful even with none of
            // those three on, so the user can secure the permission in advance.
            if ((settings.useRoot || settings.useShizuku) && !writeSecureSettingsGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        grantingPermission = true
                        grantErrorDetail = null
                        scope.launch {
                            val result = when {
                                settings.useRoot -> withContext(Dispatchers.IO) {
                                    RootHelper.grantWriteSecureSettingsPermission(context)
                                }
                                // The outer if already guarantees useRoot || useShizuku, so
                                // reaching here implies useShizuku - else instead of checking
                                // again.
                                else -> ShizukuHelper.grantWriteSecureSettingsPermission(context)
                            }
                            grantingPermission = false
                            when (result) {
                                is GrantResult.Granted -> {
                                    writeSecureSettingsGranted = true
                                    Toast.makeText(context, context.getString(R.string.settings_permission_granted), Toast.LENGTH_SHORT).show()
                                }
                                is GrantResult.NeedsAppRestart -> {
                                    grantErrorDetail = context.getString(R.string.settings_needs_restart)
                                }
                                is GrantResult.Failed -> {
                                    grantErrorDetail = result.reason
                                }
                            }
                        }
                    },
                    enabled = !grantingPermission, modifier = Modifier.dpadFocus(RoundedCornerShape(50))
                ) {
                    if (grantingPermission) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_grant_permission))
                    }
                }
                if (grantErrorDetail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = grantErrorDetail ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        // HELP - frequently asked questions, in the same expandable format as the Privacy Policy.
        SectionCard(title = stringResource(R.string.settings_help_title)) {
            HelpItem(
                question = stringResource(R.string.settings_help_a11y_q),
                answer = stringResource(R.string.settings_help_a11y_a)
            )
            Spacer(modifier = Modifier.height(8.dp))
            HelpItem(
                question = stringResource(R.string.settings_help_random_q),
                answer = stringResource(R.string.settings_help_random_a)
            )
        }

        // DIAGNOSTICS - service events + the real process exit reasons (TV).
        SectionCard(title = stringResource(R.string.settings_diagnostic_title)) {
            Text(
                text = stringResource(R.string.settings_diagnostic_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { showDiagnostic = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_diagnostic_view))
            }
        }

        // BACKUP
        SectionCard(title = stringResource(R.string.settings_backup)) {
            Text(
                text = stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { exportLauncher.launch(backupFileName) },
                    modifier = Modifier.dpadFocus(RoundedCornerShape(50)).weight(1f)
                ) {
                    Text(stringResource(R.string.settings_export))
                }

                // Same component and colors as the Export button (it used to be an OutlinedButton,
                // outlined instead of filled - it visually clashed with the neighboring one of the
                // pair).
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "text/*")) },
                    modifier = Modifier.dpadFocus(RoundedCornerShape(50)).weight(1f)
                ) {
                    Text(stringResource(R.string.settings_import))
                }
            }
        }

        // SOBRE
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // It used to take the whole width of the Card (fillMaxWidth) - a single isolated button
            // stretched edge to edge clashes with the pattern of the rest of the app, where loose
            // buttons (not paired with another symmetric button) are centered at their natural
            // width. Box + Alignment.Center here instead of Modifier.fillMaxWidth() on the button
            // itself is what effectively centers it (a Button already centered but with
            // fillMaxWidth still looks "stretched", since the button itself fills the Card even if
            // its inner text is centered).
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = { showAboutScreen = true }, modifier = Modifier.dpadFocus(RoundedCornerShape(50))) {
                    Text(stringResource(R.string.settings_about_button))
                }
            }
        }
    }

    if (showDiagnostic) {
        var report by remember { mutableStateOf(DiagnosticLog.buildReport(context)) }
        BloodMoonDialog(
            title = stringResource(R.string.settings_diagnostic_title),
            onDismiss = { showDiagnostic = false }
        ) {
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_diagnostic_share))
            }
            // Limpar: apaga o registro e atualiza o texto do painel na hora.
            FilledTonalButton(
                onClick = {
                    DiagnosticLog.clear(context)
                    report = DiagnosticLog.buildReport(context)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_diagnostic_clear))
            }
        }
    }

    if (showAdbPanel) {
        BloodMoonDialog(
            title = stringResource(R.string.settings_adb_panel_title),
            onDismiss = { showAdbPanel = false },
            onConfirm = {
                val nowGranted = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                        PackageManager.PERMISSION_GRANTED
                writeSecureSettingsGranted = nowGranted
                if (nowGranted) viewModel.updateAppSettings(settings.copy(adbEnabled = true))
                showAdbPanel = false
                if (!nowGranted) {
                    Toast.makeText(context, context.getString(R.string.settings_adb_not_ready), Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text(
                text = stringResource(R.string.settings_adb_info_body),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = adbCommand,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("adb command", adbCommand))
                    Toast.makeText(context, context.getString(R.string.settings_command_copied), Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_copy_command))
            }
            FilledTonalButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, adbCommand)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_share_command))
            }
        }
    }

    if (showShizukuRequirementDialog) {
        ShizukuRequirementDialog(
            shizukuAvailable = shizukuAvailable,
            // Real bug of the "panel gets stuck, does not advance": step 3 (Grant) of the checklist
            // used a fixed isCompleted = false, never reflecting the real permission - so even if
            // the user had turned Shizuku on and enabled the app directly from the Shizuku app (a
            // flow that does not go through this app's result listener, since it only fires when
            // the grant comes from Shizuku.requestPermission() called by THIS app), the dialog had
            // no way of knowing everything was already ready. Passing the real shizukuHasPermission
            // is what fixes this.
            shizukuHasPermission = shizukuHasPermission,
            onClose = { granted ->
                showShizukuRequirementDialog = false
                recheckPrivilegedState()
                if (granted) {
                    viewModel.updateAppSettings(settings.copy(useShizuku = true))
                    Toast.makeText(context, context.getString(R.string.settings_shizuku_granted_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showImportDialog) {
        BloodMoonDialog(
            title = stringResource(R.string.settings_import_title),
            onDismiss = { showImportDialog = false }
        ) {
            Text(
                text = importError ?: "",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Requirements checklist styled after Canta's own ShizukuRequirementDialog
 * (github.com/samolego/Canta) - each step shows a check/pending status, plus a jump-to-action arrow
 * for whichever step is next. This is what was missing before: a toggle that just silently failed
 * with no explanation of what to actually do about it.
 */
@Composable
private fun ShizukuRequirementDialog(
    shizukuAvailable: Boolean,
    shizukuHasPermission: Boolean,
    onClose: (granted: Boolean) -> Unit
) {
    val context = LocalContext.current
    // While the dialog is open, it re-evaluates the real state periodically instead of relying only
    // on SettingsScreen's ON_RESUME (which only fires when the user leaves and returns to the app).
    // This was exactly what was missing: if the user enables Shizuku and authorizes Blood Moon
    // WITHOUT leaving the app entirely (e.g. via a shortcut/tile from Shizuku itself, or the binder
    // simply takes a few seconds to connect after "enabling" and the user comes back too quickly),
    // the binder may connect with the app still in the foreground - ON_RESUME never fires again in
    // that case, and the panel stayed "stuck" showing the old state forever. Canta (the original
    // inspiration for this dialog) solves this with native Shizuku SDK listeners wired to a global
    // state variable, always read on the spot - the polling here pursues the same result (the
    // reading is never stale by more than 1s) in a way that is simpler to reason about, without
    // introducing static state shared between screens.
    var liveShizukuAvailable by remember { mutableStateOf(shizukuAvailable) }
    var liveShizukuHasPermission by remember { mutableStateOf(shizukuHasPermission) }
    LaunchedEffect(Unit) {
        while (isActive) {
            liveShizukuAvailable = ShizukuHelper.isShizukuAvailable()
            liveShizukuHasPermission = ShizukuHelper.hasShizukuPermission()
            kotlinx.coroutines.delay(1000)
        }
    }

    val shizukuInstalled by remember(liveShizukuAvailable) {
        // Re-evaluated whenever liveShizukuAvailable changes instead of only once ever - a plain
        // remember{} with no key was one of the reasons this dialog could look "stuck" showing
        // step 1 as incomplete even after Shizuku was actually installed.
        mutableStateOf(runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0) }.isSuccess)
    }

    // All three steps done -> the dialog has nothing left to ask for. Auto-closing here (instead of
    // leaving the user staring at three green checkmarks with no obvious next action) is what
    // actually resolves "I go back to the app and the panel stays the same" for the common case:
    // granting the permission from inside the Shizuku app itself.
    LaunchedEffect(liveShizukuAvailable, liveShizukuHasPermission) {
        if (liveShizukuAvailable && liveShizukuHasPermission) {
            onClose(true)
        }
    }

    BloodMoonDialog(
        title = stringResource(R.string.settings_shizuku_required_title),
        onDismiss = { onClose(false) },
        onConfirm = { onClose(false) }
    ) {
        Text(
            text = stringResource(R.string.settings_shizuku_required_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        RequirementItem(
            text = stringResource(R.string.settings_shizuku_step_install),
            isCompleted = shizukuInstalled,
            onActionClick = {
                val url = if (Build.VERSION.SDK_INT >= 36) SHIZUKU_GITHUB_RELEASES_URL else SHIZUKU_PLAY_STORE_URL
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                runCatching { context.startActivity(intent) }
            }
        )
        RequirementItem(
            text = stringResource(R.string.settings_shizuku_step_start),
            isCompleted = liveShizukuAvailable,
            onActionClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
                if (intent != null) {
                    runCatching { context.startActivity(intent) }
                } else {
                    val storeIntent = Intent(Intent.ACTION_VIEW, SHIZUKU_PLAY_STORE_URL.toUri())
                    runCatching { context.startActivity(storeIntent) }
                }
            }
        )
        RequirementItem(
            text = stringResource(R.string.settings_shizuku_step_grant),
            isCompleted = liveShizukuHasPermission,
            enabled = liveShizukuAvailable,
            onActionClick = {
                ShizukuHelper.requestShizukuPermission(SHIZUKU_REQUEST_CODE)
            }
        )
    }
}

@Composable
private fun RequirementItem(
    text: String,
    isCompleted: Boolean,
    enabled: Boolean = true,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (!isCompleted && enabled) {
            FilledTonalIconButton(onClick = onActionClick, modifier = Modifier.dpadFocus(CircleShape).size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.action_ok),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
private const val SHIZUKU_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE_NAME"
private const val SHIZUKU_GITHUB_RELEASES_URL = "https://github.com/thedjchi/Shizuku/releases"

private const val REPO_URL = "https://github.com/uenzuwkxos/BloodMoon"

/**
 * Used to be AboutDialog (an AlertDialog) - it became its own full screen (explicit request), with
 * a BackHandler and a back button in the top bar, instead of onDismissRequest/tapping outside.
 * Called in place of SettingsScreen's normal content Column (see the conditional return there), not
 * floating over it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    var showLicensesScreen by remember { mutableStateOf(false) }
    // Expandable Privacy Policy card (tap to open/close the text).
    var privacyExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showLicensesScreen) {
        OpenSourceLicensesScreen(onBack = { showLicensesScreen = false })
        return
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.dpadFocus(CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = rememberAppIconPainter(),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.about_synopsis),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .dpadFocus(RoundedCornerShape(16.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.about_repo_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .dpadFocus(RoundedCornerShape(16.dp))
                    .clickable { privacyExpanded = !privacyExpanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.about_privacy_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (privacyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = privacyExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // New, explicit request: a button that opens the Open Source Licenses screen
            // (OpenSourceLicensesScreen), generated via AboutLibraries from the project's real
            // dependencies - see the full comment in build.gradle about the plugin.
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .dpadFocus(RoundedCornerShape(16.dp))
                    .clickable { showLicensesScreen = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.about_oss_licenses_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // Moved below the version (it used to be above, inside the synopsis/description text) -
            // explicit request.
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Lists the project's real dependencies/licenses (generated by the AboutLibraries plugin from
 * app/build.gradle) with its own list and dialog.
 *
 * Why not the ready-made LibrariesContainer: tapping a library did nothing (neither the dialog nor
 * the license link), and the semantics of onLibraryClick's return value change between versions of
 * the lib. By drawing the list here, a tap always opens a dialog with the name, version, license
 * and shortcuts to open the license text (official URL) and the library's website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val libs by produceLibraries(R.raw.aboutlibraries)
    val sorted = remember(libs) {
        libs?.libraries?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    var selected by remember { mutableStateOf<com.mikepenz.aboutlibraries.entity.Library?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_oss_licenses_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.dpadFocus(CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sorted.size) { index ->
                val library = sorted[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocus(RoundedCornerShape(16.dp))
                        .clickable { selected = library },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = library.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            library.artifactVersion?.takeIf { it.isNotBlank() }?.let { version ->
                                Text(
                                    text = version,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val licenseNames = library.licenses.joinToString(", ") { it.name }
                        if (licenseNames.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = licenseNames,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { library ->
        val context = LocalContext.current
        val licenses = library.licenses.toList()
        val website = library.website?.takeIf { it.isNotBlank() }
        val licenseUrl = licenses.firstNotNullOfOrNull { it.url?.takeIf { url -> url.isNotBlank() } }
        BloodMoonDialog(
            title = library.name,
            onDismiss = { selected = null }
        ) {
            library.artifactVersion?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            library.description?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Text(
                text = stringResource(R.string.about_oss_license_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (licenses.isEmpty()) {
                Text(
                    text = stringResource(R.string.about_oss_license_none),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                licenses.forEach { license ->
                    Text(
                        text = license.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (licenseUrl != null) {
                FilledTonalButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, licenseUrl.toUri())) } },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
                ) { Text(stringResource(R.string.about_oss_license_view)) }
            }
            if (website != null) {
                FilledTonalButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, website.toUri())) } },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp)).fillMaxWidth()
                ) { Text(stringResource(R.string.about_oss_license_website)) }
            }
        }
    }
}

/**
 * Crashed every single time before this fix: R.mipmap.ic_launcher resolves to an
 * <adaptive-icon> XML (background + foreground + monochrome layers, mipmap-anydpi-v26/
 * ic_launcher.xml), and Compose's painterResource() only understands VectorDrawables and
 * fully-rasterized PNG/JPG assets - handing it an adaptive icon throws
 * "IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported"
 * immediately, taking down the whole screen (and the whole app, since this dialog is reachable
 * from a single tap on "About"). This works around it by loading the Drawable the traditional
 * View-system way (ResourcesCompat.getDrawable, which *does* understand AdaptiveIconDrawable),
 * rasterizing it to a Bitmap ourselves, and wrapping that in a plain BitmapPainter - which
 * Compose has no trouble drawing regardless of what the original resource type was.
 */
@Composable
private fun rememberAppIconPainter(): Painter {
    val context = LocalContext.current
    return remember {
        val drawable = ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, context.theme)
        val size = 108 // adaptive icons are authored on a 108dp canvas; any reasonable fixed size works here
        // KTX createBitmap - androidx.core:core-ktx:1.12.0 (this project's version) already has
        // it, so the manual android.graphics.Bitmap.createBitmap(...) call isn't needed here.
        val bitmap = createBitmap(size, size)
        if (drawable != null) {
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        BitmapPainter(bitmap.asImageBitmap())
    }
}

@Composable
private fun CenteredLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Neutral colors (outline/onSurfaceVariant) for the read-only OutlinedTextFields used as the
 * "trigger" of the Theme and Language dropdowns. Without this, Material3 uses the theme's `primary`
 * (the brand red, #C41E3A) to color the field's border/label/icon when it is focused - but here the
 * field NEVER visibly loses focus (it opens a menu, it does not edit text), so in practice it was
 * always that strong red tone, even without really being "in use". Explicit request: use a neutral
 * color, like the rest of the dropdown (default outline), instead of the brand red here.
 */
@Composable
private fun dropdownTriggerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        // The D-pad focus ring wraps the whole row when the Switch is focused.
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocus(RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/**
 * Controls how long FilterOverlayService's fade in/out animation takes (150ms-3000ms), in
 * 150ms steps, via a Stepper (see util/Stepper.kt for why a Stepper instead of a Slider).
 */
@Composable
private fun FadeDurationSlider(
    valueMs: Int,
    onValueChange: (Int) -> Unit
) {
    LabeledStepper(
        label = stringResource(R.string.settings_fade_duration),
        description = stringResource(R.string.settings_fade_duration_desc),
        value = valueMs,
        valueRange = 150..3000,
        step = 150,
        onChange = onValueChange,
        valueLabel = { stringResource(R.string.settings_fade_duration_value, it / 1000f) }
    )
}

/** Question that expands to show the answer - same style as the Privacy Policy card. */
@Composable
private fun HelpItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocus(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = answer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
