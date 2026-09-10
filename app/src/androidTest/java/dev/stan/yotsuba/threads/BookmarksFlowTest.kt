package dev.stan.yotsuba.threads

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.BookmarkSortOrder
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openThreadsTab
import dev.stan.yotsuba.shell.assertAbove
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Watched segment. The three live rows are built so that each sort order puts them in a
 * different order: unread counts, last activity and bookmark time all disagree.
 */
@HiltAndroidTest
class BookmarksFlowTest : FlowTest() {

    private val watched = TestSeed.bookmark(
        threadNo = TestSeed.THREAD_NO,
        subject = TestSeed.THREAD_SUBJECT,
        bookmarkedAt = 3_000L,
    ).copy(lastActivityAt = 1_000L)
    private val bravo = TestSeed.bookmark(
        threadNo = 2_001L,
        subject = "Bravo bookmark",
        opExcerpt = "bravo body",
        readUpTo = 1L,
        postNos = listOf(10L),
        bookmarkedAt = 2_000L,
    ).copy(lastActivityAt = 3_000L)
    private val charlie = TestSeed.bookmark(
        threadNo = 3_001L,
        subject = "Charlie bookmark",
        opExcerpt = "charlie body",
        readUpTo = 1L,
        postNos = listOf(10L, 11L),
        bookmarkedAt = 1_000L,
    ).copy(lastActivityAt = 2_000L)
    private val pruned = TestSeed.bookmark(
        threadNo = 4_001L,
        subject = "Pruned bookmark",
        opExcerpt = "pruned body",
        state = BookmarkState.DEAD,
        bookmarkedAt = 500L,
    )
    private val archived = TestSeed.bookmark(
        threadNo = 5_001L,
        subject = "Archived bookmark",
        opExcerpt = "archived body",
        state = BookmarkState.ARCHIVED,
        bookmarkedAt = 400L,
    )

    override fun seed() {
        fakes.bookmarks.seed(watched, bravo, charlie)
    }

    private val rows get() = fakes.bookmarks.state.value

