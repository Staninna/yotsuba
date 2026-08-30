package dev.stan.yotsuba.core.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.domain.model.Bookmark

/**
 * What one widget row shows. Pure data so the ordering can be tested on the JVM and the
 * Glance composable stays a dumb renderer.
 */
data class WidgetRow(
    val board: String,
    val threadNo: Long,
    val title: String,
    val unread: Int,
    val dead: Boolean,
)

/**
 * Unread threads first (most unread on top), then pinned, then everything else by last
 * activity. Ties fall back to the bookmark time so the order is stable between refreshes.
 */
fun orderForWidget(bookmarks: List<Bookmark>): List<WidgetRow> =
    bookmarks
        .sortedWith(
            compareByDescending<Bookmark> { it.unread > 0 }
                .thenByDescending { it.unread }
                .thenByDescending { it.pinned }
                .thenByDescending { it.lastActivityAt ?: it.bookmarkedAt }
                .thenByDescending { it.bookmarkedAt },
        )
        .map { WidgetRow(it.board, it.threadNo, it.displayTitle, it.unread, it.isDead) }

/** Rows visible at a given widget size; `null` means "all of them, scrolling". */
fun rowLimitFor(size: WidgetSizeBucket): Int? = when (size) {
    WidgetSizeBucket.SMALL -> 3
    WidgetSizeBucket.MEDIUM -> 6
    WidgetSizeBucket.LARGE -> null
}

enum class WidgetSizeBucket { SMALL, MEDIUM, LARGE }

/** The responsive breakpoints the widget declares; [sizeBucket] reads a live size against them. */
val SMALL_WIDGET = DpSize(110.dp, 110.dp)
val MEDIUM_WIDGET = DpSize(250.dp, 110.dp)
val LARGE_WIDGET = DpSize(250.dp, 250.dp)

fun sizeBucket(size: DpSize): WidgetSizeBucket = when {
    size.height >= LARGE_WIDGET.height -> WidgetSizeBucket.LARGE
    size.width >= MEDIUM_WIDGET.width -> WidgetSizeBucket.MEDIUM
    else -> WidgetSizeBucket.SMALL
}
