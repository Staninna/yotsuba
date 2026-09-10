package dev.stan.yotsuba.vault

import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The full-screen viewer over saved files, including the shuffle that fills it. */
@HiltAndroidTest
class VaultViewerFlowTest : FlowTest() {

    override fun seed() {
        fakes.vault.seed(*VaultSeed.entries.toTypedArray())
        // The saved conversation behind the replies button; nothing was saved through the UI.
        fakes.vault.sidecars[TestSeed.BOARD to TestSeed.THREAD_NO] = TestSeed.threadDetails
    }

    private fun openViewerMenu() {
        composeRule.tapViewerIcon("More")
        composeRule.waitForText("Picture-in-picture")
    }

    /** The FAB's menu at the Recent root, then the item [item]. */
    private fun shuffle(item: String) {
        composeRule.tapIcon("Shuffle play")
        composeRule.tap(item, substring = false)
        composeRule.waitForContentDescription("Close viewer")
    }

    @Test
    fun gridTap_opensTheViewerOnThatFile_andSwipePagesTheThread() {
        composeRule.openSeededVaultThread()
        composeRule.openVaultViewer(VaultSeed.seededImage.displayName)
        composeRule.waitForViewerChrome(
            VaultSeed.seededImage.displayName,
            "1 / 2 · 12 KB · 800×600 · ${TestSeed.THREAD_SUBJECT}",
        )

        composeRule.pageViewerForward()
        composeRule.waitForViewerChrome(VaultSeed.spoilerImage.displayName, "2 / 2 · 7 KB · 640×480")
    }

    @Test
    fun overflowMenu_carriesDeleteOpenThreadAndTheAutoAdvanceToggle() {
        composeRule.openVault()
        composeRule.openVaultViewer(VaultSeed.seededImage.displayName)
        openViewerMenu()
        composeRule.waitForText("Delete", substring = false)
        composeRule.waitForText("Open thread")
        composeRule.tap("Looping. Tap to auto-advance")

        openViewerMenu()
        composeRule.waitForText("Auto-advance on. Tap to loop")
    }

    @Test
    fun localFile_hasNoThreadToOpen() {
        composeRule.openVault()
        composeRule.openVaultViewer(VaultSeed.holiday.displayName)
        openViewerMenu()
        composeRule.waitForText("Delete", substring = false)
        assertFalse(composeRule.hasText("Open thread"))
    }

    @Test
    fun savedReplies_opensTheSavedConversation() {
        composeRule.openSeededVaultThread()
        composeRule.openVaultViewer(VaultSeed.seededImage.displayName)
        composeRule.tapViewerIcon("Saved replies")

        composeRule.waitForText(">>${TestSeed.THREAD_NO + 1}")
        composeRule.waitForText(TestSeed.REPLY_TEXT)
        composeRule.waitForText("Replies (0)")
    }

    @Test
    fun shuffleMenu_startsTheViewerOnAMatchingFile() {
        composeRule.openVault()
        shuffle("Videos only")
        composeRule.waitForViewerTitleIn(VaultSeed.videos)
        composeRule.tapViewerIcon("Close viewer")

        shuffle("Videos with sound")
        composeRule.waitForViewerTitleIn(VaultSeed.videosWithSound)
        composeRule.tapViewerIcon("Close viewer")

        shuffle("Silent videos")
        composeRule.waitForViewerChrome(VaultSeed.silentClip.displayName)
        composeRule.tapViewerIcon("Close viewer")

        shuffle("Images only")
        composeRule.waitForViewerTitleIn(VaultSeed.images)
        composeRule.tapViewerIcon("Close viewer")

        shuffle("Everything")
        composeRule.waitForViewerTitleIn(VaultSeed.names)
    }

    @Test
    fun shuffleOfASelection_playsOnlyTheTickedThreads() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.longPressText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("2 selected")

        shuffle("Selection")
        composeRule.waitForViewerTitleIn(
            listOf(VaultSeed.seededImage.displayName, VaultSeed.spoilerImage.displayName),
        )
    }

    @Test
    fun deleteFromTheViewer_isFinal_withNoUndo() {
        composeRule.openVault()
        composeRule.openVaultViewer(VaultSeed.seededImage.displayName)
        openViewerMenu()
        composeRule.tap("Delete", substring = false)
        composeRule.waitForText("${VaultSeed.seededImage.displayName} will be removed from the vault.")
        composeRule.button("Delete").performClick()

        composeRule.waitUntilTrue { fakes.vault.entriesNow.none { it.url == VaultSeed.seededImage.url } }
        assertTrue("a viewer delete skips the trash", fakes.vault.trashState.value.isEmpty())
        composeRule.waitForText("Deleted", substring = false)
        assertFalse(composeRule.hasText("Undo"))
    }
}
