package com.bloodmoon.app.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * The app's integer value controls: ALWAYS a slider (Slider). The old -/+ buttons (with repeat on
 * hold) were removed, along with the "Use sliders" option - sliders work well on phones
 * (touch/drag) and on TV (D-pad).
 */

/**
 * Integer value control without its own label (used inside dialogs and below a Switch): full-width
 * bar with the value beside it.
 */
@Composable
fun Stepper(
    value: Int,
    valueRange: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
    valueLabel: @Composable (Int) -> String = { "$it" }
) {
    StepSlider(
        value = value,
        valueRange = valueRange,
        step = step,
        onChange = onChange,
        valueLabel = valueLabel,
        showValue = true,
        onLiveChange = {},
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Integer value control with a label: label and value on the top row, full-width bar below (a bar
 * squeezed beside the label would be too short on narrow screens).
 */
@Composable
fun LabeledStepper(
    label: String,
    value: Int,
    valueRange: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
    valueLabel: @Composable (Int) -> String = { "$it" },
    description: String? = null,
    modifier: Modifier = Modifier
) {
    var shownValue by remember(value) { mutableIntStateOf(value) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(shownValue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StepSlider(
            value = value,
            valueRange = valueRange,
            step = step,
            onChange = onChange,
            valueLabel = valueLabel,
            showValue = false,
            onLiveChange = { shownValue = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Slider that respects [step] and only writes the value on release (onValueChangeFinished), so it
 * does not write to the DataStore on every dragged pixel.
 *
 * D-pad (TV): Left/Right adjust one step per KeyDown (Android's native key repeat already generates
 * repeated KeyDowns on hold). Up/Down do NOT touch the bar - Material's Slider treats all 4 arrows
 * as increase/decrease, which trapped navigation in the app on TV -; they move focus to the
 * previous/next component, and are consumed before reaching the Slider. The left/right keys are
 * consumed even at the extremes so focus does not leak.
 */
@Composable
private fun StepSlider(
    value: Int,
    valueRange: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    valueLabel: @Composable (Int) -> String,
    showValue: Boolean,
    onLiveChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = valueRange.first
    val safeStep = step.coerceAtLeast(1)
    val intervals = ((valueRange.last - first) / safeStep).coerceAtLeast(1)
    val last = first + intervals * safeStep

    var live by remember(value) { mutableIntStateOf(value.coerceIn(first, last)) }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier.dpadFocus(RoundedCornerShape(14.dp)).onPreviewKeyEvent { event ->
            if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                if (event.type == KeyEventType.KeyDown) {
                    focusManager.moveFocus(
                        if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down
                    )
                }
                return@onPreviewKeyEvent true
            }
            val direction = when (event.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> 0
            }
            if (direction == 0) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) {
                val next = (live + direction * safeStep).coerceIn(first, last)
                if (next != live) {
                    live = next
                    onLiveChange(next)
                    onChange(next)
                }
            }
            true
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Slider(
            value = (live - first) / safeStep.toFloat(),
            onValueChange = { raw ->
                val index = raw.roundToInt().coerceIn(0, intervals)
                val next = first + index * safeStep
                if (next != live) {
                    live = next
                    onLiveChange(next)
                }
            },
            onValueChangeFinished = { if (live != value) onChange(live) },
            valueRange = 0f..intervals.toFloat(),
            steps = (intervals - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f)
        )
        if (showValue) {
            Text(
                text = valueLabel(live),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp)
            )
        }
    }
}
