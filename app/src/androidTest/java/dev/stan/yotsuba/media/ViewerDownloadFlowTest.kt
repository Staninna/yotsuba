package dev.stan.yotsuba.media

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class ViewerDownloadFlowTest : FlowTest() {

    private val url = TestSeed.mediaItem.fullUrl

    /** Opens the seeded image and presses the download button once. */
    private fun tapSave() {
        composeRule.openSeededImage()
        composeRule.tapChrome("Save")
    }

    private fun waitForSaved() = composeRule.waitForContentDescription("Saved", substring = false)

    private fun waitForUnsaved() {
        composeRule.showChrome()
        composeRule.waitForContentDescription("Save", substring = false)
    }

    @Test
    fun save_flipsToSaved_inViewerAndThread() {
        tapSave()
        waitForSaved()

        val (item, context) = fakes.vault.saves.single()
        assertEquals(url, item.fullUrl)
        assertEquals(TestSeed.BOARD, context.board)
        assertEquals(TestSeed.THREAD_NO, context.threadNo)
        assertEquals(listOf(url), fakes.vault.entriesNow.map { it.url })
        val saved = runBlocking { fakes.vault.savedThread(TestSeed.BOARD, TestSeed.THREAD_NO) }
        assertTrue(saved!!.posts.any { it.no == TestSeed.mediaItem.postNo })

        composeRule.tapChrome(CLOSE_VIEWER)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
        waitForSaved()
    }

    @Test
    fun savedMenu_removeDownload_emptiesVault() {
        tapSave()
        composeRule.tapChrome("Saved")
        composeRule.tap("Remove download")
        composeRule.waitUntilTrue { fakes.vault.entriesNow.isEmpty() }
        waitForUnsaved()
    }

    @Test
    fun savedMenu_downloadAgain_savesAFreshCopy() {
        tapSave()
        composeRule.tapChrome("Saved")
        composeRule.tap("Download again")
        composeRule.waitUntilTrue { fakes.vault.saves.size == 2 }
        assertEquals(listOf(url), fakes.vault.entriesNow.map { it.url })
        composeRule.showChrome()
        waitForSaved()
    }

    @Test
    fun heldSave_showsQueued_andCancelDownloadDropsIt() {
        fakes.saveQueue.holdNext = true
        tapSave()
        composeRule.tapChrome("Queued for download")
        composeRule.tap("Cancel download")
        composeRule.waitUntilTrue { fakes.saveQueue.cancelled == listOf(url) }
        waitForUnsaved()
        assertTrue(fakes.vault.saves.isEmpty())
    }

    @Test
    fun failedSave_showsError_retryDownloadSucceeds() {
        fakes.saveQueue.failNextWith = VaultError.Io("disk full")
        tapSave()
        composeRule.tapChrome("Couldn't save")
        // The error label is a dead row: tapping it leaves the menu where it was.
        composeRule.tap("Couldn't write file")
        composeRule.waitForText("Retry download")
        composeRule.tap("Retry download")
        composeRule.waitUntilTrue { fakes.saveQueue.retried == listOf(url) }
        waitForSaved()
        assertEquals(1, fakes.vault.saves.size)
    }

    @Test
    fun failedSave_dismissClearsTheStatus() {
        fakes.saveQueue.failNextWith = VaultError.Io("disk full")
        tapSave()
        composeRule.tapChrome("Couldn't save")
        composeRule.tap("Dismiss")
        waitForUnsaved()
        assertTrue(fakes.vault.saves.isEmpty())
    }

    @Test
    fun withoutStorageAccess_theSaveTapSavesNothing() {
        fakes.vault.access.value = false
        tapSave()
        // The tap opens the system "All files access" page, which takes the app off screen
        // along with its compose tree, so the vault is all that is left to read. The same
        // tap with access granted saves: see save_flipsToSaved_inViewerAndThread.
        assertTrue(fakes.vault.saves.isEmpty())
    }
}
