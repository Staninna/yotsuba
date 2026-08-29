package dev.stan.yotsuba

/*
 * Instrumented Compose e2e tests. They need a device or emulator attached:
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 *
 * All repositories are replaced with in-memory fakes (see di/TestRepositoryModule.kt),
 * so no network or database is touched; the tests drive the real navigation graph.
 */

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
class BoardsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun boardsList_showsSeededBoard() {
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
    }

    @Test
    fun boardTap_opensCatalogWithSeededThread() {
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.clickText(TestSeed.BOARD_TITLE)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun threadTap_showsSeededPosts() {
        composeRule.openSeededThread()
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }

    @Test
    fun thumbnailTap_opensMediaViewer() {
        composeRule.openSeededThread()
        composeRule.waitForContentDescription(TestSeed.MEDIA_FILENAME)
        composeRule.onNodeWithContentDescription(
            TestSeed.MEDIA_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        // The viewer chrome carries the "Close viewer" action (media_close).
        composeRule.waitForContentDescription("Close viewer")
    }
}
