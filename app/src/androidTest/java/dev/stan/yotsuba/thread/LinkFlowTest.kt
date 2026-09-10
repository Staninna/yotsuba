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
     * Replaces the seeded link with one no installed app can open, so a link that is let
     * through comes back as the "nothing handles this" snackbar instead of backgrounding
     * the app: once the browser is in front there is no compose tree left to assert on.
     */
    private fun tapALinkNothingHandles() {
        fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
                TestSeed.post(
                    TestSeed.BOARD, TestSeed.THREAD_NO + 1,
                    PostText(
                        listOf(
                            PostSegment("${TestSeed.LINK_REPLY_TEXT} "),
                            PostSegment(ODD_URL, annotation = PostAnnotation.Link(ODD_URL)),
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
        tapALinkNothingHandles()
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
        tapALinkNothingHandles()

        composeRule.waitForText("No app can open this link")
        assertFalse(composeRule.hasText("Open external link?"))
    }

    @Test
    fun anAlreadyTrustedDomain_skipsTheDialog() {
        fakes.settings.set { it.copy(trustedDomains = setOf(ODD_DOMAIN)) }
        tapALinkNothingHandles()

        composeRule.waitForText("No app can open this link")
        assertFalse(composeRule.hasText("Open external link?"))
    }

    private companion object {
        const val ODD_DOMAIN = "nowhere.invalid"
        const val ODD_URL = "yotsuba-test://$ODD_DOMAIN/page"
    }
}
