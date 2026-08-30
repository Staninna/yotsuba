package dev.stan.yotsuba.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion

/**
 * The Home board tabs: a scrollable row that looks like a `ScrollableTabRow` but lets a tab
 * be picked up with a long press and dragged to a new slot. The neighbours slide out of the
 * way, the row scrolls itself when the tab nears an edge, and letting go calls [onMove].
 * [trailing] (the "+" tab) sits after the boards and takes no part in the drag.
 */
@Composable
fun ReorderableTabRow(
    boards: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDragState: (dragging: Boolean, overRemove: Boolean) -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val moveLeft = stringResource(R.string.home_move_left)
    val moveRight = stringResource(R.string.home_move_right)
    val removeLabel = stringResource(R.string.home_remove_zone)
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // Measured tab widths keyed by board, so a width follows its tab through a reorder or
    // removal; [widths] is the same in tab order, read live wherever it is used.
    val measured = remember { mutableStateMapOf<String, Int>() }
    val widths by remember(boards) { derivedStateOf { boards.map { measured[it] ?: 0 } } }

    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val from = dragging
    var dragY by remember { mutableFloatStateOf(0f) }
    val removeThreshold = with(density) { 32.dp.toPx() }
    val overRemove = from != null && dragY > removeThreshold
    LaunchedEffect(from != null, overRemove) {
        if (from != null && overRemove) haptics.reject()
        onDragState(from != null, overRemove)
    }
    var viewportWidth by remember { mutableIntStateOf(0) }
    // A favourites write landing mid-drag restarts the gesture without onDragCancel, so the
    // lifted index would otherwise outlive the tab it pointed at.
    LaunchedEffect(boards) {
        dragging = null
        dragOffset = 0f
        dragY = 0f
    }
    val target = if (from != null && from in widths.indices) dropTarget(from, dragOffset, widths) else -1

    // Keep the dragged tab under the finger while the row scrolls beneath it.
    LaunchedEffect(from) {
        if (from == null) return@LaunchedEffect
        val edge = with(density) { 48.dp.toPx() }
        val step = with(density) { 6.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val start = widths.take(from).sum() + dragOffset - scrollState.value
            val end = start + (widths.getOrNull(from) ?: 0)
            val delta = when {
                start < edge -> -step
                end > viewportWidth - edge -> step
                else -> 0f
            }
            if (delta != 0f) dragOffset += scrollState.scrollBy(delta)
        }
    }

    // Selecting a tab scrolls it into view, as ScrollableTabRow does.
    LaunchedEffect(selectedIndex, widths) {
        if (from != null || selectedIndex !in widths.indices || viewportWidth == 0) return@LaunchedEffect
        val start = widths.take(selectedIndex).sum()
        val centred = start + widths[selectedIndex] / 2 - viewportWidth / 2
        scrollState.animateScrollTo(centred.coerceAtLeast(0))
    }

    val indicatorStart by animateFloatAsState(
        targetValue = widths.take(selectedIndex.coerceIn(0, widths.size)).sum().toFloat(),
        animationSpec = rememberMotionSpec(motion.short),
        label = "indicatorStart",
    )
    val indicatorWidth by animateFloatAsState(
        targetValue = widths.getOrNull(selectedIndex)?.toFloat() ?: 0f,
        animationSpec = rememberMotionSpec(motion.short),
        label = "indicatorWidth",
    )

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scrollState, enabled = from == null),
    ) {
        Row(Modifier.height(48.dp)) {
            boards.forEachIndexed { index, board ->
                val lifted = index == from
                val shift = if (from == null) 0 else shiftFor(index, from, target) * (widths.getOrNull(from) ?: 0)
                val slide by animateFloatAsState(
                    targetValue = shift.toFloat(),
                    animationSpec = rememberMotionSpec(motion.short),
                    label = "slide",
                )
                val scale by animateFloatAsState(
                    targetValue = if (lifted) 1.08f else 1f,
                    animationSpec = rememberMotionSpec(motion.short),
                    label = "scale",
                )
                BoardTab(
                    board = board,
                    selected = index == selectedIndex,
                    lifted = lifted,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .zIndex(if (lifted) 1f else 0f)
                        .semantics {
                            // The drag is out of reach for a screen reader; offer the same moves as actions.
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveLeft) { onMove(index, index - 1); true })
                                if (index < boards.lastIndex) {
                                    add(CustomAccessibilityAction(moveRight) { onMove(index, index + 1); true })
                                }
                                add(CustomAccessibilityAction(removeLabel) { onRemove(index); true })
                            }
                        }
                        .onSizeChanged { measured[board] = it.width }
                        .graphicsLayer {
                            translationX = if (lifted) dragOffset else slide
                            translationY = if (lifted) dragY.coerceAtLeast(0f) else 0f
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (lifted) 8.dp.toPx() else 0f
                            shape = RoundedCornerShape(8.dp)
                            clip = lifted
                        }
                        .pointerInput(boards) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.longPress()
                                    dragOffset = 0f
                                    dragY = 0f
                                    dragging = index
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.x
                                    dragY += amount.y
                                },
                                onDragEnd = {
                                    val f = dragging
                                    if (f != null) {
                                        if (dragY > removeThreshold) {
                                            haptics.confirm()
                                            onRemove(f)
                                        } else {
                                            val t = dropTarget(f, dragOffset, widths)
                                            if (t != f) onMove(f, t)
                                        }
                                    }
                                    dragging = null
                                    dragOffset = 0f
                                    dragY = 0f
                                },
                                onDragCancel = {
                                    dragging = null
                                    dragOffset = 0f
                                    dragY = 0f
                                },
                            )
                        },
                )
            }
            trailing()
        }
        HorizontalDivider(Modifier.align(Alignment.BottomStart))
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer { translationX = indicatorStart }
                .width(with(density) { indicatorWidth.toDp() })
                .height(3.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun BoardTab(
    board: String,
    selected: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .then(if (lifted) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "/$board/",
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
