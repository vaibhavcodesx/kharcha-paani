package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

object KpMotion {
    /** Material's emphasized curves: things arriving decelerate, things leaving accelerate. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Moving between top-level tabs: a short slide in the direction of travel with a cross-fade, so
     * going right feels like moving right. [forward] is true when the new tab is to the right.
     */
    fun tabTransition(forward: Boolean): ContentTransform {
        val direction = if (forward) 1 else -1
        return (
            slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { direction * it / 10 } +
                fadeIn(tween(220, delayMillis = 60))
            ) togetherWith (
            slideOutHorizontally(tween(180, easing = EmphasizedAccelerate)) { -direction * it / 10 } +
                fadeOut(tween(120))
            )
    }
}
