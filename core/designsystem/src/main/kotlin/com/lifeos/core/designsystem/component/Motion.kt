package com.lifeos.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One motion vocabulary for the whole app (§7 motion). Everything is a fade at
 * heart: content that changes cross-fades, content that appears fades in with a
 * barely-there scale, and boxes that resize animate instead of jumping.
 *
 * Durations are deliberately short - the app should feel calm, not slow.
 */
object LifeMotion {
    /** Leaving the screen. */
    const val EXIT_MS = 260

    /** Arriving: long enough to read as a real fade, short enough to stay snappy. */
    const val ENTER_MS = 420

    /** Cross-fading one piece of content for another. */
    const val SWAP_MS = 420

    /** Layout growing or shrinking. */
    const val RESIZE_MS = 380

    fun <T> enterSpec() = tween<T>(durationMillis = ENTER_MS, easing = LinearOutSlowInEasing)
    fun <T> exitSpec() = tween<T>(durationMillis = EXIT_MS, easing = FastOutSlowInEasing)
    fun <T> swapSpec() = tween<T>(durationMillis = SWAP_MS, easing = FastOutSlowInEasing)
}

/** Cross-fades whenever [targetState] changes. The default for swapped content. */
@Composable
fun <T> FadeThrough(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "fade-through",
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = LifeMotion.swapSpec(),
        label = label,
        content = content,
    )
}

/** Fades a block in and out, with a hint of scale so it does not pop. */
@Composable
fun FadeVisible(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String = "fade-visible",
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(LifeMotion.enterSpec()),
        exit = fadeOut(LifeMotion.exitSpec()),
        label = label,
        content = { content() },
    )
}

/** Animates height/width changes instead of snapping to the new size. */
fun Modifier.smoothSize(): Modifier = this.animateContentSize(
    animationSpec = tween(durationMillis = LifeMotion.RESIZE_MS, easing = FastOutSlowInEasing),
)
