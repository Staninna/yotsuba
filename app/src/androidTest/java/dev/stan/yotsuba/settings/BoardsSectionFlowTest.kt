package dev.stan.yotsuba.settings

import androidx.compose.ui.test.performImeAction
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.textField
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class BoardsSectionFlowTest : FlowTest() {

    private fun openBoards() = composeRule.openSettingsSection("Boards", "Hide NSFW boards")

    /** Types a board code into the "Board code" field and presses Done, which opens its profile. */
    private fun openProfileDialog(code: String) {
        composeRule.typeInField(code)
        composeRule.textField().performImeAction()
        composeRule.waitForText("/$code/", substring = false)
    }

    @Test
    fun hideNsfwBoards_asksFirst_andCancelChangesNothing() {
        openBoards()
        composeRule.tapRow("Hide NSFW boards")
        composeRule.waitForText("Are you sure?")
        composeRule.tapRow("Cancel")
        composeRule.waitForTextGone("Are you sure?")
        assertTrue(fakes.settings.state.value.hiddenBoards.isEmpty())
    }

    @Test
    fun hideNsfwBoards_confirmed_hidesTheNsfwBoard() {
        openBoards()
        composeRule.tapRow("Hide NSFW boards")
        composeRule.waitForText("Are you sure?")
        flip("Confirm", setOf(TestSeed.NSFW_BOARD)) { it.hiddenBoards }
    }

    @Test
    fun hiddenThreadsDialog_unhidesAThread() {
        fakes.hidden.seed(HiddenThread(TestSeed.BOARD, TestSeed.THREAD_NO))
        openBoards()
        composeRule.tapRow("Hidden threads (1)")
        composeRule.waitForText("/${TestSeed.BOARD}/${TestSeed.THREAD_NO}", substring = false)
        composeRule.tapRow("Unhide")
        composeRule.waitUntilTrue { fakes.hidden.state.value.isEmpty() }
        composeRule.waitForText("Nothing here.")
        composeRule.tapRow("Done")
        composeRule.waitForText("Hidden threads (0)", substring = false)
    }

    @Test
    fun boardProfile_addsAnOverride_thenRemovesTheProfile() {
        openBoards()
        openProfileDialog(TestSeed.BOARD)
        flip("Large", FontSize.LARGE) { it.boardProfiles[TestSeed.BOARD]?.fontSize }
        composeRule.tapRow("Done")
        composeRule.waitForText("1 override", substring = false)

        composeRule.tapRow("/${TestSeed.BOARD}/")
        composeRule.waitForText("Remove profile")
        flip("Remove profile", emptyMap()) { it.boardProfiles }
        composeRule.waitForTextGone("Remove profile")
    }

    @Test
    fun boardProfile_startsOnTheGlobalSettings() {
        fakes.settings.set { it.copy(favouriteBoards = setOf(TestSeed.BOARD)) }
        openBoards()
        composeRule.waitForText("Uses global settings")
        composeRule.tapRow("/${TestSeed.BOARD}/")
        composeRule.waitForText("Use global")
    }
}
