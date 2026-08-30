package dev.stan.yotsuba

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

        composeRule.onAllNodesWithContentDescription("Toggle favourite")[0].performClick()
        composeRule.waitForText("Favourites")
        composeRule.waitUntil(UI_TIMEOUT_MS) { TestSeed.BOARD in settings.state.value.favouriteBoards }

        // Toggling again clears the section.
        composeRule.onAllNodesWithContentDescription("Toggle favourite")[0].performClick()
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

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Tech")
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
    }
}
