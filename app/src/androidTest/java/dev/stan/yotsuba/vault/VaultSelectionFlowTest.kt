package dev.stan.yotsuba.vault

import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.iconInRow
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-select from the thread list and everything the selection bar can do with it. */
@HiltAndroidTest
class VaultSelectionFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    private val seededUrls = setOf(VaultSeed.seededImage.url, VaultSeed.spoilerImage.url)

    /** /g/ with the seeded thread's two files ticked. */
    private fun selectSeededThread() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.longPressText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("2 selected")
    }

    @Test
    fun overflowSelect_thenLongPress_addUp_andClearEmptiesIt() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.iconInRow(TestSeed.STICKY_SUBJECT, "More").performClick()
        composeRule.tap("Select", substring = false)
        composeRule.waitForText("1 selected")

        composeRule.longPressText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("3 selected")

        composeRule.tapIcon("Clear selection")
        composeRule.waitForTextGone("selected")
        composeRule.waitForContentDescription("Search")
    }

    @Test
    fun saveToGallery_exportsTheSelection_andReports() {
        selectSeededThread()
        composeRule.tapIcon("Save to gallery")
        composeRule.waitUntilTrue { fakes.vault.exported.size == 2 }
        assertEquals(seededUrls, fakes.vault.exported.toSet())
        composeRule.waitForText("Saved 2 files to the gallery")
        composeRule.waitForTextGone("selected")
    }

    @Test
    fun share_handsTheSelectionToTheSystemSheet() {
        selectSeededThread()
        // The chooser is system UI; the tap is as far as this suite can see.
        composeRule.tapIcon("Share")
    }

    @Test
    fun delete_cancel_keepsEverything() {
        selectSeededThread()
        composeRule.tapIcon("Delete")
        composeRule.waitForText("Delete 2 files from the vault?")
        composeRule.tap("Cancel", substring = false)
        composeRule.waitForTextGone("Delete file?")
        composeRule.waitForTextGone("selected")
        assertEquals(VaultSeed.entries.size, fakes.vault.entriesNow.size)
        assertTrue(fakes.vault.trashState.value.isEmpty())
    }

    @Test
    fun delete_dontAskAgain_trashesTheFiles_andUndoBringsThemBack() {
        selectSeededThread()
        composeRule.tapIcon("Delete")
        composeRule.tap("Don't ask again")
        composeRule.confirmDelete()
        composeRule.waitUntilTrue { fakes.vault.trashState.value.size == 2 }
        assertEquals(seededUrls, fakes.vault.trashState.value.map { it.url }.toSet())
        assertFalse(fakes.settings.state.value.confirmVaultDelete)

        composeRule.waitForText("Deleted 2 files")
        composeRule.tap("Undo", substring = false)
        composeRule.waitUntilTrue { fakes.vault.trashState.value.isEmpty() }
        assertEquals(VaultSeed.entries.size, fakes.vault.entriesNow.size)
        composeRule.waitForText("Restored")
    }
}
