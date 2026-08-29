package dev.stan.yotsuba.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * The [SharedTransitionScope] of the enclosing `SharedTransitionLayout` (the NavHost's),
 * or null when there is none: previews, tests, and any screen composed outside the shell.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The [AnimatedVisibilityScope] of the nearest transition: a NavHost destination, or an
 * `AnimatedVisibility` a screen puts up around an overlay. Null when there is none.
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a piece of media as one end of a thumbnail-to-viewer transition. Both ends use the
 * same [key] (the media's URL or file path) and the shared-bounds overlay morphs one into
 * the other while the destinations cross-fade.
 *
 * Does nothing when either scope is absent or the user has turned animations off, so the
 * same composable renders identically in previews, tests and reduced-motion setups.
 */
@Composable
fun Modifier.sharedMedia(key: String, shape: Shape = RectangleShape): Modifier {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalAnimatedVisibilityScope.current
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (reducedMotion) return this
    return sharedMedia(key, shared, visibility, shape)
}

/**
 * The scope-explicit form of [sharedMedia]; identity when either scope is null. Split out
 * so the no-op contract can be checked on the JVM without composing anything.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMedia(
    key: String,
    shared: SharedTransitionScope?,
    visibility: AnimatedVisibilityScope?,
    shape: Shape = RectangleShape,
): Modifier {
    if (shared == null || visibility == null) return this
    return with(shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit),
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
