package dev.stan.yotsuba

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeSettingsRepository
import dev.stan.yotsuba.di.TestSeed
import javax.inject.Inject
import org.junit.Test

@HiltAndroidTest
class BoardsFavouriteFlowTest : FlowTest() {

    @Inject
    lateinit var settings: FakeSettingsRepository

    @Test
    fun favouritingABoard_showsTheFavouritesSection_andPersists() {
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)

        composeRule.iconInRow(TestSeed.BOARD_TITLE, "Toggle favourite").performClick()
        composeRule.waitForText("Favourites")
        composeRule.waitUntil(UI_TIMEOUT_MS) { TestSeed.BOARD in settings.state.value.favouriteBoards }

        // Toggling again clears the section.
        // The board now sits in Favourites and in its category: either star will do.
        composeRule.onAllNodes(inRow(TestSeed.BOARD_TITLE, "Toggle favourite"), useUnmergedTree = true).onFirst().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Favourites").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun boardsSearch_filtersAndRestores() {
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)

        composeRule.onNode(hasSetTextAction()).performTextInput("zzz-no-such-board")
        composeRule.waitForText("No matches")

        // Clearing the query restores the full list.
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
    }
}
