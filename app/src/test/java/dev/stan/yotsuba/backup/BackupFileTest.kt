package dev.stan.yotsuba.backup

import dev.stan.yotsuba.core.backup.BackupFile
import dev.stan.yotsuba.core.backup.BookmarkBackup
import dev.stan.yotsuba.core.backup.SettingsBackup
import dev.stan.yotsuba.core.backup.applyTo
import dev.stan.yotsuba.core.backup.toBackup
import dev.stan.yotsuba.core.backup.toDomain
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFileTest {

    private val bookmark = Bookmark(
        board = "g",
        threadNo = 12345,
        subject = "Test thread",
        opExcerpt = "hello",
        thumbnailUrl = "https://example.com/t.jpg",
        replyCount = 42,
        imageCount = 7,
        bookmarkedAt = 1_700_000_000_000,
        lastCheckedAt = 1_700_000_100_000,
        lastSeenPostNo = 12399,
        state = BookmarkState.ALIVE,
        newReplies = 3,
        unreadCount = 5,
    )

    @Test
    fun `a bookmark survives the round trip`() {
        val restored = bookmark.toBackup().toDomain()
        assertEquals(bookmark.board, restored.board)
        assertEquals(bookmark.threadNo, restored.threadNo)
        assertEquals(bookmark.subject, restored.subject)
        assertEquals(bookmark.replyCount, restored.replyCount)
        assertEquals(bookmark.bookmarkedAt, restored.bookmarkedAt)
        assertEquals(bookmark.lastSeenPostNo, restored.lastSeenPostNo)
        assertEquals(BookmarkState.ALIVE, restored.state)
    }

    @Test
    fun `settings survive the round trip`() {
        val settings = Settings(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            revealAllSpoilers = true,
            trustedDomains = setOf("example.com", "archive.org"),
            historyRetention = HistoryRetention.DAYS_7,
            favouriteBoards = setOf("g", "a"),
        )
        val restored = settings.toBackup().applyTo(Settings())
        assertEquals(settings.themeMode, restored.themeMode)
        assertEquals(settings.dynamicColor, restored.dynamicColor)
        assertEquals(settings.revealAllSpoilers, restored.revealAllSpoilers)
        assertEquals(settings.trustedDomains, restored.trustedDomains)
        assertEquals(settings.historyRetention, restored.historyRetention)
        assertEquals(settings.favouriteBoards, restored.favouriteBoards)
    }

    @Test
    fun `the update token is never carried in a backup`() {
        val current = Settings(updateToken = "ghp_secret")
        val restored = Settings(updateToken = "").toBackup().applyTo(current)
        assertEquals("ghp_secret", restored.updateToken)
    }

    @Test
    fun `an enum written by a newer build falls back instead of throwing`() {
        val restored = SettingsBackup(themeMode = "SEPIA").applyTo(Settings(themeMode = ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, restored.themeMode)
    }

    @Test
    fun `unknown fields in the file are ignored`() {
        val text = """
            {"version":1,"exportedAtMs":1,"appVersion":"9.9.9","somethingNew":true,
             "bookmarks":[{"board":"g","threadNo":1,"whatIsThis":"?"}]}
        """.trimIndent()
        val parsed = BackupFile.json.decodeFromString<BackupFile>(text)
        assertEquals(1, parsed.bookmarks.size)
        assertEquals("g", parsed.bookmarks.first().board)
    }

    @Test
    fun `the file round trips through json`() {
        val backup = BackupFile(
            exportedAtMs = 1_700_000_000_000,
            appVersion = "1.0.0",
            settings = Settings(themeMode = ThemeMode.LIGHT).toBackup(),
            bookmarks = listOf(bookmark.toBackup()),
        )
        val decoded = BackupFile.json.decodeFromString<BackupFile>(
            BackupFile.json.encodeToString(backup),
        )
        assertEquals(backup, decoded)
        assertEquals(BackupFile.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun `a bookmark backup keeps its defaults when fields are absent`() {
        val minimal = BackupFile.json.decodeFromString<BookmarkBackup>("""{"board":"g","threadNo":7}""")
        assertEquals(BookmarkState.UNKNOWN, minimal.toDomain().state)
        assertEquals(0, minimal.toDomain().replyCount)
    }
}
