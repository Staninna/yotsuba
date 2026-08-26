package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MediaSaveFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun saveFromViewer_flipsBadgeToSaved_inViewerAndThread() {
        composeRule.openSeededThread()

        // Open the viewer on the seeded reply image.
        composeRule.waitForContentDescription(TestSeed.MEDIA_FILENAME)
        composeRule.onNodeWithContentDescription(
            TestSeed.MEDIA_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        composeRule.waitForContentDescription("Close viewer")

        // Tap the download action ("Save" while unsaved); the fake vault records it,
        // so the icon flips to the saved state ("Saved").
        composeRule.waitForContentDescription("Save")
        composeRule.onNodeWithContentDescription("Save", substring = false).performClick()
        composeRule.waitForContentDescription("Saved")

        // Back in the thread, the thumbnail carries the saved badge.
        composeRule.onNodeWithContentDescription("Close viewer").performClick()
        composeRule.waitForText(TestSeed.REPLY_TEXT)
        composeRule.waitForContentDescription("Saved")
    }
}
