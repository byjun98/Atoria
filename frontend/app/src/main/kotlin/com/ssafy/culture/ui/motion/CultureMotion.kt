package com.ssafy.culture.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

object CultureMotion {
    const val ScreenTransitionMillis: Int = 260
    const val QuickTransitionMillis: Int = 170
    const val PressedScale: Float = 0.975f
    const val SubtlePressedScale: Float = 0.985f

    fun pressSpring(): AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> softSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

fun Modifier.tossClickable(
    enabled: Boolean = true,
    role: Role? = null,
    pressedScale: Float = CultureMotion.PressedScale,
    showIndication: Boolean = false,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    val isPressed: Boolean by interactionSource.collectIsPressedAsState()
    val scale: Float by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = CultureMotion.pressSpring(),
        label = "toss_click_scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = if (showIndication) LocalIndication.current else null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}
