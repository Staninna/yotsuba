package dev.stan.yotsuba.catalog

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.CatalogSort
import dev.stan.yotsuba.openCatalog
import dev.stan.yotsuba.shell.assertAbove
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalog's sort menu. Three threads, arranged so the order under every sort differs
 * from the order under every other one, which is what stops an assertion on the card order
 * from passing for the wrong sort.
 */
@HiltAndroidTest
class CatalogSortFlowTest : FlowTest() {

    private val sorts get() = fakes.settings.state.value.catalogSorts

    override fun seed() {
        // FRESH was created past the clock, so its age floors at an hour and its
        // replies-per-hour is its reply count, far above the two older threads'.
        fakes.catalog.catalogs[TestSeed.BOARD] = listOf(
            thread(7001L, QUIET, replyCount = 1, imageCount = 5, createdAt = 1_699_500_000L),
            thread(7002L, BUSY, replyCount = 9, imageCount = 0, createdAt = 1_699_000_000L),
            thread(7003L, FRESH, replyCount = 4, imageCount = 2, createdAt = 2_000_000_000L),
        )
    }

    private fun thread(no: Long, subject: String, replyCount: Int, imageCount: Int, createdAt: Long) =
        TestSeed.catalogThread(TestSeed.BOARD, no, subject, "body $no", replyCount, imageCount)
            .copy(createdAt = createdAt)

    /** Opens the sort menu, picks [label], and waits for the choice to reach the store. */
    private fun sortBy(sort: CatalogSort, label: String) {
        composeRule.tapIcon("Sort")
        composeRule.tap(label, substring = false)
        // Bump order is the default, so choosing it drops the board's entry instead.
        composeRule.waitUntilTrue { sorts[TestSeed.BOARD] == sort.takeIf { it != CatalogSort.BUMP_ORDER } }
        composeRule.waitForIdle()
    }

    private fun assertOrder(first: String, second: String, third: String) {
        composeRule.assertAbove(first, second)
        composeRule.assertAbove(second, third)
    }

    @Test
    fun sortMenu_appliesEveryOrderToTheCards() {
        composeRule.openCatalog(firstThread = QUIET)
        assertOrder(QUIET, BUSY, FRESH)

        sortBy(CatalogSort.CREATION_TIME, "Newest first")
        assertOrder(FRESH, QUIET, BUSY)
        sortBy(CatalogSort.REPLY_COUNT, "Most replies")
        assertOrder(BUSY, FRESH, QUIET)
        sortBy(CatalogSort.IMAGE_COUNT, "Most images")
        assertOrder(QUIET, FRESH, BUSY)
        sortBy(CatalogSort.REPLIES_PER_HOUR, "Replies per hour")
        assertOrder(FRESH, BUSY, QUIET)
        sortBy(CatalogSort.BUMP_ORDER, "Bump order")
        assertOrder(QUIET, BUSY, FRESH)
    }

    @Test
    fun sortIsRememberedPerBoard() {
        composeRule.openCatalog(firstThread = QUIET)
        sortBy(CatalogSort.REPLY_COUNT, "Most replies")
        assertOrder(BUSY, FRESH, QUIET)

        // Another board is untouched: the choice was stored under /g/ alone.
        pressBack()
        composeRule.tap(TestSeed.VIDEO_BOARD_TITLE)
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)
        assertEquals(mapOf(TestSeed.BOARD to CatalogSort.REPLY_COUNT), sorts)

        pressBack()
        composeRule.tap(TestSeed.BOARD_TITLE)
        composeRule.waitForText(QUIET)
        assertOrder(BUSY, FRESH, QUIET)
    }

    private companion object {
        const val QUIET = "Quiet thread"
        const val BUSY = "Busy thread"
        const val FRESH = "Fresh thread"
    }
}
