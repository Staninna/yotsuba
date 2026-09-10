package dev.stan.yotsuba.feature.thread.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme
import dev.stan.yotsuba.core.util.Urls
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Copying a thread's link, on the JVM. The instrumented suite can drive the menu but cannot
 * read the result: from API 29 the platform refuses `getPrimaryClip` to an app that does not
 * hold window focus, and nothing else in that suite needs focus, so only this assertion
 * noticed. Here the clipboard is readable and the check is exact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadTopBarCopyLinkTest {
    @get:Rule val composeRule = createComposeRule()

    private val noop = ThreadTopBarActions(
        onBack = {},
        onToggleBookmark = {},
        onRefresh = {},
        onOpenSearch = {},
        onOpenGallery = {},
        onSaveAll = {},
        onToggleTreeView = {},
        onToggleUnreadOnly = {},
        onToggleAutoRefresh = {},
        onOpenExternal = {},
        onClearFilter = {},
        onRepliesToMeTap = {},
        onRepliesToMeLongPress = {},
    )

    @Test
    fun copyLink_putsTheThreadUrlOnTheClipboard_andClosesTheMenu() {
        composeRule.setContent {
            YotsubaTheme(reduceMotion = true) {
                ThreadTopBar(
                    board = "g",
                    threadNo = 12_345L,
                    state = ThreadTopBarState(title = "Seeded thread"),
                    actions = noop,
                )
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Copy link").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Copy link").assertDoesNotExist()
        assertEquals(Urls.threadWebUrl("g", 12_345L), clipboardText())
    }

    private fun clipboardText(): String? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.getSystemService(ClipboardManager::class.java)
            .primaryClip?.getItemAt(0)?.text?.toString()
    }
}
