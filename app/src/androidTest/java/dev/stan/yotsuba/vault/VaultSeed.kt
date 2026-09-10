package dev.stan.yotsuba.vault

import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultPaths

/**
 * The vault every test in this package starts from: two live boards, three live threads,
 * a locally imported thread and one unsorted leftover. Sizes, save times and post numbers
 * are all distinct, so each sort puts the eight files in a different order, and the
 * expected orders below are written out by hand rather than derived from the sort code.
 */
object VaultSeed {
    const val LOCAL_SUBJECT = "Imported holiday"
    val LOCAL = VaultLocation(VaultPaths.LOCAL_BOARD_NAME, 101L)

    private const val T0 = 1_700_000_000_000L
    private const val HOUR = 3_600_000L

    /** Under the FileProvider's external root, so a share tap builds a URI instead of throwing. */
    private fun VaultEntry.onDisk() = copy(
        absolutePath = "/storage/emulated/0/Yotsuba/${location.board}/${location.threadNo}/$displayName",
    )

    val seededImage = TestSeed.vaultEntry(TestSeed.mediaItem, savedAt = T0 + 4 * HOUR).onDisk()
    val spoilerImage = TestSeed.vaultEntry(TestSeed.spoilerMediaItem, savedAt = T0 + 3 * HOUR).onDisk()
    val stickyPic = TestSeed.vaultEntry(
        TestSeed.mediaItem(TestSeed.STICKY_THREAD_NO + 1, "sticky_pic", sizeBytes = 4_000L),
        threadNo = TestSeed.STICKY_THREAD_NO,
        subject = TestSeed.STICKY_SUBJECT,
        savedAt = T0,
    ).onDisk()
    val clip = TestSeed.vaultEntry(
        TestSeed.videoItem, TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO, TestSeed.VIDEO_SUBJECT,
        savedAt = T0 + 7 * HOUR, durationMs = 12_000L, hasAudio = true,
    ).onDisk()
    val soundPost = TestSeed.vaultEntry(
        TestSeed.soundItem, TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO, TestSeed.VIDEO_SUBJECT,
        savedAt = T0 + 6 * HOUR, durationMs = 5_000L,
    ).onDisk()
    val silentClip = TestSeed.vaultEntry(
        TestSeed.mediaItem(TestSeed.VIDEO_THREAD_NO + 4, "silent_clip", ext = ".webm", sizeBytes = 500_000L),
        TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO, TestSeed.VIDEO_SUBJECT,
        savedAt = T0 + 5 * HOUR, durationMs = 8_000L, hasAudio = false,
    ).onDisk()
    val holiday = TestSeed.vaultEntry(
        TestSeed.mediaItem(0L, "holiday", ext = ".jpg", sizeBytes = 1_000L),
        LOCAL.board, LOCAL.threadNo, LOCAL_SUBJECT, savedAt = T0 + 2 * HOUR,
    ).copy(postNo = null).onDisk()
    val stray = TestSeed.vaultEntry(
        TestSeed.mediaItem(0L, "stray", sizeBytes = 2_000L),
        VaultLocation.Unsorted.board, VaultLocation.Unsorted.threadNo, subject = null, savedAt = T0 + HOUR,
    ).copy(postNo = null).onDisk()

    /** Seed order; the post-number sort keeps the two files without a number in it. */
    val entries = listOf(seededImage, spoilerImage, stickyPic, clip, soundPost, silentClip, holiday, stray)

    /**
     * Listed in the seeded /g/ thread's sidecar but not on disk, as a rescan leaves a file
     * deleted outside the app: no path, so [VaultEntry.missing] is true. Its recorded size
     * is large enough that counting it would show up in every total, and it stays out of
     * [entries] so the counts and orders below are unchanged for the tests that ignore it.
     */
    val missingImage = TestSeed.vaultEntry(
        TestSeed.mediaItem(TestSeed.THREAD_NO + 8, "gone_image", sizeBytes = 5_000_000L),
        savedAt = T0 + 8 * HOUR,
    ).copy(absolutePath = "")

    /** The eight files on disk plus the one a rescan found gone. */
    val withMissing = entries + missingImage
    val names: Set<String> = entries.mapTo(HashSet()) { it.displayName }

    val newestFirst = listOf(
        "seeded_clip.webm", "seeded_soundpost.webm", "silent_clip.webm", "seeded_image.png",
        "spoiler_image.png", "holiday.jpg", "stray.png", "sticky_pic.png",
    )
    val largestFirst = listOf(
        "seeded_clip.webm", "seeded_soundpost.webm", "silent_clip.webm", "seeded_image.png",
        "spoiler_image.png", "sticky_pic.png", "stray.png", "holiday.jpg",
    )
    val byName = listOf(
        "holiday.jpg", "seeded_clip.webm", "seeded_image.png", "seeded_soundpost.webm",
        "silent_clip.webm", "spoiler_image.png", "sticky_pic.png", "stray.png",
    )
    val byPost = listOf(
        "seeded_image.png", "spoiler_image.png", "sticky_pic.png", "seeded_clip.webm",
        "seeded_soundpost.webm", "silent_clip.webm", "holiday.jpg", "stray.png",
    )
    val videos = listOf("seeded_clip.webm", "seeded_soundpost.webm", "silent_clip.webm")
    val videosWithSound = listOf("seeded_clip.webm", "seeded_soundpost.webm")
    val images = newestFirst - videos.toSet()
}
