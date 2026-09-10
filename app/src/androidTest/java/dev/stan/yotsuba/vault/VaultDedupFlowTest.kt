package dev.stan.yotsuba.vault

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateEntry
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duplicate finder. Two groups, each with one redundant file: the seeded image keeps
 * the spoiler copy company, and the sticky's picture the unsorted stray.
 */
@HiltAndroidTest
class VaultDedupFlowTest : FlowTest() {

    private fun dup(entry: VaultEntry) = DuplicateEntry(
        url = entry.url,
        absolutePath = entry.absolutePath,
        displayName = entry.displayName,
        sizeBytes = entry.sizeBytes!!,
        width = entry.width,
        height = entry.height,
        savedAt = entry.savedAt,
        subject = entry.subject,
        isVideo = entry.isVideo,
    )

    override fun seed() {
        fakes.vault.seed(*VaultSeed.entries.toTypedArray())
        fakes.dedup.groups += listOf(
            DuplicateGroup(listOf(dup(VaultSeed.seededImage), dup(VaultSeed.spoilerImage)), VaultSeed.seededImage.url),
            DuplicateGroup(listOf(dup(VaultSeed.stickyPic), dup(VaultSeed.stray)), VaultSeed.stickyPic.url),
        )
    }

    private fun openFinder() {
        composeRule.openVault()
        composeRule.tapIcon("More")
        composeRule.tap("Files saved twice, or images that look alike")
        composeRule.waitForText("Duplicates", substring = false)
    }

    /** The button that applies one group, told apart from the other group's by its size. */
    private fun groupButton(size: String) = composeRule.button("Keep selected, delete 1 ($size)")

    @Test
    fun finder_summarisesTheGroupsItFound() {
        openFinder()
        composeRule.waitForText("2 groups · suggestions free 9 KB")
        composeRule.waitForText("Apply suggestions")
        assertEquals(listOf(DedupMode.EXACT to 6), fakes.dedup.findCalls)
        assertEquals(1, fakes.vault.rescanCalls)
    }

    @Test
    fun similarMode_rescans_andItsSliderNarrowsTheDistance() {
        openFinder()
        composeRule.tapSegment("Similar")
        composeRule.waitUntilTrue { fakes.dedup.findCalls.size == 2 }
        assertEquals(DedupMode.SIMILAR to 6, fakes.dedup.findCalls.last())
        composeRule.waitForText("Max distance: 6 (lower is stricter)")

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput { swipeRight() }
        composeRule.waitUntilTrue { fakes.dedup.findCalls.size > 2 }
        val (mode, distance) = fakes.dedup.findCalls.last()
        assertEquals(DedupMode.SIMILAR, mode)
        assertTrue("the slider should have moved off the default", distance > 6)
        composeRule.waitForText("Max distance: $distance (lower is stricter)")
    }

    @Test
    fun keptTicks_toggle_andAGroupThatKeepsEverythingIsLeftAlone() {
        openFinder()
        assertEquals(2, composeRule.onAllNodesWithContentDescription("Kept").fetchSemanticsNodes().size)

        composeRule.inSheet("Duplicates", VaultSeed.spoilerImage.displayName).performClick()
        composeRule.waitUntilTrue { composeRule.onAllNodesWithContentDescription("Kept").fetchSemanticsNodes().size == 3 }
        groupButton("0 B").assertIsNotEnabled()

        composeRule.inSheet("Duplicates", VaultSeed.spoilerImage.displayName).performClick()
        composeRule.waitUntilTrue { composeRule.onAllNodesWithContentDescription("Kept").fetchSemanticsNodes().size == 2 }
        groupButton("7 KB").assertIsEnabled()
    }

    @Test
    fun applyOneGroup_deletesItsRedundantFile() {
        openFinder()
        groupButton("7 KB").performClick()
        composeRule.waitForText("Delete 1 file (7 KB) from the vault, keeping the ticked copies?")
        composeRule.button("Delete").performClick()

        composeRule.waitUntilTrue { fakes.vault.entriesNow.none { it.url == VaultSeed.spoilerImage.url } }
        assertTrue(fakes.vault.entriesNow.any { it.url == VaultSeed.seededImage.url })
        assertTrue(fakes.vault.entriesNow.any { it.url == VaultSeed.stray.url })
        composeRule.waitForText("Deleted 1 duplicate")
    }

    @Test
    fun applySuggestions_deletesEveryRedundantFile_orCanBeCalledOff() {
        openFinder()
        composeRule.tap("Apply suggestions")
        composeRule.waitForText("Delete 2 files (9 KB) from the vault, keeping the ticked copies?")
        composeRule.tap("Cancel", substring = false)
        composeRule.waitForTextGone("keeping the ticked copies")
        assertEquals(VaultSeed.entries.size, fakes.vault.entriesNow.size)

        composeRule.tap("Apply suggestions")
        composeRule.button("Delete").performClick()
        composeRule.waitUntilTrue { fakes.vault.entriesNow.size == VaultSeed.entries.size - 2 }
        val left = fakes.vault.entriesNow.map { it.url }
        assertTrue(VaultSeed.spoilerImage.url !in left && VaultSeed.stray.url !in left)
        composeRule.waitForText("Deleted 2 duplicates")
    }

    @Test
    fun noGroups_afterHashingWhatWasMissing_saysNoDuplicatesFound() {
        fakes.dedup.groups.clear()
        fakes.dedup.missingHashes = 3
        openFinder()
        composeRule.waitForText("No duplicates found")
        // The hashing pass ran; its "Hashing files (i of 3)" progress is too quick to catch.
        assertEquals(1, fakes.dedup.backfillCalls)
        assertEquals(0, fakes.dedup.missingHashes)
    }
}
