package dev.stan.yotsuba.media

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.di.FakeMediaVaultRepository
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.hasContentDescription
import dev.stan.yotsuba.clickContentDescription
import dev.stan.yotsuba.nodeWithContentDescription
import dev.stan.yotsuba.openSeededViewer
import dev.stan.yotsuba.openVaultTab
import dev.stan.yotsuba.openThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForTextGone
import java.io.File

const val CLOSE_VIEWER = "Close viewer"

/** What an image page is described as: "name.png, 800×600". Video pages carry no description. */
fun pageDescription(item: MediaItem) = "${item.displayName}, ${item.width}×${item.height}"

fun ComposeTestRule.viewerImage(item: MediaItem = TestSeed.mediaItem): SemanticsNodeInteraction =
    nodeWithContentDescription(pageDescription(item))

/**
 * The seeded image in the viewer, once the thread underneath has left the composition:
 * its thumbnail carries the same description as the page and would shadow it.
 */
fun ComposeTestRule.openSeededImage() {
    openSeededViewer()
    waitForTextGone(TestSeed.OP_TEXT)
}

fun ComposeTestRule.openSeededVideo() {
    openThread(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT, TestSeed.VIDEO_OP_TEXT)
    tapIcon(TestSeed.VIDEO_FILENAME, substring = true)
    waitForContentDescription(CLOSE_VIEWER)
    waitForTextGone(TestSeed.VIDEO_OP_TEXT)
}

/** A tap in the middle of the page: what shows and hides the chrome. */
fun ComposeTestRule.tapPage() = onRoot().performTouchInput { click(center) }

/** The chrome hides itself three seconds after the last touch; brings it back before a control is used. */
fun ComposeTestRule.showChrome() {
    if (!hasContentDescription(CLOSE_VIEWER, substring = false)) tapPage()
    waitForContentDescription(CLOSE_VIEWER)
}

fun ComposeTestRule.tapChrome(description: String) {
    showChrome()
    tapIcon(description)
}

fun ComposeTestRule.openViewerMenu(item: String) {
    tapChrome("More")
    tap(item, substring = false)
}

/** Flicks the feed to the next page. */
fun ComposeTestRule.nextPage() = onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.2f) }

/**
 * Puts [item] in the vault as a real file (garbage bytes), so actions that need a local
 * copy skip the fetch. A `file:` [url] makes it a vault-only file with no online copy.
 */
fun FakeMediaVaultRepository.seedLocalCopy(
    item: MediaItem,
    url: String = item.fullUrl,
    board: String = TestSeed.BOARD,
    threadNo: Long = TestSeed.THREAD_NO,
): File {
    val file = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, item.displayName)
    file.writeBytes(ByteArray(64))
    seed(TestSeed.vaultEntry(item, board, threadNo).copy(url = url, absolutePath = file.path))
    return file
}

/** The Saved tab, then the tile named [displayName], which opens the vault's own viewer. */
fun ComposeTestRule.openVaultViewer(displayName: String) {
    openVaultTab()
    waitForContentDescription(displayName)
    clickContentDescription(displayName)
    waitForContentDescription(CLOSE_VIEWER)
}
