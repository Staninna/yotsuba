package dev.stan.yotsuba.vault

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Trash sheet: what a grid delete moved aside, and the two ways out of it. */
@HiltAndroidTest
class VaultTrashFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    private val trashed = VaultSeed.spoilerImage

    private fun openTrash() {
        composeRule.tapIcon("More")
        composeRule.tap("Deleted files, kept for a week")
        composeRule.waitForText("Deleted files stay here for a week, then go for good.")
    }

    /** Deletes one file off the Recent grid, which is the only route into the trash. */
    private fun trashOneFile() {
        composeRule.openVault()
        composeRule.openEntrySheet(trashed.displayName)
        composeRule.tap("Delete", substring = false)
        composeRule.confirmDelete()
        composeRule.waitUntilTrue { fakes.vault.trashState.value.map { it.url } == listOf(trashed.url) }
    }

    @Test
    fun trash_isEmptyUntilSomethingIsDeleted() {
        composeRule.openVault()
        openTrash()
        composeRule.waitForText("The trash is empty")
    }

    @Test
    fun deletedFile_isListed_andRestoreBringsItBack() {
        trashOneFile()
        openTrash()
        composeRule.waitForText(trashed.displayName, substring = false)
        composeRule.tap("Restore", substring = false)

        composeRule.waitUntilTrue { fakes.vault.trashState.value.isEmpty() }
        assertEquals(VaultSeed.entries.size, fakes.vault.entriesNow.size)
        composeRule.waitForText("The trash is empty")
        composeRule.waitForContentDescription(trashed.displayName)
    }

    @Test
    fun emptyTrash_clearsItForGood_andClosesTheSheet() {
        trashOneFile()
        openTrash()
        composeRule.tap("Empty trash")
        composeRule.waitUntilTrue { fakes.vault.emptyTrashCalls == 1 }
        assertTrue(fakes.vault.trashState.value.isEmpty())
        composeRule.waitForTextGone("Deleted files stay here for a week")
        assertEquals(VaultSeed.entries.size - 1, fakes.vault.entriesNow.size)
    }
}
