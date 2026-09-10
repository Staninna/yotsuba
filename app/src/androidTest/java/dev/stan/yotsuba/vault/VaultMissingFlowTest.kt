package dev.stan.yotsuba.vault

import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.clickContentDescription
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.iconInRow
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file a rescan found gone from disk: how the explorer marks it, and the three routes
 * that offer to fetch it back.
 */
@HiltAndroidTest
class VaultMissingFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.withMissing.toTypedArray())

    private val seededThread = VaultLocation(TestSeed.BOARD, TestSeed.THREAD_NO)

    /** The Sync menu's re-download, which works through every thread with a missing file. */
    private fun redownloadEverything() {
        composeRule.openVault()
        composeRule.tapIcon("Sync")
        composeRule.tap("Re-download missing")
    }

    /** Nothing on disk to view, so a tap on the tile opens the sheet a long press would. */
    private fun openMissingSheet() {
        composeRule.openVault()
        composeRule.waitForContentDescription(VaultSeed.missingImage.displayName)
        composeRule.clickContentDescription(VaultSeed.missingImage.displayName)
        composeRule.waitForText(VaultSeed.missingImage.displayName, substring = false)
    }

    @Test
    fun missingFile_isMarkedInTheGrid_andCountedOnItsThreadRow() {
        composeRule.openVault()
        composeRule.waitForContentDescription(VaultSeed.missingImage.displayName)
        composeRule.waitForContentDescription("Missing from disk")

        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        // Three files on the row, and the 5 MB the gone one records is not in its size.
        composeRule.waitForText("3 files · 19 KB")
        composeRule.waitForText("1 missing")
    }

    @Test
    fun syncMenu_redownload_asksForTheThreadThatLostAFile_andReports() {
        fakes.vault.syncSummary = VaultSyncSummary(redownloaded = 1, unrecoverable = 2)
        redownloadEverything()
        composeRule.waitUntilTrue { fakes.vault.redownloadCalls.isNotEmpty() }
        assertEquals(listOf(listOf(seededThread)), fakes.vault.redownloadCalls)
        composeRule.waitForText("Fetched 1 file back, 2 unrecoverable.")
    }

    @Test
    fun redownload_thatFetchesNothingBack_namesTheUnrecoverableFile() {
        fakes.vault.syncSummary = VaultSyncSummary(unrecoverable = 1)
        redownloadEverything()
        composeRule.waitForText("Fetched 0 files back, 1 unrecoverable.")
    }

    @Test
    fun threadRowMenu_redownloadsThatThread() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.iconInRow(TestSeed.THREAD_SUBJECT, "More").performClick()
        composeRule.tap("Re-download missing")
        composeRule.waitUntilTrue { fakes.vault.redownloadCalls.isNotEmpty() }
        assertEquals(listOf(listOf(seededThread)), fakes.vault.redownloadCalls)
    }

    @Test
    fun entrySheet_redownload_reportsAPassThatFoundNoSource() {
        composeRule.openVault()
        composeRule.openEntrySheet(VaultSeed.missingImage.displayName)
        composeRule.tap("Re-download missing")
        composeRule.waitUntilTrue { fakes.vault.redownloadCalls.isNotEmpty() }
        assertEquals(listOf(listOf(seededThread)), fakes.vault.redownloadCalls)
        // The default summary fetched nothing back and found nothing to give up on either.
        composeRule.waitForText("Couldn't fetch any file back")
    }

    @Test
    fun missingFile_sheetOffersNoShareOrSaveToGallery() {
        openMissingSheet()
        composeRule.waitForText("Missing from disk")
        listOf("Re-download missing", "Select", "Delete").forEach {
            composeRule.waitForText(it, substring = false)
        }
        composeRule.waitForText("Open thread")
        // Neither can copy a file that is not there.
        assertFalse(composeRule.hasText("Share", substring = false))
        assertFalse(composeRule.hasText("Save to gallery"))
    }

    @Test
    fun mixedSelection_savesOnlyTheFilesStillOnDisk() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.longPressText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("3 selected")

        composeRule.tapIcon("Save to gallery")
        composeRule.waitUntilTrue { fakes.vault.exported.size == 2 }
        assertEquals(
            setOf(VaultSeed.seededImage.url, VaultSeed.spoilerImage.url),
            fakes.vault.exported.toSet(),
        )
        composeRule.waitForText("Saved 2 files to the gallery")
    }

    @Test
    fun selectionOfOnlyTheMissingFile_sharesAndSavesNothing() {
        openMissingSheet()
        composeRule.tap("Select", substring = false)
        composeRule.waitForText("1 selected")

        // The share builds no URI, which is what kept FileProvider from throwing here.
        composeRule.tapIcon("Share")
        composeRule.tapIcon("Save to gallery")
        composeRule.waitForIdle()
        assertTrue(fakes.vault.exported.isEmpty())
        // Nothing was copied, so the bar stays up rather than reporting a save.
        composeRule.waitForText("1 selected")
        assertFalse(composeRule.hasText("Saved 1 file"))
    }

    @Test
    fun deletingAMissingFile_trashesIt_andUndoBringsTheRowBack() {
        openMissingSheet()
        composeRule.tap("Delete", substring = false)
        composeRule.waitForText("${VaultSeed.missingImage.displayName} will be removed from the vault.")
        composeRule.confirmDelete()
        composeRule.waitUntilTrue {
            fakes.vault.trashState.value.map { it.url } == listOf(VaultSeed.missingImage.url)
        }
        assertTrue(fakes.vault.entriesNow.none { it.url == VaultSeed.missingImage.url })

        // The row is the only record of what to re-download, so the undo has to work.
        composeRule.waitForText("Deleted 1 file")
        composeRule.tap("Undo", substring = false)
        composeRule.waitUntilTrue { fakes.vault.trashState.value.isEmpty() }
        composeRule.waitForContentDescription("Missing from disk")
    }
}
