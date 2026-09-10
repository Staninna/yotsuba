package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A tapped link in a post body, and the confirmation that stands between it and the browser. */
@HiltAndroidTest
class LinkFlowTest : FlowTest() {

    private val domain = "example.com"

    private fun tapTheSeededLink() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.LINK_REPLY_TEXT)
    }

    /**
     * Replaces the seeded link with [url] and taps it. The default is one no installed app
     * can open, so a link that is let through comes back as the "nothing handles this"
     * snackbar instead of backgrounding the app: once the browser is in front there is no
     * compose tree left to assert on.
     */
    private fun tapALinkTo(url: String = ODD_URL) {
        fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
                TestSeed.post(
                    TestSeed.BOARD, TestSeed.THREAD_NO + 1,
                    PostText(
                        listOf(
                            PostSegment("${TestSeed.LINK_REPLY_TEXT} "),
                            PostSegment(url, annotation = PostAnnotation.Link(url)),
                        ),
                    ),
                ),
            ),
        )
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.LINK_REPLY_TEXT)
    }

    @Test
    fun linkTap_asksFirst_andCancelKeepsTheThread() {
        tapTheSeededLink()

        composeRule.waitForText("Open external link?")
        composeRule.sheetNode(TestSeed.LINK_URL).assertIsDisplayed()
        composeRule.nodeWithText("Always trust $domain").assertIsDisplayed()

        composeRule.tap("Cancel")
        composeRule.waitForTextGone("Open external link?")
        assertEquals(emptySet<String>(), fakes.settings.state.value.trustedDomains)
        composeRule.listNode(TestSeed.LINK_REPLY_TEXT).assertIsDisplayed()
    }

    /** Open is the one button that leaves, so this one goes through the unopenable link. */
    @Test
    fun alwaysTrust_thenOpen_remembersTheDomain() {
        tapALinkTo()
        composeRule.waitForText("Open external link?")

        composeRule.tap("Always trust $ODD_DOMAIN")
        composeRule.nodeWithText("Always trust $ODD_DOMAIN").assertIsOn()
        composeRule.tap("Open", substring = false)

        composeRule.waitForText("No app can open this link")
        assertEquals(setOf(ODD_DOMAIN), fakes.settings.state.value.trustedDomains)
    }

    @Test
    fun withConfirmationOff_theLinkGoesStraightOut() {
        fakes.settings.set { it.copy(confirmBeforeOpeningLinks = false) }
        tapALinkTo()

        composeRule.waitForText("No app can open this link")
        assertFalse(composeRule.hasText("Open external link?"))
    }

    @Test
    fun anAlreadyTrustedDomain_skipsTheDialog() {
        fakes.settings.set { it.copy(trustedDomains = setOf(ODD_DOMAIN)) }
        tapALinkTo()

        composeRule.waitForText("No app can open this link")
        assertFalse(composeRule.hasText("Open external link?"))
    }

    /**
     * The preview is itself a visit to the untrusted host, so the dialog asks before it
     * fetches. The fetch goes out over OkHttp: this host does not resolve, and an answer
     * that carries no Open Graph tags reads the same as one that never arrived.
     */
    @Test
    fun previewAsksFirst_thenSaysThePageCarriesNoSummary() {
        tapALinkTo(DEAD_URL)

        composeRule.waitForText("Open external link?")
        composeRule.sheetNode("Preview").assertIsDisplayed()
        assertFalse(composeRule.hasText("No summary on that page"))

        composeRule.tap("Preview", substring = false)
        composeRule.waitForText("No summary on that page")
        assertFalse(composeRule.hasText("Preview"))
    }

    private companion object {
        const val ODD_DOMAIN = "nowhere.invalid"
        const val ODD_URL = "yotsuba-test://$ODD_DOMAIN/page"
        const val DEAD_URL = "https://$ODD_DOMAIN/page"
    }
}
