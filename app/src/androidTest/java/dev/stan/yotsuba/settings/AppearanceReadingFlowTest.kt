package dev.stan.yotsuba.settings

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.backToTabs
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.LineSpacing
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.domain.model.TimestampMode
import dev.stan.yotsuba.domain.model.TimestampZone
import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@HiltAndroidTest
class AppearanceReadingFlowTest : FlowTest() {

    private fun openAppearance() = composeRule.openSettingsSection("Appearance", "Dynamic color")
    private fun openReading() = composeRule.openSettingsSection("Reading", "Text size")

    @Test
    fun appearance_everyControlFlipsItsSetting() {
        openAppearance()
        flip("Dark", ThemeMode.DARK) { it.themeMode }
        flip("System", ThemeMode.SYSTEM) { it.themeMode }
        flip("Dynamic color", false) { it.dynamicColor }
        flip("Pure black", true) { it.pureBlack }
        flip("Compact", CatalogLayout.COMPACT) { it.catalogLayout }
        flip("List", CatalogLayout.LIST) { it.catalogLayout }
        flip("Reveal all spoilers", true) { it.revealAllSpoilers }
        flip("Reduce motion", true) { it.reduceMotion }
    }

    @Test
    fun appearance_pureBlackIsDeadUnderTheLightTheme() {
        openAppearance()
        flip("Light", ThemeMode.LIGHT) { it.themeMode }
        // Dead row: the same tap turns it on under any other theme.
        composeRule.tapRow("Pure black")
        composeRule.waitForIdle()
        assertFalse(fakes.settings.state.value.pureBlack)
    }

    @Test
    fun reading_textControlsFlipTheirSettings_andThePreviewIsThere() {
        openReading()
        composeRule.waitForText("This is how posts will look with these settings.")
        composeRule.waitForText("be me, reading a thread on my phone")
        flip("Small", FontSize.SMALL) { it.fontSize }
        flip("Extra large", FontSize.EXTRA_LARGE) { it.fontSize }
        flip("Relaxed", LineSpacing.RELAXED) { it.lineSpacing }
        flip("Compact", LineSpacing.COMPACT) { it.lineSpacing }
        flip("Jump to post", QuoteTapAction.JUMP) { it.quoteTap }
        flip("Show preview", QuoteTapAction.POPOVER) { it.quoteTap }
    }

    @Test
    fun reading_everySwitchFlipsItsSetting() {
        openReading()
        flip("Auto-refresh threads", true) { it.autoRefreshEnabled }
        flip("Collapse read posts", false) { it.collapseReadPosts }
        flip("Highlight gets", true) { it.highlightGets }
        flip("Translate posts", true) { it.translatePosts }
        flip("Record history", false) { it.recordHistory }
        flip("Notify on new replies", false) { it.bookmarkNotifications }
    }

    @Test
    fun reading_postTimeChips_andTheZoneThatWaitsOnAnAbsoluteTime() {
        openReading()
        // A relative time has no zone to read it in, so that row is dead under the default.
        composeRule.tapRow("Board (New York)")
        composeRule.waitForIdle()
        assertEquals(TimestampZone.LOCAL, fakes.settings.state.value.timestampZone)

        flip("Date and time", TimestampMode.ABSOLUTE) { it.timestampMode }
        flip("Board (New York)", TimestampZone.BOARD) { it.timestampZone }
        flip("Both", TimestampMode.BOTH) { it.timestampMode }
        flip("This phone", TimestampZone.LOCAL) { it.timestampZone }
        flip("Relative", TimestampMode.RELATIVE) { it.timestampMode }
    }

    /**
     * Retention reaches the usage log the way it reaches history: the repository applies the
     * preference on every write, so the trim lands on the next thread the app records.
     */
    @Test
    fun reading_historyRetentionTrimsTheUsageLog() {
        fakes.usage.seed(
            UsageEvent(UsageKind.SEARCH_RUN, System.currentTimeMillis() - 30L * 86_400_000),
            UsageEvent(UsageKind.SEARCH_RUN, System.currentTimeMillis()),
        )
        openReading()
        flip("7 days", HistoryRetention.DAYS_7) { it.historyRetention }
        composeRule.backToTabs()
        composeRule.openSeededThread()

        composeRule.waitUntilTrue { fakes.usage.trimCalls > 0 }
        assertEquals(1, fakes.usage.count(UsageKind.SEARCH_RUN))
    }

    @Test
    fun reading_historyAndBookmarkChipsFlipTheirSettings() {
        openReading()
        flip("7 days", HistoryRetention.DAYS_7) { it.historyRetention }
        flip("30 days", HistoryRetention.DAYS_30) { it.historyRetention }
        flip("Forever", HistoryRetention.FOREVER) { it.historyRetention }
        flip("180 min", 180) { it.bookmarkRefreshMinutes }
        flip("15 min", 15) { it.bookmarkRefreshMinutes }
    }
}
