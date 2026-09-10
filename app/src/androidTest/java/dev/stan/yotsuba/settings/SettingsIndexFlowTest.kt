package dev.stan.yotsuba.settings

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.openSettings
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import org.junit.Test

@HiltAndroidTest
class SettingsIndexFlowTest : FlowTest() {

    /** Each index row and one control that only its own section shows. */
    private val sections = listOf(
        "Appearance" to "Dynamic color",
        "Reading" to "Text size",
        "Media & playback" to "Data saver",
        "Boards" to "Hide NSFW boards",
        "Links & privacy" to "Confirm before opening links",
        "Privacy" to "Lock the app",
        "Filters" to "Add filter",
        "Storage & data" to "Clear cache",
        "Updates" to "Version history",
        "About" to "Your numbers",
    )

    @Test
    fun everyRow_opensItsSection_andBackReturnsToTheIndex() {
        composeRule.openSettings()
        sections.forEach { (row, control) ->
            composeRule.tap(row, substring = false)
            composeRule.waitForText(control)
            composeRule.goBack()
            composeRule.waitForText("Storage & data", substring = false)
        }
    }

    @Test
    fun summaries_readTheSeededSettings() {
        fakes.hidden.seed(HiddenThread(TestSeed.BOARD, TestSeed.THREAD_NO))
        fakes.settings.set {
            it.copy(
                themeMode = ThemeMode.DARK,
                catalogLayout = CatalogLayout.LIST,
                hiddenBoards = setOf(TestSeed.NSFW_BOARD),
                trustedDomains = setOf("example.com", "example.org"),
                appLock = true,
                filters = listOf(Filter(id = "a", pattern = "spam"), Filter(id = "b", pattern = "bait")),
            )
        }
        composeRule.openSettings()

        composeRule.waitForText("Dark · List", substring = false)
        composeRule.waitForText("Text Default · spacing Default · history Forever", substring = false)
        composeRule.waitForText("Unmetered connections only · 10 s", substring = false)
        composeRule.waitForText("1 boards hidden · 1 threads hidden", substring = false)
        composeRule.waitForText("2 trusted domains", substring = false)
        composeRule.waitForText("App lock on", substring = false)
        composeRule.waitForText("2 filters", substring = false)
        composeRule.waitForText("Cache, history, bookmarks", substring = false)
        composeRule.waitForText("Version ${BuildConfig.VERSION_NAME}", substring = false)
        composeRule.waitForText("Version and attribution", substring = false)
    }
}
