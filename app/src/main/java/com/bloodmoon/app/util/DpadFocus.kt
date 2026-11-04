package com.bloodmoon.app.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/**
 * D-pad focus indicator (TV remote): when the element - or anything inside it - gets focus, a 3 dp
 * ring in the accent color and a translucent fill appear, with a short transition. Outside focus it
 * draws nothing (and takes no space).
 *
 * Place it BEFORE (higher up the Modifier chain) the clickable/button/row that receives focus:
 * onFocusChanged only sees the focus of what comes after it. Since it uses hasFocus, it also works
 * for containers (e.g. the whole row when the Switch inside it is focused).
 *
 * The ring is drawn with border() and the fill with drawBehind(): neither changes the layout, so
 * nothing "jumps" while navigating.
 */
fun Modifier.dpadFocus(shape: Shape = RoundedCornerShape(16.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val strength by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "dpadFocus"
    )
    val accent = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.hasFocus }
        .drawBehind {
            if (strength > 0f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = accent.copy(alpha = 0.16f * strength)
                )
            }
        }
        .border(
            border = BorderStroke(3.dp, accent.copy(alpha = strength)),
            shape = shape
        )
}