    private fun openWatched() {
        composeRule.openThreadsTab()
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    private fun openSheetFor(title: String) {
        composeRule.nodeWithText(title).performTouchInput { longClick() }
        composeRule.waitForText("Snapshot to vault")
    }

    @Test
    fun rows_showUnreadPillsPinAndStateBadges() {
        fakes.bookmarks.seed(watched.copy(pinned = true), bravo, charlie, archived, pruned)
        openWatched()
        composeRule.waitForText("1 unread")
        composeRule.waitForText("2 unread")
        composeRule.waitForContentDescription("Pinned")
        composeRule.waitForText("Archived", substring = false)
        composeRule.waitForText("Pruned", substring = false)
        // A pinned row leads whatever the sort order says.
        composeRule.assertAbove(TestSeed.THREAD_SUBJECT, "Charlie bookmark")
    }

    @Test
    fun sortMenu_reordersRows_andPersistsTheChoice() {
        openWatched()
        // The default is unread first: two unread, then one, then none.
        composeRule.assertAbove("Charlie bookmark", "Bravo bookmark")
        composeRule.assertAbove("Bravo bookmark", TestSeed.THREAD_SUBJECT)

        selectSort("Last activity", BookmarkSortOrder.LAST_ACTIVITY)
        composeRule.assertAbove("Bravo bookmark", "Charlie bookmark")
        composeRule.assertAbove("Charlie bookmark", TestSeed.THREAD_SUBJECT)

        selectSort("Bookmarked", BookmarkSortOrder.BOOKMARKED)
        composeRule.assertAbove(TestSeed.THREAD_SUBJECT, "Bravo bookmark")
        composeRule.assertAbove("Bravo bookmark", "Charlie bookmark")

        selectSort("Unread first", BookmarkSortOrder.UNREAD_FIRST)
        composeRule.assertAbove("Charlie bookmark", TestSeed.THREAD_SUBJECT)
    }

    private fun selectSort(label: String, expected: BookmarkSortOrder) {
        composeRule.tapIcon("Bookmark options")
        composeRule.tap(label, substring = false)
        composeRule.waitUntilTrue { fakes.settings.state.value.bookmarkSortOrder == expected }
    }

    @Test
    fun removePruned_isOfferedOnlyOnceARowIsPruned() {
        openWatched()
        composeRule.tapIcon("Bookmark options")
        composeRule.tap("Remove pruned threads")
        composeRule.waitForIdle()
        assertEquals(0, fakes.bookmarks.removeDeadCalls)

        // The menu stays open while the list gains a pruned row, which enables the item.
        fakes.bookmarks.seed(watched, bravo, charlie, pruned)
        composeRule.waitForText("Pruned", substring = false)
        composeRule.tap("Remove pruned threads")
        composeRule.waitUntilTrue { fakes.bookmarks.removeDeadCalls == 1 }
        composeRule.waitForTextGone("Pruned bookmark")
        assertTrue(rows.none { it.isDead })
    }

    @Test
    fun pullToRefresh_refreshesEveryBookmark_andCountsProgress() {
        openWatched()
        composeRule.waitUntilTrue { fakes.bookmarks.refreshCalls >= 1 }
        fakes.bookmarks.refreshProgressSteps = 3

        composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        composeRule.waitForText("Checking")
        composeRule.waitUntilTrue { fakes.bookmarks.refreshCalls >= 2 }
        composeRule.waitForTextGone("Checking")
    }

    @Test
    fun swipeToDelete_removesTheRow_andUndoRestoresIt() {
        openWatched()
        composeRule.nodeWithText("Bravo bookmark").performTouchInput { swipeLeft() }
        composeRule.waitForText("Bookmark removed")
        composeRule.waitUntilTrue { rows.none { it.threadNo == bravo.threadNo } }
        composeRule.waitForTextGone("Bravo bookmark")

        composeRule.tap("Undo")
        composeRule.waitUntilTrue { rows.any { it.threadNo == bravo.threadNo } }
        composeRule.waitForText("Bravo bookmark")
    }

    @Test
    fun actionSheet_opensPinsAndRemoves() {
        openWatched()
        openSheetFor("Bravo bookmark")
        composeRule.tap("Pin", substring = false)
        composeRule.waitUntilTrue { rows.single { it.threadNo == bravo.threadNo }.pinned }
        composeRule.waitForContentDescription("Pinned")

        openSheetFor("Bravo bookmark")
        composeRule.tap("Unpin")
        composeRule.waitUntilTrue { !rows.single { it.threadNo == bravo.threadNo }.pinned }

        openSheetFor("Bravo bookmark")
        composeRule.tap("Remove", substring = false)
        composeRule.waitForText("Bookmark removed")
        composeRule.waitUntilTrue { rows.none { it.threadNo == bravo.threadNo } }
        composeRule.waitForTextGone("Bravo bookmark")
    }

    @Test
    fun actionSheet_openReopensTheThread() {
        openWatched()
        openSheetFor(TestSeed.THREAD_SUBJECT)
        composeRule.tap("Open", substring = false)
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    @Test
    fun actionSheet_snapshotWritesToTheVault() {
        openWatched()
        openSheetFor(TestSeed.THREAD_SUBJECT)
        composeRule.tap("Snapshot to vault")
        composeRule.waitForText("Snapshot saved")
        assertEquals(listOf(VaultLocation(TestSeed.BOARD, TestSeed.THREAD_NO)), fakes.vault.snapshotCalls)
    }

    @Test
    fun actionSheet_snapshotIsRefusedForAPrunedThread() {
        fakes.bookmarks.seed(pruned)
        composeRule.openThreadsTab()
        composeRule.waitForText("Pruned bookmark")
        openSheetFor("Pruned bookmark")
        composeRule.waitForText("Thread is gone; the vault copy is what remains")

        // Disabled: the tap neither closes the sheet nor reaches the vault.
        composeRule.tap("Snapshot to vault")
        composeRule.waitForIdle()
        composeRule.waitForText("Thread is gone; the vault copy is what remains")
        assertTrue(fakes.vault.snapshotCalls.isEmpty())
    }

    @Test
    fun nothingWatched_showsTheEmptyState() {
        fakes.bookmarks.seed()
        composeRule.openThreadsTab()
        composeRule.waitForText("No bookmarks yet")
        composeRule.waitForText("Open a thread and tap the bookmark icon to keep it here.")
    }
}
