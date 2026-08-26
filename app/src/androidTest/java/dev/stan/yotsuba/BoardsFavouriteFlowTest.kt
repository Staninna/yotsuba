package dev.stan.yotsuba

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeSettingsRepository
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class BoardsFavouriteFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun favouritingABoard_showsTheFavouritesSection_andPersists() {
        val fake = settingsRepository as FakeSettingsRepository
        composeRule.waitForText(TestSeed.BOARD_TITLE)

        composeRule.onAllNodesWithContentDescription("Toggle favourite")[0].performClick()
        composeRule.waitForText("Favourites")
        composeRule.waitUntil(UI_TIMEOUT_MS) { TestSeed.BOARD in fake.state.value.favouriteBoards }

        // Toggling again clears the section.
        composeRule.onAllNodesWithContentDescription("Toggle favourite")[0].performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Favourites").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun boardsSearch_filtersAndRestores() {
        composeRule.waitForText(TestSeed.BOARD_TITLE)

        composeRule.onNode(hasSetTextAction()).performTextInput("zzz-no-such-board")
        composeRule.waitForText("No matches")

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Tech")
        composeRule.waitForText(TestSeed.BOARD_TITLE)
    }
}
