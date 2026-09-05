package com.smartisan.music.ui.components

import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos

/** The calibrated ViewPropertyAnimator/AnimatorSet default used for tab and edit motion. */
internal val SmartisanEaseInOut = Easing { fraction ->
    ((cos((fraction + 1) * PI) / 2.0) + .5).toFloat()
}
