package dev.stan.yotsuba.vault

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.clearField
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.hasText as showsText
import dev.stan.yotsuba.iconInRow
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The overflow menus on thread and board rows: rename, merge, delete. */
@HiltAndroidTest
class VaultThreadActionsFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    private fun openThreadMenu(board: String, subject: String) {
        composeRule.openVault()
        composeRule.openVaultBoard(board)
        composeRule.iconInRow(subject, "More").performClick()
        composeRule.waitForText("Delete thread")
    }

    @Test
    fun remoteThread_hasNoRename() {
        openThreadMenu("/${TestSeed.BOARD}/", TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("Merge into")
        assertFalse(composeRule.showsText("Rename", substring = false))
    }

    @Test
    fun rename_localThread_blankIsRefused_andTheNewNameLands() {
        openThreadMenu("Imported", VaultSeed.LOCAL_SUBJECT)
        composeRule.tap("Rename", substring = false)
        composeRule.waitForText(VaultSeed.LOCAL_SUBJECT, substring = false)
        composeRule.clearField()
        composeRule.button("Rename").assertIsNotEnabled()

        composeRule.typeInField("Beach day")
        composeRule.button("Rename").performClick()
        composeRule.waitUntilTrue { fakes.vault.renames.isNotEmpty() }
        assertEquals(listOf(VaultSeed.LOCAL to "Beach day"), fakes.vault.renames)
        composeRule.waitForText("Beach day", substring = false)
        composeRule.waitForText("Renamed")
    }

    @Test
    fun merge_listsTheOtherThread_andMovesTheFilesIntoIt() {
        openThreadMenu("/${TestSeed.BOARD}/", TestSeed.THREAD_SUBJECT)
        composeRule.tap("Merge into")
        composeRule.waitForText("Merge into", substring = false)
        composeRule.onNode(hasText(TestSeed.STICKY_SUBJECT) and hasAnyAncestor(isDialog())).performClick()

        composeRule.waitUntilTrue { fakes.vault.merges.isNotEmpty() }
        val from = VaultLocation(TestSeed.BOARD, TestSeed.THREAD_NO)
        val into = VaultLocation(TestSeed.BOARD, TestSeed.STICKY_THREAD_NO)
        assertEquals(listOf(from to into), fakes.vault.merges)
        assertTrue(fakes.vault.entriesNow.none { it.location == from })
        composeRule.waitForText("Merged")
        composeRule.waitForTextGone(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("3 files · 23 KB")
    }

    @Test
    fun merge_withNoOtherThreadOnTheBoard_saysSo() {
        composeRule.openVault()
        composeRule.tapIcon("Search")
        composeRule.typeInField("thread")
        composeRule.tapSegment("Threads")
        composeRule.iconInRow(TestSeed.VIDEO_SUBJECT, "More").performClick()
        composeRule.tap("Merge into")
        composeRule.waitForText("No other thread on this board to merge into.")
        composeRule.tap("Cancel", substring = false)
        composeRule.waitForTextGone("No other thread")
        assertTrue(fakes.vault.merges.isEmpty())
    }

    @Test
    fun deleteThread_removesItsFilesForGood() {
        openThreadMenu("/${TestSeed.BOARD}/", TestSeed.THREAD_SUBJECT)
        composeRule.tap("Delete thread")
        composeRule.waitForText("Delete 2 files from the vault?")
        composeRule.confirmDelete()
        composeRule.waitUntilTrue { fakes.vault.entriesNow.none { it.location.threadNo == TestSeed.THREAD_NO } }
        assertTrue(fakes.vault.trashState.value.isEmpty())
        composeRule.waitForTextGone(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("Deleted", substring = false)
    }

    @Test
    fun deleteBoard_removesEveryThreadOnIt() {
        composeRule.openVault()
        composeRule.tapSegment("Browse")
        composeRule.iconInRow("/${TestSeed.VIDEO_BOARD}/", "More").performClick()
        composeRule.tap("Delete board")
        composeRule.waitForText("Delete 3 files from the vault?")
        composeRule.confirmDelete()
        composeRule.waitUntilTrue { fakes.vault.entriesNow.none { it.location.board == TestSeed.VIDEO_BOARD } }
        composeRule.waitForTextGone("/${TestSeed.VIDEO_BOARD}/", substring = false)
        composeRule.waitForText("5 files")
    }
}
