package dev.stan.yotsuba.feature.bookmarks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Swiping a bookmark away and undoing it, on the JVM, because the row that undo puts back
 * once deleted itself as it composed and the bookmark was lost for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookmarksListSwipeTest {
    @get:Rule val composeRule = createComposeRule()

    private fun bookmark(threadNo: Long, subject: String, postNos: List<Long>) = Bookmark(
        board = "g",
        threadNo = threadNo,
        subject = subject,
        opExcerpt = "$subject body",
        thumbnailUrl = null,
        replyCount = postNos.size,
        imageCount = 0,
        bookmarkedAt = threadNo,
        lastCheckedAt = null,
        state = BookmarkState.ALIVE,
        readUpTo = 1L,
        postNos = postNos,
        lastActivityAt = threadNo,
    )

    private val bravo = bookmark(2_001L, "Bravo bookmark", listOf(10L))
    private val rows = mutableStateListOf(
        bookmark(1_001L, "Alpha bookmark", emptyList()),
        bravo,
        bookmark(3_001L, "Charlie bookmark", listOf(10L, 11L)),
    )

    @Test
    fun undoAfterASwipe_putsTheBookmarkBack_andKeepsIt() {
        composeRule.setContent {
            YotsubaTheme(reduceMotion = true) {
                val snackbar = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    BookmarksList(
                        state = BookmarksUiState(bookmarks = rows.toList(), loaded = true),
                        snapshotResult = null,
                        onSnapshotResultShown = {},
                        onScreenVisible = {},
                        onRefreshAll = {},
                        onRemove = { removed -> rows.removeAll { it.threadNo == removed.threadNo } },
                        onUndoRemove = { rows.add(it) },
                        onTogglePinned = {},
                        onSnapshot = {},
                        onOpenThread = { _, _ -> },
                        snackbar = snackbar,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Bravo bookmark").performTouchInput { swipeLeft() }
        composeRule.waitUntil { rows.none { it.threadNo == bravo.threadNo } }
        composeRule.onNodeWithText("Bravo bookmark").assertDoesNotExist()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil { rows.any { it.threadNo == bravo.threadNo } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bravo bookmark").assertExists()
        assertEquals(3, rows.size)
    }
}
