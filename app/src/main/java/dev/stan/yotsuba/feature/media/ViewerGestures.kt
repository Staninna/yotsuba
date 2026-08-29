package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Swipe distance that commits a horizontal navigation, in either viewer. */
private val SWIPE_COMMIT = 56.dp

/** How much more sideways than vertical a drag must be before it counts as a swipe. */
private const val HORIZONTAL_BIAS = 1.5f

/**
 * Horizontal navigation for the viewer, measured on the [PointerEventPass.Final] pass:
 * whatever movement no child claimed is ours.
 *
 * A plain `detectHorizontalDragGestures` on the parent loses this race. Compose offers
 * pointer events to children first, so the pager's slop detection or telephoto's pan can
 * consume the very event that would have crossed the threshold, and the swipe silently
 * does nothing — which is exactly the "sometimes it just doesn't open" the old one had.
 * Reading the leftovers instead means a drag something else wanted stays theirs, and one
 * nobody wanted always reaches us, including from on top of a reply panel that covers the
 * feed completely.
 */
suspend fun PointerInputScope.detectViewerSwipe(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val commit = SWIPE_COMMIT.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            // A second finger means a pinch; nothing about that is a swipe.
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull() ?: break
            if (!change.isConsumed) {
                dx += change.positionChange().x
                dy += change.positionChange().y
            }
            if (!change.pressed) break
        }
        // Diagonal drags belong to the vertical pager; only a decisive sideways one counts.
        if (abs(dx) < commit || abs(dx) < abs(dy) * HORIZONTAL_BIAS) return@awaitEachGesture
        if (dx < 0) onSwipeLeft() else onSwipeRight()
    }
}

/**
 * Direction lock for the vertical pager, decided on the [PointerEventPass.Initial] pass —
 * the only pass that runs before the pager's own slop detection.
 *
 * The pager only ever measures the vertical component of a drag, so a sideways swipe with
 * any downward drift in it crosses the pager's slop and pages the feed, which is both the
 * wrong thing and the reason the sideways swipe then went nowhere. Once a drag is clearly
 * horizontal this reports it, and the pager is switched off for the rest of that gesture.
 *
 * Nothing is consumed here. Stealing the events would take them from the scrub bar and
 * from panning a zoomed image too, and those are children with a better claim; disabling
 * the pager is enough, and only the pager is affected.
 */
suspend fun PointerInputScope.lockPagerOnHorizontalIntent(onLock: () -> Unit, onRelease: () -> Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dx = 0f
        var dy = 0f
        var decided = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                // A second finger is a pinch. Leave the pager alone and stop deciding.
                if (event.changes.size > 1) {
                    decided = true
                } else {
                    dx += change.positionChange().x
                    dy += change.positionChange().y
                }
                if (!decided && abs(dx) > slop && abs(dx) > abs(dy) * HORIZONTAL_BIAS) {
                    decided = true
                    onLock()
                }
                if (!change.pressed) break
            }
        } finally {
            onRelease()
        }
    }
}
