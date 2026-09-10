package dev.stan.yotsuba.shell

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.MainActivity
import dev.stan.yotsuba.core.widget.WidgetDeepLink
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextDisplayed
import org.junit.Test

@HiltAndroidTest
class DeepLinkFlowTest : FlowTest() {

    override val launchOnSetUp: Boolean get() = false

    @Test
    fun threadUrl_coldStart_landsInThread() {
        launch(viewIntent(threadUrl(TestSeed.BOARD, TestSeed.THREAD_NO)))
        composeRule.waitForText(TestSeed.OP_TEXT)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }

    @Test
    fun postFragment_scrollsToThatPost() {
        launch(viewIntent(threadUrl(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.THREAD_NO + 7)))
        // Composed is not proof: a lazy list composes a little beyond the viewport, which is
        // why waiting for the OP to leave the composition was flaky. The deep-linked post
        // being on screen is the scroll, and it cannot be while the list still sits at the OP.
        composeRule.waitForTextDisplayed(TestSeed.GREENTEXT_LINE)
    }

    @Test
    fun catalogUrl_landsInCatalog() {
        // The other host and the plain http scheme are in the manifest too.
        launch(viewIntent("http://boards.4channel.org/${TestSeed.BOARD}/catalog"))
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText(TestSeed.CLOSED_SUBJECT)
        // A pushed catalog, not the Home pane: it carries its own back arrow.
        composeRule.waitForContentDescription("Back")
    }

    @Test
    fun sharedText_opensFirstFourChanUrl() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(Intent.EXTRA_TEXT, "look at ${threadUrl(TestSeed.BOARD, TestSeed.STICKY_THREAD_NO)}, nice")
        launch(intent)
        composeRule.waitForText(TestSeed.STICKY_OP_TEXT)
    }

    @Test
    fun secondLink_whileRunning_opensItsThread() {
        launch()
        composeRule.waitForText("No favourite boards yet")
        deliver(viewIntent(threadUrl(TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO)))
        composeRule.waitForText(TestSeed.VIDEO_OP_TEXT)
    }

    @Test
    fun widgetTap_opensTheThreadFromItsExtras() {
        launch(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(WidgetDeepLink.EXTRA_BOARD, TestSeed.BOARD)
                .putExtra(WidgetDeepLink.EXTRA_THREAD_NO, TestSeed.STICKY_THREAD_NO),
        )
        composeRule.waitForText(TestSeed.STICKY_OP_TEXT)
    }

    @Test
    fun missingThread_showsNotFound() {
        launch(viewIntent(threadUrl(TestSeed.BOARD, 7777L)))
        composeRule.waitForText("Not found", substring = false)
        composeRule.waitForText("Retry", substring = false)
    }
}
