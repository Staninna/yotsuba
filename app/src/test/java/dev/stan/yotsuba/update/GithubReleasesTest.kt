package dev.stan.yotsuba.update

import dev.stan.yotsuba.core.update.GithubReleases
import dev.stan.yotsuba.core.update.ReleaseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleasesTest {

    // Shaped like the real releases/latest payload, trimmed to the fields we
    // read plus a few we deliberately ignore.
    private val payload = """
        {
          "tag_name": "v1.1.0",
          "name": "v1.1.0",
          "draft": false,
          "body": "## What's Changed\n* In-app updates\n",
          "author": {"login": "Staninna"},
          "assets": [
            {"name": "mapping.txt", "size": 12, "url": "https://api.github.com/repos/Staninna/yotsuba/releases/assets/1",
             "browser_download_url": "https://example.com/mapping.txt"},
            {"name": "yotsuba-v1.1.0.apk", "size": 14680064,
             "url": "https://api.github.com/repos/Staninna/yotsuba/releases/assets/2",
             "browser_download_url": "https://github.com/Staninna/yotsuba/releases/download/v1.1.0/yotsuba-v1.1.0.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun `it reads the tag, notes and apk asset`() {
        val release = GithubReleases.parse(payload)
        assertEquals("v1.1.0", release.tag)
        assertEquals(14680064L, release.sizeBytes)
        assertEquals(
            "https://github.com/Staninna/yotsuba/releases/download/v1.1.0/yotsuba-v1.1.0.apk",
            release.apkUrl,
        )
        assertEquals("## What's Changed\n* In-app updates", release.notes)
    }

    @Test
    fun `the apk asset is picked over other attachments`() {
        val release = GithubReleases.parse(payload)
        assertTrue(release.apkUrl.endsWith(".apk"))
    }

    @Test
    fun `a release with no apk is an error, not a silent no-op`() {
        val noApk = """{"tag_name": "v1.1.0", "body": "", "assets": []}"""
        val e = assertThrows(ReleaseException::class.java) { GithubReleases.parse(noApk) }
        assertEquals("Release v1.1.0 has no APK attached.", e.message)
    }

    @Test
    fun `garbage is an error`() {
        assertThrows(ReleaseException::class.java) { GithubReleases.parse("not json") }
    }

    @Test
    fun `the release list keeps order, skips drafts and needs no apk`() {
        val list = GithubReleases.parseAll(
            """
            [
              {"tag_name": "v2.2.0", "body": "## Added\n- x", "published_at": "2026-08-30T20:11:00Z", "assets": []},
              {"tag_name": "v9.9.9", "body": "", "draft": true, "published_at": "2026-08-30T20:11:00Z", "assets": []},
              {"tag_name": "v2.1.2", "body": " notes ", "published_at": "2026-08-29T10:00:00Z", "assets": []}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("v2.2.0", "v2.1.2"), list.map { it.tag })
        assertEquals("2026-08-30", list[0].publishedAt)
        assertEquals("notes", list[1].notes)
    }

    @Test
    fun `a broken release list is a ReleaseException`() {
        assertThrows(ReleaseException::class.java) { GithubReleases.parseAll("{") }
    }
}
