package dev.stan.yotsuba.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.shell.tab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Test

@HiltAndroidTest
@OptIn(ExperimentalTestApi::class)
class HomeFlowTest : FlowTest() {

    override fun seed() {
        fakes.settings.set {
            it.copy(favouriteBoards = setOf(TestSeed.BOARD, TestSeed.VIDEO_BOARD, TestSeed.NSFW_BOARD))
        }
    }

    private val favourites get() = fakes.settings.state.value.favouriteBoards.toList()
    private val lastWrittenOrder get() = fakes.settings.writes.lastOrNull()?.favouriteBoards?.toList()

    private fun boardTab(board: String) = composeRule.nodeWithText("/$board/", substring = false)

    @Test
    fun noFavourites_showsEmptyState_andPickBoardsOpensBoards() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        fakes.settings.set { it.copy(favouriteBoards = emptySet()) }
        composeRule.waitForText("No favourite boards yet")
        composeRule.waitForText("Star a board and it shows up here as a tab.")
        composeRule.tap("Pick boards")
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.tab("Boards").assertIsSelected()
    }

    @Test
    fun favouriteTabs_tapAndSwipe_switchPages() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.tap("/${TestSeed.VIDEO_BOARD}/", substring = false)
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)
        composeRule.waitForText(TestSeed.VIDEO_BOARD_TITLE)
        boardTab(TestSeed.VIDEO_BOARD).assertIsSelected()

        // Back to the first page by swiping the pager the other way. Starting well inside
        // the window keeps clear of the system's edge gestures.
        composeRule.onRoot().performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.8f) }
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        boardTab(TestSeed.BOARD).assertIsSelected()
    }

    @Test
    fun addTab_opensBoards() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tapIcon("Add a board")
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.tab("Boards").assertIsSelected()
    }

    @Test
    fun accessibilityActions_reorderTheFavourites() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        // A move rewrites only the set's iteration order, so the new Settings `equals` the
        // old one and StateFlow drops it: the write is where a reorder can be seen. The
        // strip therefore still reads g, v, b, and the second move starts from there.
        boardTab(TestSeed.VIDEO_BOARD).performCustomAccessibilityActionWithLabel("Move left")
        composeRule.waitUntilTrue {
            lastWrittenOrder == listOf(TestSeed.VIDEO_BOARD, TestSeed.BOARD, TestSeed.NSFW_BOARD)
        }
        boardTab(TestSeed.VIDEO_BOARD).performCustomAccessibilityActionWithLabel("Move right")
        composeRule.waitUntilTrue {
            lastWrittenOrder == listOf(TestSeed.BOARD, TestSeed.NSFW_BOARD, TestSeed.VIDEO_BOARD)
        }
    }

    @Test
    fun accessibilityActions_removeFromHome_withUndo() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        boardTab(TestSeed.VIDEO_BOARD).performCustomAccessibilityActionWithLabel("Remove from Home")
        composeRule.waitForText("Removed /${TestSeed.VIDEO_BOARD}/ from favourites")
        composeRule.waitUntilTrue { favourites == listOf(TestSeed.BOARD, TestSeed.NSFW_BOARD) }
        composeRule.waitForTextGone("/${TestSeed.VIDEO_BOARD}/")

        composeRule.tap("Undo")
        composeRule.waitUntilTrue {
            favourites == listOf(TestSeed.BOARD, TestSeed.VIDEO_BOARD, TestSeed.NSFW_BOARD)
        }
        composeRule.waitForText("/${TestSeed.VIDEO_BOARD}/")
    }

    @Test
    fun currentPage_survivesRecreate() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tap("/${TestSeed.VIDEO_BOARD}/", substring = false)
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)
        recreate()
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)
        boardTab(TestSeed.VIDEO_BOARD).assertIsSelected()
    }
}
