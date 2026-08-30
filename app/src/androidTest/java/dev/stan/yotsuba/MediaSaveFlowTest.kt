package dev.stan.yotsuba

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeMediaVaultRepository
import dev.stan.yotsuba.di.TestSeed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class MediaSaveFlowTest : FlowTest() {

    @Inject lateinit var vault: FakeMediaVaultRepository

    @Test
    fun saveFromViewer_flipsBadgeToSaved_inViewerAndThread() {
        composeRule.openSeededThread()
        assertTrue(vault.saves.isEmpty())

        // Open the viewer on the seeded reply image.
        composeRule.waitForContentDescription(TestSeed.MEDIA_FILENAME)
        composeRule.onNodeWithContentDescription(
            TestSeed.MEDIA_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        composeRule.waitForContentDescription("Close viewer")

        // Tap the download action ("Save" while unsaved); the fake vault records it,
        // so the icon flips to the saved state ("Saved").
        composeRule.waitForContentDescription("Save", substring = false)
        composeRule.onNodeWithContentDescription("Save", substring = false).performClick()
        composeRule.waitForContentDescription("Saved")

        // The vault really took the save: the seeded image, under the seeded thread.
        val (item, context) = vault.saves.single()
        assertEquals(TestSeed.mediaItem.fullUrl, item.fullUrl)
        assertEquals(TestSeed.BOARD, context.board)
        assertEquals(TestSeed.THREAD_NO, context.threadNo)
        assertEquals(listOf(TestSeed.mediaItem.fullUrl), vault.entriesNow.map { it.url })
        val saved = runBlocking { vault.savedThread(TestSeed.BOARD, TestSeed.THREAD_NO) }
        assertNotNull(saved)
        assertTrue(saved!!.posts.any { it.no == TestSeed.mediaItem.postNo })

        // Back in the thread, the thumbnail carries the saved badge.
        composeRule.onNodeWithContentDescription("Close viewer").performClick()
        composeRule.waitForText(TestSeed.REPLY_TEXT)
        composeRule.waitForContentDescription("Saved")
    }
}
