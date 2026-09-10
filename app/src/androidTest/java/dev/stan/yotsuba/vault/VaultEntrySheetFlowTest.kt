package dev.stan.yotsuba.vault

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The long-press sheet over a grid cell, from the Recent grid. */
@HiltAndroidTest
class VaultEntrySheetFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    private fun openSeededSheet() {
        composeRule.openVault()
        composeRule.openEntrySheet(VaultSeed.seededImage.displayName)
    }

    @Test
    fun longPress_opensTheSheet_withDetailsAndEveryAction() {
        openSeededSheet()
        composeRule.waitForText("12 KB · 800×600 · No. ${TestSeed.THREAD_NO + 1}")
        listOf("Select", "Open thread", "Go to post", "Share", "Save to gallery", "Delete").forEach {
            composeRule.waitForText(it, substring = false)
        }
    }

    @Test
    fun localEntry_hasNoThreadToGoTo() {
        composeRule.openVault()
        composeRule.openEntrySheet(VaultSeed.holiday.displayName)
        composeRule.waitForText("1000 B")
        composeRule.waitForText("Delete", substring = false)
        assertFalse(composeRule.hasText("Open thread"))
        assertFalse(composeRule.hasText("Go to post"))
    }

    @Test
    fun select_startsASelectionWithThatFile() {
        openSeededSheet()
        composeRule.tap("Select", substring = false)
        composeRule.waitForText("1 selected")
    }

    @Test
    fun openThread_leavesForTheLiveThread() {
        openSeededSheet()
        composeRule.tap("Open thread")
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    @Test
    fun goToPost_leavesForThePostItself() {
        openSeededSheet()
        composeRule.tap("Go to post")
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }

    @Test
    fun share_handsTheFileToTheSystemSheet() {
        openSeededSheet()
        // The chooser is system UI; the tap is as far as this suite can see.
        composeRule.tap("Share", substring = false)
    }

    @Test
    fun saveToGallery_exportsTheFile_andReports() {
        openSeededSheet()
        composeRule.tap("Save to gallery")
        composeRule.waitUntilTrue { fakes.vault.exported.isNotEmpty() }
        assertEquals(listOf(VaultSeed.seededImage.url), fakes.vault.exported)
        composeRule.waitForText("Saved 1 file to the gallery")
    }

    @Test
    fun delete_asksFirst_thenTrashesWithUndo() {
        openSeededSheet()
        composeRule.tap("Delete", substring = false)
        composeRule.waitForText("${VaultSeed.seededImage.displayName} will be removed from the vault.")
        composeRule.confirmDelete()
        composeRule.waitUntilTrue { fakes.vault.trashState.value.map { it.url } == listOf(VaultSeed.seededImage.url) }
        composeRule.waitForText("Deleted 1 file")
        composeRule.waitForText("Undo", substring = false)
    }
}
