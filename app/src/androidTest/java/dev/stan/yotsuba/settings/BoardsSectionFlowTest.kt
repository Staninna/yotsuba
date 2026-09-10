package dev.stan.yotsuba.settings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performImeAction
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.textField
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class BoardsSectionFlowTest : FlowTest() {

    private fun openBoards() = composeRule.openSettingsSection("Boards", "Hide NSFW boards")

    /** Types a board code and taps the add button, which opens that board's profile. */
    private fun addProfile(code: String) {
        composeRule.typeInField(code)
        composeRule.tapIcon("Board code")
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

    /** Catalog thumbnails only, as its summary says; the profile dialog can override it per board. */
    @Test
    fun blurCatalogThumbnails_flipsItsSetting() {
        openBoards()
        composeRule.waitForText("Tap a thumbnail to see it; tap again to open the thread")
        flip("Blur catalog thumbnails", true) { it.blurThumbnails }
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
        addProfile(TestSeed.BOARD)
        flip("Large", FontSize.LARGE) { it.boardProfiles[TestSeed.BOARD]?.fontSize }
        composeRule.tapRow("Done")
        composeRule.waitForText("1 override", substring = false)

        composeRule.tapRow("/${TestSeed.BOARD}/")
        composeRule.waitForText("Remove profile")
        flip("Remove profile", emptyMap()) { it.boardProfiles }
        composeRule.waitForTextGone("Remove profile")
    }

    @Test
    fun boardCode_addButton_opensTheProfile_andEmptiesTheField() {
        openBoards()
        addProfile(TestSeed.BOARD)
        composeRule.tapRow("Done")
        // Emptied on success, so the next code starts from nothing.
        composeRule.textField().assert(hasText(TestSeed.BOARD, substring = false).not())
    }

    @Test
    fun boardCode_thatIsRejected_saysWhy_andCommitsNothing() {
        openBoards()
        // Seven characters, so the board-code regex rejects it.
        composeRule.typeInField("toolong")
        composeRule.waitForText("A board code is 1 to 5 lowercase letters or digits")

        // Both ways in are off: the add button, and the Done key that used to fail silently.
        composeRule.tapIcon("Board code")
        composeRule.textField().performImeAction()
        composeRule.waitForIdle()
        assertFalse(composeRule.hasText("/toolong/", substring = false))
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
