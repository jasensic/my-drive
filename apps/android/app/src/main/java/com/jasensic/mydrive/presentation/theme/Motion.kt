package com.jasensic.mydrive.presentation.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * One set of motion values so every transition feels the same: things move with springs
 * (they have weight), and fades are short. Nothing animates for longer than ~400 ms.
 */
object Motion {
    const val Fast = 150
    const val Medium = 260
    const val Slow = 380

    /** Default for position and size changes: a little overshoot, settles quickly. */
    fun <T> spring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)

    /** Taps and toggles: no overshoot. */
    fun <T> snap(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

    /** Sheets and full-screen covers. */
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 320f)

    fun <T> fade(): FiniteAnimationSpec<T> = tween(Medium)
}

/** Shrinks slightly while pressed and springs back, like an iOS button. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.snap(), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Same press feedback for places that have no interaction source of their own. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Light haptics tied to what happens on screen. Falls back to a plain tick on older Android. */
class Haptics(private val view: View) {
    /** A tab or a segment changed. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A row joined or left a selection. */
    fun select() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Long press recognised, a sheet opened. */
    fun lift() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** An action finished successfully. */
    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
