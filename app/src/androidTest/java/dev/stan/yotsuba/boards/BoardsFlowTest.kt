package dev.stan.yotsuba.boards

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.clearField
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.iconInRow
import dev.stan.yotsuba.inRow
import dev.stan.yotsuba.openBoardsTab
import dev.stan.yotsuba.shell.assertAbove
import dev.stan.yotsuba.shell.nodeOnLineWith
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Test

@HiltAndroidTest
class BoardsFlowTest : FlowTest() {

    private val settings get() = fakes.settings.state.value

    private fun openBoards() {
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
    }

    /** The /v/ row, not the "Video Games" category header above it. */
    private val videoBoardRow = hasText(TestSeed.VIDEO_BOARD_TITLE) and hasClickAction()

    /** The tri-state checkbox beside a category header in edit mode. */
    private fun categoryCheckbox(label: String) = composeRule.nodeOnLineWith(label, isToggleable())

    @Test
    fun list_showsEveryBoardWithCategoriesAndNsfwBadge() {
        openBoards()
        composeRule.waitForText(TestSeed.VIDEO_BOARD_TITLE)
        composeRule.waitForText(TestSeed.NSFW_BOARD_TITLE)
        composeRule.waitForText("Interests")
        composeRule.waitForText("Adult")
        composeRule.waitForText("NSFW", substring = false)
        composeRule.waitForText("${TestSeed.BOARD_TITLE} board")
    }

    @Test
    fun rowTap_andCodeChip_openTheCatalog() {
        openBoards()
        composeRule.tap(TestSeed.BOARD_TITLE)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        pressBack()
        composeRule.tap("/${TestSeed.VIDEO_BOARD}/", substring = false)
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)
    }

    @Test
    fun search_ranksCodeMatchesFirst_andClearingRestores() {
        openBoards()
        composeRule.typeInField("g")
        composeRule.waitForTextGone(TestSeed.NSFW_BOARD_TITLE)
        // /g/ is an exact code match; "Video Games" only has a g in its title.
        composeRule.assertAbove(TestSeed.BOARD_TITLE, TestSeed.VIDEO_BOARD_TITLE)

        composeRule.clearField()
        composeRule.typeInField("zzz")
        composeRule.waitForText("No matches")
        composeRule.waitForText("Nothing matches \"zzz\".")
        composeRule.clearField()
        composeRule.waitForText(TestSeed.NSFW_BOARD_TITLE)
    }

    @Test
    fun editMode_hidesBoardsAndCategories_untilNoneAreLeft() {
        openBoards()
        composeRule.tapIcon("Edit visible boards")
        composeRule.tap(TestSeed.BOARD_TITLE)
        composeRule.waitUntilTrue { TestSeed.BOARD in settings.hiddenBoards }
        composeRule.onNode(videoBoardRow).performClick()
        composeRule.waitUntilTrue { TestSeed.VIDEO_BOARD in settings.hiddenBoards }
        categoryCheckbox("Adult").performClick()
        composeRule.waitUntilTrue { TestSeed.NSFW_BOARD in settings.hiddenBoards }

        composeRule.tapIcon("Done editing")
        composeRule.waitForText("No boards")
        composeRule.waitForText("Every board is hidden. Use the pencil to unhide some.")

        // A category whose boards are all hidden toggles back to shown.
        composeRule.tapIcon("Edit visible boards")
        categoryCheckbox("Interests").performClick()
        composeRule.waitUntilTrue { TestSeed.BOARD !in settings.hiddenBoards }
        composeRule.tapIcon("Done editing")
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.waitForTextGone(TestSeed.NSFW_BOARD_TITLE)
    }

    @Test
    fun favouriteStar_addsSection_andRemovalOffersUndo() {
        openBoards()
        composeRule.iconInRow(TestSeed.BOARD_TITLE, "Toggle favourite").performClick()
        composeRule.waitForText("Favourites")
        composeRule.waitUntilTrue { TestSeed.BOARD in settings.favouriteBoards }

        // The board now sits in Favourites and in its category: either star will do.
        composeRule.onAllNodes(inRow(TestSeed.BOARD_TITLE, "Toggle favourite"), useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForText("Removed /${TestSeed.BOARD}/ from favourites")
        composeRule.waitUntilTrue { TestSeed.BOARD !in settings.favouriteBoards }
        composeRule.waitForTextGone("Favourites")
        composeRule.tap("Undo")
        composeRule.waitUntilTrue { TestSeed.BOARD in settings.favouriteBoards }
        composeRule.waitForText("Favourites")
    }

    @Test
    fun loadFailure_showsError_andRetryRecovers() {
        fakes.boards.failWith = NetworkError.Offline
        composeRule.openBoardsTab()
        composeRule.waitForText("You're offline")
        fakes.boards.failWith = null
        composeRule.tap("Retry", substring = false)
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.waitUntilTrue { fakes.boards.calls >= 2 }
    }
}
