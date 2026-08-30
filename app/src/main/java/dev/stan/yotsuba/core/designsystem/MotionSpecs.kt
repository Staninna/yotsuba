package dev.stan.yotsuba.core.designsystem

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import dev.stan.yotsuba.core.designsystem.token.LocalMotion

/*
 * The app's animation vocabulary, built from the `Motion` tokens. Everything here
 * collapses to its end state under reduced motion, so a call site never checks that
 * itself.
 */

/** A tween over [durationMs], or a snap when animations are off. */
@Composable
fun <T> rememberMotionSpec(durationMs: Int): FiniteAnimationSpec<T> =
    if (rememberReducedMotion()) snap() else tween(durationMs)

/** Fade-and-place for a row that can be inserted, removed or moved in a `LazyColumn`. */
@Composable
fun LazyItemScope.animatedListItem(): Modifier {
    val motion = LocalMotion.current
    if (rememberReducedMotion()) return Modifier
    return Modifier.animateItem(
        fadeInSpec = tween(motion.medium),
        placementSpec = tween(motion.medium),
        fadeOutSpec = tween(motion.short),
    )
}

/** [animatedListItem] for a `LazyVerticalGrid` cell. */
@Composable
fun LazyGridItemScope.animatedGridItem(): Modifier {
    val motion = LocalMotion.current
    if (rememberReducedMotion()) return Modifier
    return Modifier.animateItem(
        fadeInSpec = tween(motion.medium),
        placementSpec = tween(motion.medium),
        fadeOutSpec = tween(motion.short),
    )
}

/** Enter for a floating control (a FAB, a divider) appearing over content. */
@Composable
fun motionEnter(): EnterTransition {
    val motion = LocalMotion.current
    if (rememberReducedMotion()) return EnterTransition.None
    return fadeIn(tween(motion.medium)) + scaleIn(tween(motion.medium), initialScale = 0.8f)
}

/** Exit matching [motionEnter]. */
@Composable
fun motionExit(): ExitTransition {
    val motion = LocalMotion.current
    if (rememberReducedMotion()) return ExitTransition.None
    return fadeOut(tween(motion.short)) + scaleOut(tween(motion.short), targetScale = 0.8f)
}

/**
 * A counter changing value: the new number slides in from the side it grew towards.
 * Pass as `transitionSpec` to an `AnimatedContent<Int>`.
 */
@Composable
fun rememberCountTransition(): AnimatedContentTransitionScope<Int>.() -> ContentTransform {
    val motion = LocalMotion.current
    val reduced = rememberReducedMotion()
    return {
        if (reduced) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            val up = targetState > initialState
            (slideInVertically(tween(motion.short)) { if (up) it else -it } + fadeIn(tween(motion.short)))
                .togetherWith(
                    slideOutVertically(tween(motion.short)) { if (up) -it else it } + fadeOut(tween(motion.short)),
                )
        }
    }
}

/**
 * The NavHost's four transitions. A tab switch composes both screens at once, so it gets
 * a short fade and no slide; the push/pop slide is for screens that stack. Plain
 * functions rather than composables so the NavHost lambdas, which run in the transition
 * scope, can call them.
 */
class NavTransitions internal constructor(
    private val short: Int,
    private val medium: Int,
    private val reduced: Boolean,
) {
    fun enter(tabSwitch: Boolean): EnterTransition = when {
        reduced -> EnterTransition.None
        tabSwitch -> fadeIn(tween(short))
        else -> fadeIn(tween(medium)) + slideInHorizontally(tween<IntOffset>(medium)) { it / 8 }
    }

    fun exit(): ExitTransition = if (reduced) ExitTransition.None else fadeOut(tween(short))

    fun popEnter(tabSwitch: Boolean): EnterTransition =
        if (reduced) EnterTransition.None else fadeIn(tween(if (tabSwitch) short else medium))

    fun popExit(tabSwitch: Boolean): ExitTransition = when {
        reduced -> ExitTransition.None
        tabSwitch -> fadeOut(tween(short))
        else -> fadeOut(tween(medium)) + slideOutHorizontally(tween<IntOffset>(medium)) { it / 8 }
    }
}

@Composable
fun rememberNavTransitions(): NavTransitions {
    val motion = LocalMotion.current
    return NavTransitions(motion.short, motion.medium, rememberReducedMotion())
}
