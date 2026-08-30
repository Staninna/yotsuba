package dev.stan.yotsuba.update

import dev.stan.yotsuba.core.update.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `structured notes split into titled sections with leads`() {
        val notes = ReleaseNotes.parse(
            """
            ## Added
            - **Reverse image search** — search an image or a video frame from the viewer
            - Swipe left or right in a thread to move through the catalog
            ## Fixed
            - **Quote previews**: dead links now resolve against the archive
            - **App lock.** The unlock prompt shows again when you come back.
            """.trimIndent(),
        )
        assertEquals(listOf("Added", "Fixed"), notes.sections.map { it.title })
        assertEquals("App lock", notes.sections[1].items[1].lead)
        assertEquals("The unlock prompt shows again when you come back.", notes.sections[1].items[1].text)
        val added = notes.sections[0].items
        assertEquals("Reverse image search", added[0].lead)
        assertEquals("search an image or a video frame from the viewer", added[0].text)
        assertNull(added[1].lead)
        assertEquals("Swipe left or right in a thread to move through the catalog", added[1].text)
        assertEquals("Quote previews", notes.sections[1].items[0].lead)
        assertEquals("dead links now resolve against the archive", notes.sections[1].items[0].text)
    }

    @Test
    fun `github generated notes fall back to paragraphs with markup flattened`() {
        val notes = ReleaseNotes.parse(
            """
            ## What's Changed
            * Bump to 2.1.2 by @Staninna in https://github.com/Staninna/yotsuba/pull/3

            **Full Changelog**: https://github.com/Staninna/yotsuba/compare/v2.1.1...v2.1.2
            """.trimIndent(),
        )
        assertEquals("What's Changed", notes.sections.single().title)
        val items = notes.sections.single().items
        assertEquals("Bump to 2.1.2 by @Staninna in https://github.com/Staninna/yotsuba/pull/3", items[0].text)
        assertNull(items[1].lead)
        assertEquals("Full Changelog: https://github.com/Staninna/yotsuba/compare/v2.1.1...v2.1.2", items[1].text)
    }

    @Test
    fun `text before any heading keeps an untitled section and links collapse to their label`() {
        val notes = ReleaseNotes.parse("See the [docs](https://x.y) and `Settings`.\n\n## Changed\n- `Vault` moved")
        assertNull(notes.sections[0].title)
        assertEquals("See the docs and Settings.", notes.sections[0].items[0].text)
        assertEquals("Vault moved", notes.sections[1].items[0].text)
    }

    @Test
    fun `blank input is empty`() {
        assertTrue(ReleaseNotes.parse("  \n\n").isEmpty)
    }
}
