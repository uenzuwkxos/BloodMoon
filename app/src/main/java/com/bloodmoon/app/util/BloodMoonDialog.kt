package com.bloodmoon.app.util

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloodmoon.app.R
import com.bloodmoon.app.util.dpadFocus
import com.bloodmoon.app.ui.theme.dialogSurfaceColor

/**
 * Standard dialog for the WHOLE app. A single template, so no panel clashes with the others:
 *
 * - centered title at the top; - centered content (extra buttons inside it must be filled, never
 * plain text); - a single "Confirm" button, filled and centered - no "Cancel". If there is nothing
 * to confirm ([onConfirm] = close), it just closes the panel; - leaving without confirming =
 * Back/Exit on the remote or tapping outside the panel ([onDismiss]).
 *
 * Back/Exit on the remote (TV): before, only the Confirm button closed the panels. Now Back is
 * handled through three independent paths, because depending on the device/Android version any one
 * of them may not arrive: (1) the Dialog's native dispatcher (onDismissRequest), (2) a BackHandler
 * inside the dialog itself and (3) the Back/Esc/B key intercepted in Compose - the latter requires
 * focus inside the panel, which is why the Confirm button already opens focused (also the standard
 * for dialogs on Android TV). Closing more than once is harmless: every onDismiss is idempotent.
 *
 * Focus: the whole panel is ONE focus group that blocks leaving (exit = Cancel) - the D-pad
 * navigates freely between the content and the button, but never leaks outside the panel (it was
 * the problem reported before, when each AlertDialog slot had its own trap and one prevented going
 * from the content to Confirm).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BloodMoonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = onDismiss,
    confirmText: String = stringResource(R.string.action_confirm),
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .onPreviewKeyEvent { event ->
                    val isExitKey = event.key == Key.Back || event.key == Key.Escape || event.key == Key.ButtonB
                    if (!isExitKey) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.dpadFocus(RoundedCornerShape(14.dp))
                        .widthIn(min = 160.dp)
                        .focusRequester(confirmFocus)
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}
