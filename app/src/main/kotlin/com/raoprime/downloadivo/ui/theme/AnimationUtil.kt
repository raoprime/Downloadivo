package com.raoprime.downloadivo.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Spring-loaded physical bounce modifier for buttons, cards, chips, and icons.
 * Mimics high-end iOS/Apple and YouTube tactile micro-interactions with physical spring feedback.
 */
@Composable
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    hapticFeedback: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncy_scale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                if (hapticFeedback) {
                    HapticUtil.lightClick(context)
                }
                onClick()
            }
        )
}

/**
 * Spring-loaded scaling effect for interactive elements that manage their own clicks (e.g. Surface).
 */
@Composable
fun Modifier.bouncyPress(
    isPressed: Boolean,
    scaleDown: Float = 0.94f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncy_press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
